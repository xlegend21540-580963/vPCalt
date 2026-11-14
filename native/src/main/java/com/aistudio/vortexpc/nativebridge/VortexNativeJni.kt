package com.aistudio.vortexpc.nativebridge

object VortexNativeJni {
  val isNativeLoaded: Boolean = runCatching {
    System.loadLibrary("vortex_native")
    true
  }.getOrDefault(false)

  external fun nativeGetAbiInfo(): String
  external fun nativeOpenXConnector(socketPath: String): Int
  external fun nativeCloseXConnector(serverFd: Int)
  external fun nativeCreateSysVShm(name: String, sizeBytes: Int): Int
  external fun nativeCloseSysVShm(fd: Int)
  external fun nativeStartAudioBridge(sampleRate: Int, channels: Int, framesPerBurst: Int): Int
  external fun nativeWriteAudioPcm(pcm: ShortArray, frameCount: Int): Int
  external fun nativeStopAudioBridge()
  external fun nativeSpawnPRoot(
    loaderPath: String,
    prootPath: String,
    rootfsPath: String,
    command: String,
    affinityMask: Int,
  ): Int
  external fun nativeInitVirgl(socketPath: String): Int
  external fun nativeShutdownVirgl()
}
