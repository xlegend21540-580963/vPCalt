#ifndef VORTEX_NATIVE_H
#define VORTEX_NATIVE_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

int vortex_xconnector_open(const char* socket_path);
void vortex_xconnector_close(int server_fd);
int vortex_xconnector_accept(int server_fd, int timeout_ms);
int vortex_xconnector_recv_fd(int client_fd, void* out_buf, size_t buf_len, int* out_fd);

int vortex_sysvshm_create(const char* name, size_t size_bytes);
void vortex_sysvshm_close(int fd);

int vortex_audiobridge_start(int sample_rate, int channel_count, int frames_per_burst);
int vortex_audiobridge_write(const int16_t* pcm_data, int frame_count);
void vortex_audiobridge_stop(void);

int vortex_proot_spawn(
    const char* loader_path,
    const char* proot_path,
    const char* rootfs_path,
    const char* command,
    uint32_t cpu_affinity_mask
);
int vortex_proot_set_affinity(int pid, uint32_t mask);

int vortex_virgl_init(const char* socket_path);
void vortex_virgl_shutdown(void);

#ifdef __cplusplus
}
#endif

#endif // VORTEX_NATIVE_H
