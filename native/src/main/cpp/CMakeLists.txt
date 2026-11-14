cmake_minimum_required(VERSION 3.22.1)
project(vortex_native LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Enforce 16KB ELF page alignment for Android 15/16+ devices
add_link_options(-Wl,-z,max-page-size=16384)

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)
find_library(AAUDIO_LIB aaudio)

add_library(vortex_native SHARED
    vortex_jni.cpp
    xconnector.c
    sysvshm.c
    audiobridge.c
    proot_bridge.c
    virgl_bridge.c
)

target_include_directories(vortex_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(vortex_native
    ${LOG_LIB}
    ${ANDROID_LIB}
    ${AAUDIO_LIB}
)
