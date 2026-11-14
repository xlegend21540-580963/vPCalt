cmake_minimum_required(VERSION 3.22.1)
project(vortex_spike LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Enforce 16 KB ELF segment alignment for Android 15/16+ compatibility
add_link_options(-Wl,-z,max-page-size=16384)

find_library(LOG_LIB log)

# 1. Tiny PIE ELF executable packaged as libspike_exec.so in jniLibs/arm64-v8a
add_executable(spike_exec spike_exec.c)
target_compile_options(spike_exec PRIVATE -fPIE -Wall -Wextra)
target_link_options(spike_exec PRIVATE -pie -Wl,-z,max-page-size=16384)
set_target_properties(spike_exec PROPERTIES
    PREFIX "lib"
    OUTPUT_NAME "spike_exec"
    SUFFIX ".so"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
)

# 2. JNI library exposing Experiment 1 & Experiment 2 native probes
add_library(spike_jni SHARED spike_jni.c)
target_compile_options(spike_jni PRIVATE -Wall -Wextra)
target_link_options(spike_jni PRIVATE -Wl,-z,max-page-size=16384)
target_link_libraries(spike_jni
    ${LOG_LIB}
    dl
)
