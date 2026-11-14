#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/utsname.h>
#include <sys/wait.h>
#include <unistd.h>

#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif

extern char **environ;

static void sanitize_field(char *s) {
    if (!s) return;
    for (size_t i = 0; s[i] != '\0'; ++i) {
        if (s[i] == '\n' || s[i] == '\r' || s[i] == '|') {
            s[i] = ' ';
        }
    }
}

static jstring make_result(JNIEnv *env, int pass, int err, const char *note) {
    char buf[1024];
    char clean_note[896];
    snprintf(clean_note, sizeof(clean_note), "%s", note ? note : "");
    sanitize_field(clean_note);
    snprintf(
        buf,
        sizeof(buf),
        "%s|%d|%s",
        pass ? "PASS" : "FAIL",
        err,
        clean_note
    );
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jstring JNICALL
Java_com_aistudio_vortexpc_spike_SpikeNative_nativeReadEnvironment(JNIEnv *env, jobject thiz) {
    (void)thiz;
    long page_size = sysconf(_SC_PAGESIZE);

    struct utsname u;
    memset(&u, 0, sizeof(u));
    int u_rc = uname(&u);
    int u_err = (u_rc == 0) ? 0 : errno;

    char selinux[256] = "unreadable";
    int selinux_err = 0;
    int fd = open("/proc/self/attr/current", O_RDONLY);
    if (fd >= 0) {
        ssize_t n = read(fd, selinux, sizeof(selinux) - 1);
        if (n > 0) {
            selinux[n] = '\0';
            for (ssize_t i = n - 1; i >= 0; --i) {
                if (selinux[i] == '\0' || selinux[i] == '\n' || selinux[i] == '\r') {
                    selinux[i] = '\0';
                } else {
                    break;
                }
            }
        } else {
            selinux_err = errno;
            snprintf(selinux, sizeof(selinux), "read_err=%d(%s)", selinux_err, strerror(selinux_err));
        }
        close(fd);
    } else {
        selinux_err = errno;
        snprintf(selinux, sizeof(selinux), "open_err=%d(%s)", selinux_err, strerror(selinux_err));
    }

    char out[1024];
    snprintf(
        out,
        sizeof(out),
        "kernel=%s %s (%s) | page_size=%ld | selinux=%s | uname_err=%d",
        (u_rc == 0) ? u.sysname : "unknown",
        (u_rc == 0) ? u.release : "unknown",
        (u_rc == 0) ? u.machine : "unknown",
        page_size,
        selinux,
        u_err
    );
    return (*env)->NewStringUTF(env, out);
}

JNIEXPORT jstring JNICALL
Java_com_aistudio_vortexpc_spike_SpikeNative_nativeExecve(
    JNIEnv *env,
    jobject thiz,
    jstring j_exec_path,
    jobjectArray j_argv
) {
    (void)thiz;
    const char *exec_path = (*env)->GetStringUTFChars(env, j_exec_path, NULL);
    if (!exec_path) {
        return make_result(env, 0, EINVAL, "null exec_path");
    }

    jsize argc = j_argv ? (*env)->GetArrayLength(env, j_argv) : 0;
    if (argc <= 0 || argc > 16) {
        (*env)->ReleaseStringUTFChars(env, j_exec_path, exec_path);
        return make_result(env, 0, EINVAL, "invalid argc");
    }

    char *argv[17];
    memset(argv, 0, sizeof(argv));
    for (jsize i = 0; i < argc; ++i) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, j_argv, i);
        const char *cs = s ? (*env)->GetStringUTFChars(env, s, NULL) : NULL;
        argv[i] = cs ? strdup(cs) : strdup("");
        if (s && cs) (*env)->ReleaseStringUTFChars(env, s, cs);
        if (s) (*env)->DeleteLocalRef(env, s);
    }
    argv[argc] = NULL;

    int out_pipe[2] = {-1, -1};
    int err_pipe[2] = {-1, -1};
    if (pipe2(out_pipe, O_CLOEXEC) != 0 || pipe2(err_pipe, O_CLOEXEC) != 0) {
        int e = errno;
        if (out_pipe[0] >= 0) { close(out_pipe[0]); close(out_pipe[1]); }
        if (err_pipe[0] >= 0) { close(err_pipe[0]); close(err_pipe[1]); }
        for (jsize i = 0; i < argc; ++i) free(argv[i]);
        (*env)->ReleaseStringUTFChars(env, j_exec_path, exec_path);
        return make_result(env, 0, e, strerror(e));
    }

    pid_t pid = fork();
    if (pid < 0) {
        int e = errno;
        close(out_pipe[0]); close(out_pipe[1]);
        close(err_pipe[0]); close(err_pipe[1]);
        for (jsize i = 0; i < argc; ++i) free(argv[i]);
        (*env)->ReleaseStringUTFChars(env, j_exec_path, exec_path);
        return make_result(env, 0, e, strerror(e));
    }

    if (pid == 0) {
        close(out_pipe[0]);
        close(err_pipe[0]);
        dup2(out_pipe[1], STDOUT_FILENO);
        dup2(out_pipe[1], STDERR_FILENO);
        close(out_pipe[1]);
        /* err_pipe[1] retains FD_CLOEXEC so parent sees EOF if execve succeeds */
        execve(exec_path, argv, environ);
        int exec_err = errno;
        ssize_t ignored = write(err_pipe[1], &exec_err, sizeof(exec_err));
        (void)ignored;
        close(err_pipe[1]);
        _exit(127);
    }

    close(out_pipe[1]);
    close(err_pipe[1]);

    int child_exec_err = 0;
    ssize_t err_n = read(err_pipe[0], &child_exec_err, sizeof(child_exec_err));
    close(err_pipe[0]);

    char child_out[512];
    memset(child_out, 0, sizeof(child_out));
    size_t total_out = 0;
    while (total_out < sizeof(child_out) - 1) {
        ssize_t n = read(out_pipe[0], child_out + total_out, sizeof(child_out) - 1 - total_out);
        if (n <= 0) break;
        total_out += (size_t)n;
    }
    close(out_pipe[0]);
    sanitize_field(child_out);

    int status = 0;
    pid_t w_rc = waitpid(pid, &status, 0);
    int wait_err = (w_rc < 0) ? errno : 0;

    for (jsize i = 0; i < argc; ++i) free(argv[i]);
    (*env)->ReleaseStringUTFChars(env, j_exec_path, exec_path);

    char note[896];
    if (err_n == (ssize_t)sizeof(child_exec_err) && child_exec_err != 0) {
        snprintf(
            note,
            sizeof(note),
            "execve errno=%d (%s); waitpid_status=0x%x; out=%s",
            child_exec_err,
            strerror(child_exec_err),
            status,
            child_out[0] ? child_out : "<empty>"
        );
        return make_result(env, 0, child_exec_err, note);
    }

    if (WIFEXITED(status)) {
        int exit_code = WEXITSTATUS(status);
        snprintf(
            note,
            sizeof(note),
            "exit=%d waitpid_status=0x%x wait_err=%d out=%s",
            exit_code,
            status,
            wait_err,
            child_out[0] ? child_out : "<empty>"
        );
        return make_result(env, exit_code == 0, exit_code == 0 ? 0 : exit_code, note);
    }

    if (WIFSIGNALED(status)) {
        int sig = WTERMSIG(status);
        snprintf(
            note,
            sizeof(note),
            "signaled=%d waitpid_status=0x%x out=%s",
            sig,
            status,
            child_out[0] ? child_out : "<empty>"
        );
        return make_result(env, 0, sig, note);
    }

    snprintf(note, sizeof(note), "waitpid_status=0x%x out=%s", status, child_out);
    return make_result(env, 0, wait_err, note);
}

JNIEXPORT jstring JNICALL
Java_com_aistudio_vortexpc_spike_SpikeNative_nativeDlopen(
    JNIEnv *env,
    jobject thiz,
    jstring j_path
) {
    (void)thiz;
    const char *path = (*env)->GetStringUTFChars(env, j_path, NULL);
    if (!path) return make_result(env, 0, EINVAL, "null path");

    dlerror();
    errno = 0;
    void *handle = dlopen(path, RTLD_NOW);
    int saved_errno = errno;
    const char *dl_err = dlerror();

    jstring res;
    if (handle != NULL) {
        dlclose(handle);
        res = make_result(env, 1, 0, "dlopen(RTLD_NOW) succeeded");
    } else {
        char note[896];
        snprintf(
            note,
            sizeof(note),
            "dlopen failed: errno=%d (%s) dlerror=%s",
            saved_errno,
            strerror(saved_errno),
            dl_err ? dl_err : "<null>"
        );
        res = make_result(env, 0, saved_errno, note);
    }

    (*env)->ReleaseStringUTFChars(env, j_path, path);
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_aistudio_vortexpc_spike_SpikeNative_nativeMmapFileExec(
    JNIEnv *env,
    jobject thiz,
    jstring j_path
) {
    (void)thiz;
    const char *path = (*env)->GetStringUTFChars(env, j_path, NULL);
    if (!path) return make_result(env, 0, EINVAL, "null path");

    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        int e = errno;
        (*env)->ReleaseStringUTFChars(env, j_path, path);
        return make_result(env, 0, e, strerror(e));
    }

    long page_size = sysconf(_SC_PAGESIZE);
    size_t map_len = (page_size > 0) ? (size_t)page_size : 4096U;
    void *ptr = mmap(NULL, map_len, PROT_READ | PROT_EXEC, MAP_PRIVATE, fd, 0);
    int map_err = (ptr == MAP_FAILED) ? errno : 0;
    close(fd);
    (*env)->ReleaseStringUTFChars(env, j_path, path);

    if (ptr == MAP_FAILED) {
        char note[512];
        snprintf(note, sizeof(note), "mmap(PROT_READ|PROT_EXEC, MAP_PRIVATE) failed: %s", strerror(map_err));
        return make_result(env, 0, map_err, note);
    }

    volatile uint32_t magic = *(volatile uint32_t *)ptr;
    munmap(ptr, map_len);

    char note[256];
    snprintf(note, sizeof(note), "mmap(RX) succeeded; read_magic=0x%08x", magic);
    return make_result(env, 1, 0, note);
}

JNIEXPORT jstring JNICALL
Java_com_aistudio_vortexpc_spike_SpikeNative_nativeMmapFileRwToRx(
    JNIEnv *env,
    jobject thiz,
    jstring j_path
) {
    (void)thiz;
    const char *path = (*env)->GetStringUTFChars(env, j_path, NULL);
    if (!path) return make_result(env, 0, EINVAL, "null path");

    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        int e = errno;
        (*env)->ReleaseStringUTFChars(env, j_path, path);
        return make_result(env, 0, e, strerror(e));
    }

    long page_size = sysconf(_SC_PAGESIZE);
    size_t map_len = (page_size > 0) ? (size_t)page_size : 4096U;
    void *ptr = mmap(NULL, map_len, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, 0);
    int map_err = (ptr == MAP_FAILED) ? errno : 0;
    close(fd);
    (*env)->ReleaseStringUTFChars(env, j_path, path);

    if (ptr == MAP_FAILED) {
        char note[512];
        snprintf(note, sizeof(note), "mmap(PROT_READ|PROT_WRITE, MAP_PRIVATE) failed: %s", strerror(map_err));
        return make_result(env, 0, map_err, note);
    }

    volatile uint32_t magic = *(volatile uint32_t *)ptr;
    int prot_rc = mprotect(ptr, map_len, PROT_READ | PROT_EXEC);
    int prot_err = (prot_rc != 0) ? errno : 0;
    munmap(ptr, map_len);

    if (prot_rc != 0) {
        char note[512];
        snprintf(note, sizeof(note), "mprotect(PROT_READ|PROT_EXEC) failed: %s (magic=0x%08x)", strerror(prot_err), magic);
        return make_result(env, 0, prot_err, note);
    }

    char note[256];
    snprintf(note, sizeof(note), "file mmap RW->mprotect RX succeeded; magic=0x%08x", magic);
    return make_result(env, 1, 0, note);
}

static int invoke_aarch64_jit_stub(void *page_ptr) {
#if defined(__aarch64__)
    int (*fn)(void) = (int (*)(void))page_ptr;
    return fn();
#else
    (void)page_ptr;
    return -1;
#endif
}

static void write_aarch64_ret42(uint32_t *code) {
    /* mov w0, #42 (0x52800540) ; ret (0xD65F03C0) */
    code[0] = 0x52800540U;
    code[1] = 0xD65F03C0U;
    __builtin___clear_cache((char *)code, (char *)(code + 2));
}

JNIEXPORT jstring JNICALL
Java_com_aistudio_vortexpc_spike_SpikeNative_nativeJitAnonRwToRx(JNIEnv *env, jobject thiz) {
    (void)thiz;
    long page_size = sysconf(_SC_PAGESIZE);
    size_t map_len = (page_size > 0) ? (size_t)page_size : 4096U;

    void *mem = mmap(NULL, map_len, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (mem == MAP_FAILED) {
        int e = errno;
        return make_result(env, 0, e, strerror(e));
    }

    write_aarch64_ret42((uint32_t *)mem);

    if (mprotect(mem, map_len, PROT_READ | PROT_EXEC) != 0) {
        int e = errno;
        munmap(mem, map_len);
        char note[512];
        snprintf(note, sizeof(note), "mprotect(RW->RX) failed: %s", strerror(e));
        return make_result(env, 0, e, note);
    }

    __builtin___clear_cache((char *)mem, (char *)mem + 8);
    int ret = invoke_aarch64_jit_stub(mem);
    munmap(mem, map_len);

    char note[256];
    snprintf(note, sizeof(note), "anon mmap RW->mprotect RX executed; ret=%d page_size=%zu", ret, map_len);
    return make_result(env, ret == 42, ret == 42 ? 0 : EINVAL, note);
}

JNIEXPORT jstring JNICALL
Java_com_aistudio_vortexpc_spike_SpikeNative_nativeMmapAnonRwx(JNIEnv *env, jobject thiz) {
    (void)thiz;
    long page_size = sysconf(_SC_PAGESIZE);
    size_t map_len = (page_size > 0) ? (size_t)page_size : 4096U;

    void *mem = mmap(
        NULL,
        map_len,
        PROT_READ | PROT_WRITE | PROT_EXEC,
        MAP_PRIVATE | MAP_ANONYMOUS,
        -1,
        0
    );
    if (mem == MAP_FAILED) {
        int e = errno;
        char note[512];
        snprintf(note, sizeof(note), "mmap(RWX) blocked as expected or failed: %s", strerror(e));
        return make_result(env, 0, e, note);
    }

    write_aarch64_ret42((uint32_t *)mem);
    int ret = invoke_aarch64_jit_stub(mem);
    munmap(mem, map_len);

    char note[256];
    snprintf(note, sizeof(note), "mmap(PROT_READ|PROT_WRITE|PROT_EXEC) allowed; ret=%d", ret);
    return make_result(env, 1, 0, note);
}

JNIEXPORT jstring JNICALL
Java_com_aistudio_vortexpc_spike_SpikeNative_nativeMemfdExec(JNIEnv *env, jobject thiz) {
    (void)thiz;
#ifdef __NR_memfd_create
    int mfd = (int)syscall(__NR_memfd_create, "spike_jit_memfd", MFD_CLOEXEC);
    if (mfd < 0) {
        int e = errno;
        char note[512];
        snprintf(note, sizeof(note), "memfd_create failed: %s", strerror(e));
        return make_result(env, 0, e, note);
    }

    long page_size = sysconf(_SC_PAGESIZE);
    size_t map_len = (page_size > 0) ? (size_t)page_size : 4096U;
    if (ftruncate(mfd, (off_t)map_len) != 0) {
        int e = errno;
        close(mfd);
        return make_result(env, 0, e, strerror(e));
    }

    uint32_t stub[2] = {0x52800540U, 0xD65F03C0U};
    ssize_t wn = pwrite(mfd, stub, sizeof(stub), 0);
    if (wn != (ssize_t)sizeof(stub)) {
        int e = errno;
        close(mfd);
        return make_result(env, 0, e, strerror(e));
    }

    void *exec_map = mmap(NULL, map_len, PROT_READ | PROT_EXEC, MAP_SHARED, mfd, 0);
    int map_err = (exec_map == MAP_FAILED) ? errno : 0;
    close(mfd);

    if (exec_map == MAP_FAILED) {
        char note[512];
        snprintf(note, sizeof(note), "mmap(memfd, PROT_READ|PROT_EXEC, MAP_SHARED) failed: %s", strerror(map_err));
        return make_result(env, 0, map_err, note);
    }

    __builtin___clear_cache((char *)exec_map, (char *)exec_map + 8);
    int ret = invoke_aarch64_jit_stub(exec_map);
    munmap(exec_map, map_len);

    char note[256];
    snprintf(note, sizeof(note), "memfd_create + mmap(PROT_READ|PROT_EXEC) executed; ret=%d", ret);
    return make_result(env, ret == 42, ret == 42 ? 0 : EINVAL, note);
#else
    return make_result(env, 0, ENOSYS, "__NR_memfd_create unavailable on target architecture");
#endif
}

typedef int (*pfn_ASharedMemory_create)(const char *name, size_t size);
typedef int (*pfn_ASharedMemory_setProt)(int fd, int prot);

JNIEXPORT jstring JNICALL
Java_com_aistudio_vortexpc_spike_SpikeNative_nativeAshmemExec(JNIEnv *env, jobject thiz) {
    (void)thiz;
    void *libandroid = dlopen("libandroid.so", RTLD_NOW);
    pfn_ASharedMemory_create fn_create = libandroid
        ? (pfn_ASharedMemory_create)dlsym(libandroid, "ASharedMemory_create")
        : NULL;
    pfn_ASharedMemory_setProt fn_setprot = libandroid
        ? (pfn_ASharedMemory_setProt)dlsym(libandroid, "ASharedMemory_setProt")
        : NULL;

    if (!fn_create) {
        if (libandroid) dlclose(libandroid);
        return make_result(env, 0, ENOSYS, "ASharedMemory_create unavailable (requires API 27+)");
    }

    long page_size = sysconf(_SC_PAGESIZE);
    size_t map_len = (page_size > 0) ? (size_t)page_size : 4096U;
    int fd = fn_create("spike_jit_ashmem", map_len);
    if (fd < 0) {
        int e = errno;
        if (libandroid) dlclose(libandroid);
        char note[512];
        snprintf(note, sizeof(note), "ASharedMemory_create failed: %s", strerror(e));
        return make_result(env, 0, e, note);
    }

    void *rw_map = mmap(NULL, map_len, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (rw_map == MAP_FAILED) {
        int e = errno;
        close(fd);
        if (libandroid) dlclose(libandroid);
        return make_result(env, 0, e, strerror(e));
    }

    write_aarch64_ret42((uint32_t *)rw_map);
    munmap(rw_map, map_len);

    int prot_set_rc = fn_setprot ? fn_setprot(fd, PROT_READ | PROT_EXEC) : 0;
    int prot_set_err = (prot_set_rc != 0) ? errno : 0;

    void *rx_map = mmap(NULL, map_len, PROT_READ | PROT_EXEC, MAP_SHARED, fd, 0);
    int rx_err = (rx_map == MAP_FAILED) ? errno : 0;
    close(fd);
    if (libandroid) dlclose(libandroid);

    if (rx_map == MAP_FAILED) {
        char note[512];
        snprintf(
            note,
            sizeof(note),
            "ASharedMemory mmap(PROT_READ|PROT_EXEC) failed: %s (setProt_rc=%d err=%d)",
            strerror(rx_err),
            prot_set_rc,
            prot_set_err
        );
        return make_result(env, 0, rx_err, note);
    }

    __builtin___clear_cache((char *)rx_map, (char *)rx_map + 8);
    int ret = invoke_aarch64_jit_stub(rx_map);
    munmap(rx_map, map_len);

    char note[256];
    snprintf(note, sizeof(note), "ASharedMemory RW->setProt(RX)->mmap(RX) executed; ret=%d", ret);
    return make_result(env, ret == 42, ret == 42 ? 0 : EINVAL, note);
}
