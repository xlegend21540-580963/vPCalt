#define _GNU_SOURCE
#include "vortex_native.h"
#include <errno.h>
#include <sched.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <unistd.h>

int vortex_proot_set_affinity(int pid, uint32_t mask) {
    if (mask == 0) return 0;
    cpu_set_t set;
    CPU_ZERO(&set);
    for (int cpu = 0; cpu < 32; ++cpu) {
        if (mask & (1u << cpu)) {
            CPU_SET(cpu, &set);
        }
    }
    return sched_setaffinity((pid_t)pid, sizeof(set), &set) == 0 ? 0 : -errno;
}

int vortex_proot_spawn(
    const char* loader_path,
    const char* proot_path,
    const char* rootfs_path,
    const char* command,
    uint32_t cpu_affinity_mask
) {
    if (!proot_path || !rootfs_path || !command) return -EINVAL;

    pid_t pid = fork();
    if (pid < 0) return -errno;

    if (pid == 0) {
        prctl(PR_SET_PDEATHSIG, SIGTERM);
        if (cpu_affinity_mask != 0) {
            vortex_proot_set_affinity(0, cpu_affinity_mask);
        }
        if (loader_path && loader_path[0] != '\0') {
            setenv("PROOT_LOADER", loader_path, 1);
        }
        char* const argv[] = {
            (char*)proot_path,
            (char*)"-r",
            (char*)rootfs_path,
            (char*)"-0",
            (char*)"-w",
            (char*)"/home/vortex",
            (char*)"/bin/sh",
            (char*)"-c",
            (char*)command,
            NULL
        };
        execvp(proot_path, argv);
        _exit(127);
    }

    return (int)pid;
}
