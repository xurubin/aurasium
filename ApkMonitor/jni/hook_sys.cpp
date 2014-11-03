/*
 * hook_sys.cpp
 *
 *  Created on: Sep 15, 2011
 *      Author: rubin
 */
#include <dlfcn.h>
#include <unistd.h>
#include <errno.h>
#include "JNIHelper.h"
#include <sys/stat.h>
#include <sys/types.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <time.h>

#define AurasiumPolicyFile "aurasium.decisions"
extern "C" {
JNIEXPORT void    Java_com_rx201_apkmon_APIHook_saveDecisions( JNIEnv* env, jobject thiz, jstring path, jbyteArray data);
JNIEXPORT jbyteArray Java_com_rx201_apkmon_APIHook_loadDecisions( JNIEnv* env, jobject thiz, jstring path);
}

#ifndef NDEBUG
#define LOG_FILE "/sdcard/access.txt"
#endif
#define LOG_TAG "apihook_sys"
#include "logfile.h"
void *my_libc_dlopen(const char *filename, int flag)
{
	JNIEnv* env = JNIHelper::Self().GetEnv();
	jstring jstr_filename = NULL;
	if (filename)
		jstr_filename = env->NewStringUTF(filename);

	int allow = env->CallStaticIntMethod(JNIHelper::Self().gApiHookMetadata.Clazz,
			JNIHelper::Self().gApiHookMetadata.onDlOpen,
			jstr_filename, flag);

	if (allow)
	{
//TODO:
/*
 		struct soinfo si;
		if (!load_elf(ehdr, &si))
		{
			LOGE("Cannot parse elf file.");
			continue;
		}
		if (containsAddr(&si, (unsigned)containsAddr))
		{
			LOGI("Skip patching self.");
			continue;
		}
		for (hc = hook_funcs; hc->so_filename; hc++)
		{
			int pcount = patchReloc(&si, hc->func_name, (unsigned *)&hc->original_ptr, (unsigned)hc->hook_ptr);
			LOGI("Hooking %s (%d places patched.)", hc->func_name, pcount);
			total_count += pcount;
			if (pcount)
				LOGIC("hookAll: %s %s.", memmap[i].name, hc->func_name);
		}
 */
		return dlopen(filename, flag);
	}
	else
	{
		return 0;
	}
}


/* In order to intercept the subprocess spawning of fork/execvp, we use the following trick:
 * We want to find out the target executable filename before letting the user decide whether
 * to allow it or not, and this can only be done when my_execvp() is invoked. But it's the
 * child process that invokes execvp(), and we can't get binder communication (hence unable
 * to start DialogActivity to display a alert dialog) to work properly in the child process's
 * environment (possibly due to the duplication of various handles/existing java objects).
 * Instead we intercept both fork and execvp, and create a pipe which is share between parent process
 * and child process. The parent process wait after fork() on the pipe until the child process
 * sends back the target filepath, at which point the parent can start a dialog and ask for
 * user consent, and the result is communicated back to the child via the pipe.
 *
 */

// XXX: Maybe we need make the following pipe fds thread-local? But the framework invocation function
// seems to have already guarded this with a critical section.
int filepath_pipe[2] = {-1, -1}; // Pipe for communicating execvp's pathname from child to parent.
int result_pipe[2] = {-1, -1}; // Pipe for communicating back allow/deny of execvp's invocation, from parent to child;
int in_child_process = 0;


#define MY_BUFFER_SIZE 1024
char filename_buffer[MY_BUFFER_SIZE] = "";
char pathfile[20];


//
//
// Hassen: Utils
//
//
int get_file_info_from_fd(int fd) {
	int result=-1;
	sprintf(pathfile,"/proc/self/fd/%d",fd);
	memset(filename_buffer,'\0',MY_BUFFER_SIZE);
	result = readlink(pathfile,filename_buffer,sizeof(filename_buffer)) ;
	if (result != -1) {
		LOGE(" filename for %d is: %s",fd,filename_buffer);
	}
	else {
		LOGE(" Error finding filename for %d :   %s",fd,strerror( errno ) ) ;
	}
	return result;
}


void log_fd_info(int file, struct stat fileStat, int mode) {
	time_t clock;
	LOGE("success write %d ",file);
	LOGE("  File Size: \t\t\t\t\t%d bytes",fileStat.st_size);
	LOGE("  Number of Links: \t\t\t\t%d",fileStat.st_nlink);
	LOGE("  File inode: \t\t\t\t\t%d",fileStat.st_ino);
	LOGE("  File UID: \t\t\t\t\t%d",fileStat.st_uid);
	LOGE("  File GID: \t\t\t\t\t%d",fileStat.st_gid);
	LOGE("  File Device ID: \t\t\t\t%d",fileStat.st_dev);
	LOGE("  Protection Mode is: \t\t\t\t%d",fileStat.st_mode);
	LOGE("  Device IS (special file): \t\t\t%d",fileStat.st_rdev);
	LOGE("  blocksize for file system I/O: \t\t%d",fileStat.st_blksize);
	LOGE("  number of 512B blocks allocated: \t\t%d",fileStat.st_blocks);
	clock = fileStat.st_atime;
	LOGE("  time of last access: \t\t\t\t%d \t%s",clock,ctime(&clock));
	clock = fileStat.st_mtime;
	LOGE("  time of last modification: \t\t\t%d \t%s",clock,ctime(&clock));
	clock = fileStat.st_ctime;
	LOGE("  time of last status change: \t\t\t%d \t%s",clock,ctime(&clock));
	
	switch (fileStat.st_mode & S_IFMT) {
		case S_IFBLK:  LOGE("  %s.....file type.. %d ..block device","w/r",file);            break;
		case S_IFCHR:  LOGE("  %s.....file type.. %d ..character device","w/r",file);        break;
		case S_IFDIR:  LOGE("  %s.....file type.. %d ..directory","w/r",file);               break;
		case S_IFIFO:  LOGE("  %s.....file type.. %d ..FIFO/pipe","w/r",file);               break;
		case S_IFLNK:  LOGE("  %s.....file type.. %d ..symlink","w/r",file);                 break;
		case S_IFREG:  LOGE("  %s.....file type.. %d ..regular file","w/r",file);            break;
		case S_IFSOCK: LOGE("  %s.....file type.. %d ..socket","w/r",file);                  break;
		default:       LOGE("  %s.....file type.. %d ..unknown?","w/r",file);                break;
	}
}

//
//
// End Hassen: Utils
//
//


// Between fork and execvp() in child proces, android ProcessManager will close all non-standard fds,
// and we have to intercept close() to avoid our own communication pipes being closed.
int my_libc_close(int fd)
{
	if (in_child_process == 1)
	{
		if ( (fd == filepath_pipe[1]) || // child writes to this pipe end.
			 (fd == result_pipe[0]))   // child reads from this pipe end.
		return 0;
	}
	LOGE("close  %d",fd);
	get_file_info_from_fd(fd);
	return close(fd);
}

pid_t my_libc_fork(void)
{
	if ( (-1 == pipe(filepath_pipe)) || (-1 == pipe(result_pipe)))
	{
		filepath_pipe[0] = filepath_pipe[1] = -1;
		result_pipe[0] = result_pipe[1] = -1;
		LOGE("fork: Cannot create pipes.");
		return fork();
	}

	LOGE("fork: before %d %d %d %d.", filepath_pipe[0], filepath_pipe[1], result_pipe[0], result_pipe[1]);
	pid_t pid = fork();
	LOGE("fork: after %d.", pid);

	if (pid < 0) // fork() failed
		return pid;

	if (pid == 0) //we are the child process.
	{
		in_child_process = 1;
		// close the unused ends
		close(filepath_pipe[0]);
		close(result_pipe[1]);
		return pid;
	}
	else //we are the parent process, need to wait for the filepath to come out of the pipe.
	{
		in_child_process = 0;
		//close unused ends
		close(filepath_pipe[1]);
		close(result_pipe[0]);
		errno = 0;
		LOGE("fork: reading path_len.");
		int path_len = 0; //path_len should include the terminating zero.
		int r = read(filepath_pipe[0], &path_len, sizeof(path_len));
		if ((sizeof(path_len) != r) || (path_len > 512))
		{
			LOGE("fork: Read pipe for path_len(%d) failed, r = %d, errno = %d.", path_len, r, errno);
			close(filepath_pipe[0]);
			close(result_pipe[1]);
			return pid;
		}
		char* path = (char*)malloc(path_len);
		LOGE("fork: reading path.");
		if (read(filepath_pipe[0], path, path_len) != path_len)
		{
			LOGE("fork: Read pipe for path(%d) failed.", path_len);
			close(filepath_pipe[0]);
			close(result_pipe[1]);
			free(path);
			return pid;
		}
		//Now send the information to the appropriate java handler.
		JNIEnv* env = JNIHelper::Self().GetEnv();
		jstring jstr_filename = env->NewStringUTF(path);
		LOGI("fork: filename = %s, %.8x", path, jstr_filename);
		int allow = env->CallStaticIntMethod(JNIHelper::Self().gApiHookMetadata.Clazz,
				JNIHelper::Self().gApiHookMetadata.onBeforeExecvp,
				jstr_filename);
		LOGI("fork: allow = %d", allow);

		// Finally write the decision back to pipe.
		if (write(result_pipe[1], &allow, sizeof(allow)) != sizeof(allow))
			LOGE("fork: write allow to pipe failed.");

		free(path);
		close(filepath_pipe[0]);
		close(result_pipe[1]);
		return pid;
	}

}
int my_libc_execvp(const char *filename, char *const argv [])
{
	LOGE("my_libc_execvp: %d %d %d %d.", filepath_pipe[0], filepath_pipe[1], result_pipe[0], result_pipe[1]);
	if ( (filepath_pipe[1] == -1) || (result_pipe[0] == -1))
		return execvp(filename, argv);


	int path_len = strlen(filename) + 1;
	LOGE("execvp: writing path_len.");
	//	close(execvp_pipe[0]);
	errno = 0;
	int r = write(filepath_pipe[1], &path_len, sizeof(path_len));
	if (r  != sizeof(path_len))
	{
		LOGE("execvp: write path_len to pipe failed %d, %d.", r, errno);
		close(filepath_pipe[1]);
		close(result_pipe[0]);
		return execvp(filename, argv);
	}
	LOGE("execvp: writing filename.");
	if (write(filepath_pipe[1], filename, path_len) != path_len)
	{
		LOGE("execvp: write filename(%s)(%d) to pipe failed.", filename, path_len);
		close(filepath_pipe[1]);
		close(result_pipe[0]);
		return execvp(filename, argv);
	}

	int allow = 0;
	LOGE("execvp: reading allow.");
	if (read(result_pipe[0], &allow, sizeof(allow)) != sizeof(allow))
	{
		LOGE("execvp: read pipe for allow failed.");
		close(filepath_pipe[1]);
		close(result_pipe[0]);
		return execvp(filename, argv);
	}

	close(filepath_pipe[1]);
	close(result_pipe[0]);
	if (allow)
	{
		LOGI("execvp: allow %s %x", filename, argv);
		return execvp(filename, argv);
	}
	else
	{
		LOGI("execvp: denied %s %x", filename, argv);
		errno = -EACCES;
		return -1;
	}
}




FILE  *my_libc_fopen(const char *file, const char *mode) {
	LOGE("fopen %s in mode %s",file,mode);
	if (strstr(AurasiumPolicyFile, file))
	{
		LOGEC("Access to saved decision file is denied.");
		return NULL;
	}
	return fopen(file,mode);
}



int my_libc_open(const char *file, int flags, int mode) {
	int result;
	LOGE("open %s",file);
	if (strstr(AurasiumPolicyFile, file))
	{
		LOGEC("Access to saved decision file is denied.");
		return -1;
	}
	result = open(file,flags,mode);
	LOGE("open returns %d",result);
	return result;
}



int my_libc_write(int file, const void *buffer, size_t count) {
	struct stat fileStat;
	int status;
	LOGE("write %d ",file);
	get_file_info_from_fd(file);
	if (fstat(file,&fileStat) < 0) {
		LOGE("fail write %d ",file);
	}
	else {
		log_fd_info(file,fileStat,1);
	}
	return write(file,buffer,count) ;
}

int my_libc_read(int file, void *buffer, size_t length) {
	struct stat fileStat;
	int status;
	LOGE("read %d ", file);
	get_file_info_from_fd(file);
	if (fstat(file,&fileStat) < 0) {
		LOGE("fail read %d ",file);
	}
	else {
		log_fd_info(file,fileStat,0);
	}
	return read(file,buffer,length);
}



JNIEXPORT void Java_com_rx201_apkmon_APIHook_saveDecisions( JNIEnv* env, jobject thiz, jstring path, jbyteArray data )
{
	char file[1024];
	const char *path_ptr = env->GetStringUTFChars(path, NULL);
	snprintf(file, sizeof(file), "%s/"AurasiumPolicyFile, path_ptr);
	env->ReleaseStringUTFChars(path, path_ptr);
	FILE* f = fopen(file, "w");
	LOGIC("file: %p<n", file);
	if (f)
	{
		int length = env->GetArrayLength(data);
		jbyte* array = (jbyte*)env->GetPrimitiveArrayCritical(data, 0);
		fwrite(array, 1, length, f);
		env->ReleasePrimitiveArrayCritical(data, array, 0);
		fclose(f);
	}
	else
		LOGIC("Saving decisions to %s failed.", AurasiumPolicyFile);
}

JNIEXPORT jbyteArray Java_com_rx201_apkmon_APIHook_loadDecisions( JNIEnv* env, jobject thiz, jstring path)
{
	char file[1024];
	const char *path_ptr = env->GetStringUTFChars(path, NULL);
	snprintf(file, sizeof(file), "%s/"AurasiumPolicyFile, path_ptr);
	env->ReleaseStringUTFChars(path, path_ptr);
	FILE* f = fopen(file, "r");
	if (f)
	{
		jbyte buf[4096];
		int read_len = 0;
		int ptr = 0;
		fseek(f, 0L, SEEK_END);
		int file_size = ftell(f);
		fseek(f, 0L, SEEK_SET);

		jbyteArray result = env->NewByteArray(file_size);
		while ( 0 != (read_len = fread(buf, 1, sizeof(buf), f)) )
		{
			env->SetByteArrayRegion(result, ptr, read_len, buf);
			ptr += read_len;
		}
		fclose(f);
		return result;
	}
	else
		return NULL;

}

