#include <sys/socket.h>

int my_libc_getaddrinfo(const char *node, const char *service, const struct addrinfo *hints, struct addrinfo **res);
int my_libc_connect(int sockfd, const struct sockaddr *serv_addr, socklen_t addrlen);

ssize_t my_libc_read(int fd, void *buf, size_t count);
int my_libc_ioctl(int fd, int request, ...);

void *my_libc_dlopen(const char *filename, int flag);

pid_t my_libc_fork(void);
int my_libc_execvp(const char *filename, char *const argv []);
int my_libc_close(int fd);
int my_libc_write(int file, const void *buffer, size_t count);
ssize_t my_libc_read(int fd, void *buffer, size_t length);
FILE *my_libc_fopen(const char *file, const char *mode);
int my_libc_open(const char *file, int flags, int mode);

int my__system_property_read(const char* *pi, char *name, char *value);
