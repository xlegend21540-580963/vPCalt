#include "vortex_native.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

int vortex_xconnector_open(const char* socket_path) {
    if (!socket_path) return -1;
    unlink(socket_path);

    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK | SOCK_CLOEXEC, 0);
    if (fd < 0) return -errno;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    if (strlen(socket_path) >= sizeof(addr.sun_path)) {
        close(fd);
        return -ENAMETOOLONG;
    }
    strncpy(addr.sun_path, socket_path, sizeof(addr.sun_path) - 1);

    if (bind(fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        int err = -errno;
        close(fd);
        return err;
    }
    if (listen(fd, 32) < 0) {
        int err = -errno;
        close(fd);
        return err;
    }
    return fd;
}

void vortex_xconnector_close(int server_fd) {
    if (server_fd >= 0) {
        close(server_fd);
    }
}

int vortex_xconnector_accept(int server_fd, int timeout_ms) {
    if (server_fd < 0) return -EINVAL;
    struct pollfd pfd;
    pfd.fd = server_fd;
    pfd.events = POLLIN;
    pfd.revents = 0;

    int rc = poll(&pfd, 1, timeout_ms);
    if (rc <= 0) return rc == 0 ? -EAGAIN : -errno;

    int client_fd = accept4(server_fd, NULL, NULL, SOCK_NONBLOCK | SOCK_CLOEXEC);
    return client_fd >= 0 ? client_fd : -errno;
}

int vortex_xconnector_recv_fd(int client_fd, void* out_buf, size_t buf_len, int* out_fd) {
    if (client_fd < 0 || !out_buf || buf_len == 0) return -EINVAL;
    if (out_fd) *out_fd = -1;

    struct iovec iov = {
        .iov_base = out_buf,
        .iov_len = buf_len
    };
    char ctrl[CMSG_SPACE(sizeof(int))];
    memset(ctrl, 0, sizeof(ctrl));

    struct msghdr msg;
    memset(&msg, 0, sizeof(msg));
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    msg.msg_control = ctrl;
    msg.msg_controllen = sizeof(ctrl);

    ssize_t n = recvmsg(client_fd, &msg, 0);
    if (n < 0) return -errno;

    struct cmsghdr* cmsg = CMSG_FIRSTHDR(&msg);
    if (cmsg && cmsg->cmsg_level == SOL_SOCKET && cmsg->cmsg_type == SCM_RIGHTS && out_fd) {
        memcpy(out_fd, CMSG_DATA(cmsg), sizeof(int));
    }
    return (int)n;
}
