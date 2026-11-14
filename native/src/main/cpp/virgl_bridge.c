#include "vortex_native.h"
#include <stdatomic.h>

static int g_virgl_fd = -1;
static atomic_int g_virgl_active = 0;

int vortex_virgl_init(const char* socket_path) {
    vortex_virgl_shutdown();
    g_virgl_fd = vortex_xconnector_open(socket_path);
    if (g_virgl_fd < 0) return g_virgl_fd;
    atomic_store(&g_virgl_active, 1);
    return 0;
}

void vortex_virgl_shutdown(void) {
    atomic_store(&g_virgl_active, 0);
    if (g_virgl_fd >= 0) {
        vortex_xconnector_close(g_virgl_fd);
        g_virgl_fd = -1;
    }
}
