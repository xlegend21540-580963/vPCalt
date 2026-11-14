#include <jni.h>
#include <string>
#include "vortex_native.h"

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_aistudio_vortexpc_nativebridge_VortexNativeJni_nativeGetAbiInfo(JNIEnv* env, jobject) {
#if defined(__aarch64__)
    return env->NewStringUTF("arm64-v8a (16KB-aligned C++20/C11 NDK bridge)");
#else
    return env->NewStringUTF("non-arm64");
#endif
}

JNIEXPORT jint JNICALL
Java_com_aistudio_vortexpc_nativebridge_VortexNativeJni_nativeOpenXConnector(JNIEnv* env, jobject, jstring socketPath) {
    const char* path = env->GetStringUTFChars(socketPath, nullptr);
    int fd = vortex_xconnector_open(path);
    env->ReleaseStringUTFChars(socketPath, path);
    return fd;
}

JNIEXPORT void JNICALL
Java_com_aistudio_vortexpc_nativebridge_VortexNativeJni_nativeCloseXConnector(JNIEnv*, jobject, jint serverFd) {
    vortex_xconnector_close(serverFd);
}

JNIEXPORT jint JNICALL
Java_com_aistudio_vortexpc_nativebridge_VortexNativeJni_nativeCreateSysVShm(JNIEnv* env, jobject, jstring name, jint sizeBytes) {
    const char* cname = env->GetStringUTFChars(name, nullptr);
    int fd = vortex_sysvshm_create(cname, static_cast<size_t>(sizeBytes));
    env->ReleaseStringUTFChars(name, cname);
    return fd;
}

JNIEXPORT void JNICALL
Java_com_aistudio_vortexpc_nativebridge_VortexNativeJni_nativeCloseSysVShm(JNIEnv*, jobject, jint fd) {
    vortex_sysvshm_close(fd);
}

JNIEXPORT jint JNICALL
Java_com_aistudio_vortexpc_nativebridge_VortexNativeJni_nativeStartAudioBridge(JNIEnv*, jobject, jint sampleRate, jint channels, jint framesPerBurst) {
    return vortex_audiobridge_start(sampleRate, channels, framesPerBurst);
}

JNIEXPORT jint JNICALL
Java_com_aistudio_vortexpc_nativebridge_VortexNativeJni_nativeWriteAudioPcm(JNIEnv* env, jobject, jshortArray pcm, jint frameCount) {
    jshort* ptr = env->GetShortArrayElements(pcm, nullptr);
    int written = vortex_audiobridge_write(ptr, frameCount);
    env->ReleaseShortArrayElements(pcm, ptr, JNI_ABORT);
    return written;
}

JNIEXPORT void JNICALL
Java_com_aistudio_vortexpc_nativebridge_VortexNativeJni_nativeStopAudioBridge(JNIEnv*, jobject) {
    vortex_audiobridge_stop();
}

JNIEXPORT jint JNICALL
Java_com_aistudio_vortexpc_nativebridge_VortexNativeJni_nativeSpawnPRoot(
    JNIEnv* env,
    jobject,
    jstring loaderPath,
    jstring prootPath,
    jstring rootfsPath,
    jstring command,
    jint affinityMask
) {
    const char* cLoader = env->GetStringUTFChars(loaderPath, nullptr);
    const char* cProot = env->GetStringUTFChars(prootPath, nullptr);
    const char* cRootfs = env->GetStringUTFChars(rootfsPath, nullptr);
    const char* cCmd = env->GetStringUTFChars(command, nullptr);
    int pid = vortex_proot_spawn(cLoader, cProot, cRootfs, cCmd, static_cast<uint32_t>(affinityMask));
    env->ReleaseStringUTFChars(loaderPath, cLoader);
    env->ReleaseStringUTFChars(prootPath, cProot);
    env->ReleaseStringUTFChars(rootfsPath, cRootfs);
    env->ReleaseStringUTFChars(command, cCmd);
    return pid;
}

JNIEXPORT jint JNICALL
Java_com_aistudio_vortexpc_nativebridge_VortexNativeJni_nativeInitVirgl(JNIEnv* env, jobject, jstring socketPath) {
    const char* path = env->GetStringUTFChars(socketPath, nullptr);
    int rc = vortex_virgl_init(path);
    env->ReleaseStringUTFChars(socketPath, path);
    return rc;
}

JNIEXPORT void JNICALL
Java_com_aistudio_vortexpc_nativebridge_VortexNativeJni_nativeShutdownVirgl(JNIEnv*, jobject) {
    vortex_virgl_shutdown();
}

}
