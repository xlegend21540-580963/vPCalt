#include "vortex_native.h"
#include <android/sharedmem.h>
#include <errno.h>
#include <unistd.h>

int vortex_sysvshm_create(const char* name, size_t size_bytes) {
    if (size_bytes == 0) return -EINVAL;
    const char* safe_name = (name && name[0] != '\0') ? name : "vortex_shm";
    int fd = ASharedMemory_create(safe_name, size_bytes);
    if (fd < 0) {
        return -errno;
    }
    return fd;
}

void vortex_sysvshm_close(int fd) {
    if (fd >= 0) {
        close(fd);
    }
}
