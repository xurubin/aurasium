#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <errno.h>
#include "JNIHelper.h"

#define  LOG_TAG "apihook_inet"
#ifndef NDEBUG
#define LOG_FILE "/sdcard/access.txt"
#endif
#include "logfile.h"

static const char* sockaddr_to_str(const struct sockaddr *addr, char* dst, int size)
{
	void* ptr_addr = NULL;
	if (addr->sa_family == AF_INET)
		ptr_addr = &(((sockaddr_in*)addr)->sin_addr);
	else if (addr->sa_family == AF_INET6)
		ptr_addr = &(((sockaddr_in6*)addr)->sin6_addr);

	if (ptr_addr)
	{
		const char* r = inet_ntop(addr->sa_family, ptr_addr, dst, size);
			LOGI("sockaddr_to_str: %d %s", addr->sa_family, dst);
		return r;
	}
	else
		return NULL;
}
int my_libc_connect(int sockfd, const struct sockaddr *serv_addr, socklen_t addrlen)
{
	LOGI("my_libc_connect: %d %d", sockfd, serv_addr->sa_family);
	//Only intercept Internet connections
	if ((serv_addr->sa_family == AF_INET) || (serv_addr->sa_family == AF_INET6))
	{
		int port = 0;
		if (serv_addr->sa_family == AF_INET)
			port = ((sockaddr_in*)serv_addr)->sin_port;
		else if (serv_addr->sa_family == AF_INET6)
			port = ((sockaddr_in6*)serv_addr)->sin6_port;

		char ip_str[INET6_ADDRSTRLEN];
		if (sockaddr_to_str(serv_addr, ip_str, sizeof(ip_str)))
		{
			JNIEnv* env = JNIHelper::Self().GetEnv();
			jstring jIpStr = env->NewStringUTF(ip_str);
			if (env->CallStaticIntMethod(JNIHelper::Self().gApiHookMetadata.Clazz,
					JNIHelper::Self().gApiHookMetadata.onBeforeConnect,
					 sockfd, jIpStr, ntohs(port)))
			{
				env->DeleteLocalRef(jIpStr);
				return connect(sockfd, serv_addr, addrlen);
			}
			else
			{
				env->DeleteLocalRef(jIpStr);
				errno = EPERM;
				return -1;
			}
		}
	}
	return connect(sockfd, serv_addr, addrlen);
}

int my_libc_getaddrinfo(const char *node, const char *service, const struct addrinfo *hints, struct addrinfo **res)
{
	LOGI("my_libc_getaddrinfo: %s", node);
	int r = getaddrinfo(node, service, hints, res);
	if (res && (r == 0) )
	{
		JNIEnv* env = JNIHelper::Self().GetEnv();
		jstring DomainName = env->NewStringUTF(node);

		char ip_str[INET6_ADDRSTRLEN];
		for (addrinfo* ai = *res; ai != NULL; ai = ai->ai_next) {
			sockaddr* address = ai->ai_addr;
			if (sockaddr_to_str(address, ip_str, sizeof(ip_str)))
			{
				env->CallStaticIntMethod(JNIHelper::Self().gApiHookMetadata.Clazz,
						JNIHelper::Self().gApiHookMetadata.onDNSResolve,
						 DomainName, env->NewStringUTF(ip_str));
			}
		}
	}
	return r;
}
