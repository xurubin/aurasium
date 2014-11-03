#include <stdio.h>
#include <unistd.h>
#include <dlfcn.h>
#include <linux/binder.h>
#include <android/log.h>
#include "JNIHelper.h"
#include <sys/types.h>
#include <errno.h>
#include <sys/mman.h>
#include <limits.h>    /* for PAGESIZE */
#ifndef PAGESIZE
#define PAGESIZE 4096
#endif

#ifndef NDEBUG
#define LOG_FILE "/sdcard/ioctl.txt"
#endif
#define  LOG_TAG "apihook_ioctl"
#include "logfile.h"

extern bool hasStrictMode;

#define NAME(n) case n: return #n
const char *cmd_name(uint32_t cmd)
{
    switch(cmd) {
		NAME(BC_TRANSACTION);
		NAME(BC_REPLY);
		NAME(BC_ACQUIRE_RESULT);
		NAME(BC_FREE_BUFFER);
		NAME(BC_INCREFS);
		NAME(BC_ACQUIRE);
		NAME(BC_RELEASE);
		NAME(BC_DECREFS);
		NAME(BC_INCREFS_DONE);
		NAME(BC_ACQUIRE_DONE);
		NAME(BC_ATTEMPT_ACQUIRE);
		NAME(BC_REGISTER_LOOPER);
		NAME(BC_ENTER_LOOPER);
		NAME(BC_EXIT_LOOPER);
		NAME(BC_REQUEST_DEATH_NOTIFICATION);
		NAME(BC_CLEAR_DEATH_NOTIFICATION);
		NAME(BC_DEAD_BINDER_DONE);
    default: return "???";
    }
}

const char *reply_name(uint32_t cmd)
{
   switch(cmd) {
        NAME(BR_NOOP);
        NAME(BR_TRANSACTION_COMPLETE);
        NAME(BR_INCREFS);
        NAME(BR_ACQUIRE);
        NAME(BR_RELEASE);
        NAME(BR_DECREFS);
        NAME(BR_TRANSACTION);
        NAME(BR_REPLY);
        NAME(BR_FAILED_REPLY);
        NAME(BR_DEAD_REPLY);
        NAME(BR_DEAD_BINDER);
        NAME(BR_ERROR);
        NAME(BR_OK);
        NAME(BR_ACQUIRE_RESULT);
        NAME(BR_ATTEMPT_ACQUIRE);
        NAME(BR_SPAWN_LOOPER);
        NAME(BR_FINISHED);
        NAME(BR_CLEAR_DEATH_NOTIFICATION_DONE);
    default: return "???";
    }
}

void *hBinder_so;
typedef void (*Func_Parcel_ipcSetDataReference)(int, const uint8_t*, size_t, const size_t*, size_t, void*, void*);
Func_Parcel_ipcSetDataReference pFunc_Parcel_ipcSetDataReference;

jobject createParcelFromTransactionData(JNIEnv* env, struct binder_transaction_data* tr, int skip_bytes)
{
	// Dynamically load binder.so and look up the function that can set Parcel content.
	if (!hBinder_so)
	{
		hBinder_so = dlopen("libbinder.so", RTLD_LAZY);
		if (!hBinder_so)
		{
			LOGE("Cannot load libbinder.so");
			return NULL;
		}
		pFunc_Parcel_ipcSetDataReference = (Func_Parcel_ipcSetDataReference)dlsym(
				hBinder_so, "_ZN7android6Parcel19ipcSetDataReferenceEPKhjPKjjPFvPS0_S2_jS4_jPvES6_");
		if (!pFunc_Parcel_ipcSetDataReference)
		{
			LOGE("Cannot find symbol Parcel::ipcSetDataReference");
			return NULL;
		}
	}

	// Now create a Java Parcel object using JNI
	jobject jParcel = env->NewObject(
			JNIHelper::Self().gParcelMetadata.Clazz,
			JNIHelper::Self().gParcelMetadata.Constructor,
			0);
	if(!jParcel) {
		LOGE("Failed to create Java Parcel object");
		return NULL;
	}

	// Retrive the native Parcel object pointer
    int parcelPtr = env->GetIntField(jParcel, JNIHelper::Self().gParcelMetadata.mObject);

    char* dataPtr = NULL;
    if (tr->data_size)
    {
    	dataPtr = (char*) malloc(tr->data_size - skip_bytes);
        memcpy(dataPtr, (void*)((unsigned)tr->data.ptr.buffer + skip_bytes), tr->data_size - skip_bytes);
    }
//    char* objPtr = (char*) malloc(tr->offsets_size);
//    memcpy(objPtr, tr->data.ptr.offsets, tr->offsets_size);

    if (tr->offsets_size)
    {
    	LOGE("Parcel contains objects, proceed with care.");
    }
    // Set Parcel content using exported function from libbinder.
    pFunc_Parcel_ipcSetDataReference(
    	 parcelPtr,
         reinterpret_cast<const uint8_t*>(dataPtr),
         tr->data_size - skip_bytes,
         0/*reinterpret_cast<const size_t*>(objPtr)*/,
         0/*tr->offsets_size/sizeof(size_t)*/, // Do NOT duplicate object offsets in order to
											   // preventing interference with reference counting.
         NULL/*freeBuffer*/, NULL);
    return jParcel;
}

/*
 * Coresponds to Parcel's writeInterfaceToken/enforceInterface(). There is a case split
 * between Froyo and Gingerbread, where Gingerbread adds an extra StrictMode field.
 */
#define PAD_SIZE(s) (((s)+3)&~3)
bool getInterfaceToken(unsigned ParcelPtr, int ParcelLen, char* TokenStr, int& TokenLen)
{
	int Offset = 0;
	if (hasStrictMode)
	{
		Offset += 4; //Skip the StrictMode field.
		if (Offset > ParcelLen)
			return false;
	}

	 // Read String Len field.
    if (Offset + 4 > ParcelLen)
    	return false;
	unsigned strLen = *(unsigned*)(ParcelPtr + Offset) + 1;
	Offset += 4;

	// Read String constant
    if (strLen * 2 + Offset > ParcelLen)
    	return false;
    TokenStr[0] = '\0';
    for (int i=0; i<strLen; i++)
    	TokenStr[i] = *(char*)(ParcelPtr + Offset + 2*i); //Should be unicode, but treat as ascii for now.

    TokenLen = PAD_SIZE(Offset + 2 * strLen);
    return true;
}

// Returns pointer to the newly allocated write buffer, 0 if write_buffer is not to be modified.
char* process_transaction_data(unsigned write_buffer, size_t* oldDataSize)
{
    uint32_t cmd = *(unsigned *)write_buffer;
    struct binder_transaction_data *tr = (struct binder_transaction_data *) (write_buffer + sizeof(cmd));
    char* rtnPtr = NULL;

//    LOGI("parse_transaction_data: flag:%.8x buf:%.8x size:%d", tr->flags, tr->data.ptr.buffer, tr->data_size);
//    for(int i=0;i<tr->data_size;i++)
//    {
//    	LOGI("%.2x", ((char*)tr->data.ptr.buffer)[i]);
//    }
    if ((cmd != BC_TRANSACTION) && (cmd != BR_TRANSACTION))
    {
    	LOGE("Invalid Bx_TRANSACTION buffer.");
    	return 0;
    }

    // Data is not a status code, but a marshalled parcel.
    if ((tr->flags & TF_STATUS_CODE) == 0)
    {
		char InterfaceName[256];
		int TokenLen = 0;
		if (getInterfaceToken((unsigned)tr->data.ptr.buffer, tr->data_size, InterfaceName, TokenLen))
		{
			LOGV("transactT: handle:%.8x code:%.8x flag:%.8x %s", tr->target.handle, tr->code, tr->flags, InterfaceName);
			jobject parcel = NULL;
			JNIEnv* env = JNIHelper::Self().GetEnv();

			// Create a partial parcel excluding the interface token.
			parcel = createParcelFromTransactionData(env, tr, TokenLen);
			// Call callback function
			jmethodID callbackMethod = (cmd == BC_TRANSACTION) ? JNIHelper::Self().gApiHookMetadata.onBcTransact :
			                                                  JNIHelper::Self().gApiHookMetadata.onBrTransact;
			jstring jIfName = env->NewStringUTF(InterfaceName);
			env->ExceptionClear();
			jbyteArray rtnArray = (jbyteArray)env->CallStaticObjectMethod(
					 JNIHelper::Self().gApiHookMetadata.Clazz,
					 callbackMethod,
					 gettid(), cmd, tr->code, jIfName, parcel);
			env->DeleteLocalRef(parcel);
			env->DeleteLocalRef(jIfName);
			if( env->ExceptionCheck()) {
				LOGE("Calling on B_transact raised exception.");
				jthrowable exception = env->ExceptionOccurred();

				jclass throwable_class = env->FindClass("java/lang/Throwable");
				jmethodID mid_throwable_toString =
				    env->GetMethodID(throwable_class,
				                      "toString",
				                      "()Ljava/lang/String;");
			    jstring msg_obj =
			            (jstring) env->CallObjectMethod(exception,
			                                                 mid_throwable_toString);
				const char* msg_str = env->GetStringUTFChars(msg_obj, 0);
				LOGE("%s", msg_str);
				env->ReleaseStringUTFChars(msg_obj, msg_str);
				env->DeleteLocalRef(msg_obj);

				return 0;
			}
			// Need to replace the original parcel with new data
			if (rtnArray)
			{
		    	int length = env->GetArrayLength(rtnArray);
		    	if ((length > 0))
		    	{
					jbyte* array = (jbyte*)env->GetPrimitiveArrayCritical(rtnArray, 0);
					if (array)
					{
						// It is ideal if the modified data can still fit in the original buffer,
						// and it's from user code not from mmap-ed binder address space.
						if ((length + TokenLen <= tr->data_size) && (cmd == BC_TRANSACTION))
						{
							memcpy((void*)((unsigned)tr->data.ptr.buffer + TokenLen), array, length);
							tr->data_size = length + TokenLen;
						}
						else
						{
							rtnPtr = (char*) malloc(length + TokenLen);
							memcpy(rtnPtr, (void*)tr->data.ptr.buffer , TokenLen);
							memcpy(rtnPtr + TokenLen, array, length);

							*oldDataSize = tr->data_size;
							tr->data.ptr.buffer = rtnPtr;
							tr->data_size = length + TokenLen;
						}
						env->ReleasePrimitiveArrayCritical(rtnArray, array, 0);
					}
		    	}
		    	else
		    	{
		    		LOGE("Cannot modify write_buffer with an empty buffer");
		    	}
			}
		}
    }
    else
    {
    	unsigned  err = *(unsigned*)(tr->data.ptr.buffer);
    	LOGI("parse_transaction_data: status: %.8x", err);
    }
    return rtnPtr;
}

// Returns pointer to the newly allocated write buffer, 0 if write_buffer is not to be modified.
char* process_reply_data(unsigned read_buffer, size_t* oldDataSize)
{
    uint32_t cmd = *(unsigned *)read_buffer;
    struct binder_transaction_data *tr = (struct binder_transaction_data *) (read_buffer + sizeof(cmd));
    char* rtnPtr = NULL;

    if ((cmd != BC_REPLY) && (cmd != BR_REPLY))
    {
    	LOGE("Invalid Bx_REPLY buffer.");
    	return 0;
    }

    // Data is not a status code, but a marshalled parcel.
    if ((tr->flags & TF_STATUS_CODE) == 0)
    {
		LOGV("transactR: handle:%.8x code:%.8x flag:%.8x", tr->target.handle, tr->code, tr->flags);
		jobject parcel = NULL;
		JNIEnv* env = JNIHelper::Self().GetEnv();

		parcel = createParcelFromTransactionData(env, tr, 0);
		// Call callback function
		jmethodID callbackMethod = (cmd == BC_REPLY) ? JNIHelper::Self().gApiHookMetadata.onBcReply :
													    JNIHelper::Self().gApiHookMetadata.onBrReply;
		env->ExceptionClear();
		jbyteArray rtnArray = (jbyteArray)env->CallStaticObjectMethod(
                 JNIHelper::Self().gApiHookMetadata.Clazz,
				 callbackMethod,
 				 gettid(), cmd, tr->code, parcel);
		if( env->ExceptionCheck()) {
			LOGE("Calling on B_Reply raised exception.");
			jthrowable exception = env->ExceptionOccurred();

			jclass throwable_class = env->FindClass("java/lang/Throwable");
			jmethodID mid_throwable_toString =
			    env->GetMethodID(throwable_class,
			                      "toString",
			                      "()Ljava/lang/String;");
		    jstring msg_obj =
		            (jstring) env->CallObjectMethod(exception,
		                                                 mid_throwable_toString);
			const char* msg_str = env->GetStringUTFChars(msg_obj, 0);
			LOGE("%s", msg_str);
			env->ReleaseStringUTFChars(msg_obj, msg_str);
			env->DeleteLocalRef(msg_obj);

			return 0;
		}
		// Need to replace the original parcel with this modified version of data
		if (rtnArray)
		{
			int length = env->GetArrayLength(rtnArray);
			if ((length > 0))
			{
				jbyte* array = (jbyte*)env->GetPrimitiveArrayCritical(rtnArray, 0);
				if (array)
				{
					// It is ideal if the modified data can still fit in the original buffer, AND the data
					// buffer is not originated from binder (which is mmap-ed read-only.)
					if ( (length <= tr->data_size) && (cmd == BC_REPLY) )
					{
//						// Grant write permission to the memory address returned by binder.
						// DOES NOT WORK because the memory space is mmap-ed READ-ONLY.
//						char * p = (char *)(((int) tr->data.ptr.buffer + PAGESIZE-1) & ~(PAGESIZE-1));
//						if (mprotect(p, length + ( p - (char*)tr->data.ptr.buffer), PROT_READ|PROT_WRITE)) {
//							LOGE("mprotect(%.8x %d) failed: %d", p, length + ( p - (char*)tr->data.ptr.buffer), errno);
//						}
						memcpy((void*)tr->data.ptr.buffer, array, length);
						tr->data_size = length;
					}
					else
					{// Otherwise we have to allocate new buffer, which WILL cause memory leaks (as we don't know when to free them).
//						LOGE("process_reply_data: Bigger buffer will leak memory.");
						//TODO: Fix memory leaks.
						rtnPtr = (char*) malloc(length);
						memcpy(rtnPtr, array, length);

						*oldDataSize = tr->data_size;
						tr->data.ptr.buffer = rtnPtr;
						tr->data_size = length;
					}
					env->ReleasePrimitiveArrayCritical(rtnArray, array, 0);
				}
			}
			else
			{
				LOGE("Cannot modify write_buffer with an empty buffer");
			}
//			env->DeleteGlobalRef(parcel);
		}
    }
    else
    {
    	unsigned  err = *(unsigned*)(tr->data.ptr.buffer);
    	LOGI("process_reply_data: status: %.8x", err);
    }
    return rtnPtr;
}

int ioctl_binder_write_read(int fd, int request, void* data, int* Handled)
{
    struct binder_write_read* bwr = (struct binder_write_read*) data;
    unsigned write_buffer = bwr->write_buffer;
    int write_size = bwr->write_size;
    int rtnVal = -1;
	int pos = 0;

	size_t oldSize;
	char* newBufPtr;
	char* allocBufs[16];
	size_t oldBufSize[16];
	int allocBufCount = 0;

	static volatile int uid = 1;
	int cid = uid++;

	int transCmd = -1, transReply = -1;
	// Process coalesced commands
	while(pos < write_size)
	{
		unsigned cmd = *(unsigned*)(write_buffer + pos);
//		LOGIC("Thread(%d): %d %s", gettid(), cid, cmd_name(cmd));
		switch(cmd){
		case BC_TRANSACTION:
		case BC_REPLY:
			transCmd = cmd;
			if (cmd == BC_TRANSACTION)
				newBufPtr = process_transaction_data(write_buffer + pos, &oldSize);
			else
				newBufPtr = process_reply_data(write_buffer + pos, &oldSize);
			if (newBufPtr)
			{
				LOGI("Replacing buffer(%d) with %.8x", oldSize, newBufPtr);
				allocBufs[allocBufCount] = newBufPtr;
				oldBufSize[allocBufCount] = oldSize;
				allocBufCount++;
			}

			pos += (4 + sizeof(struct binder_transaction_data));
			break;
		case BC_ACQUIRE_RESULT:
		case BC_FREE_BUFFER:
		case BC_INCREFS:
		case BC_ACQUIRE:
		case BC_RELEASE:
		case BC_DECREFS:
			pos += (4 + sizeof(int));
			break;
		case BC_INCREFS_DONE:
		case BC_ACQUIRE_DONE:
		case BC_ATTEMPT_ACQUIRE:
		case BC_REQUEST_DEATH_NOTIFICATION:
		case BC_CLEAR_DEATH_NOTIFICATION:
			pos += (4 + sizeof(struct binder_ptr_cookie));
			break;
		case BC_REGISTER_LOOPER:
		case BC_ENTER_LOOPER:
		case BC_EXIT_LOOPER:
			pos += 4;
			break;
		case BC_DEAD_BINDER_DONE:
			pos += (4 + sizeof(void *));
			break;
		default:
			LOGE("parse_binder_write_read: Unknown command.");
			break;
		}
	}

	// Invoke ioctl and inform caller that we have done so.
	*Handled = 1;
//	LOGIC("Thread(%d): UID-Bef: %d %s", gettid(), cid, cmd_name(transCmd));
	rtnVal = ioctl(fd, request, data);
	// Free malloc-ed buffers
	if (allocBufCount)
	{
		for(int i=0;i<allocBufCount;i++)
		{
			free(allocBufs[i]);
		}
	}

//	LOGI("w_c:%d, w_s:%d, r_c:%d, r_s:%d", bwr->write_consumed, bwr->write_size, bwr->read_consumed, bwr->read_size);
	if (rtnVal >= 0)
	{
		// Process coalesced replies.
		pos = 0;
		allocBufCount = 0;
		unsigned read_buffer = bwr->read_buffer;
		int read_size = bwr->read_consumed;
		while(pos < read_size)
		{
			unsigned cmd = *(unsigned*)(read_buffer + pos);
//			LOGIC("Thread(%d): %d %s", gettid(),uid, reply_name(cmd));
			switch(cmd)
			{
			case BR_ERROR:
			case BR_ACQUIRE_RESULT:
				pos += (4 + sizeof(int));
				break;
			case BR_OK:
			case BR_DEAD_REPLY:
			case BR_TRANSACTION_COMPLETE:
			case BR_NOOP:
			case BR_SPAWN_LOOPER:
			case BR_FINISHED:
			case BR_FAILED_REPLY:
				pos += 4;
				break;
			case BR_TRANSACTION:
			case BR_REPLY:
				transReply = cmd;

				if (cmd == BR_TRANSACTION)
					newBufPtr = process_transaction_data(read_buffer + pos, &oldSize);
				else
					newBufPtr = process_reply_data(read_buffer + pos, &oldSize);
				if (newBufPtr)
				{
					LOGI("Replacing buffer(%d) with LEAKY %.8x", oldSize, newBufPtr);
					allocBufs[allocBufCount++] = newBufPtr;
				}

				pos += (4 + sizeof(struct binder_transaction_data));
				break;
			case BR_INCREFS:
			case BR_ACQUIRE:
			case BR_RELEASE:
			case BR_DECREFS:
				pos += (4 + sizeof(struct binder_ptr_cookie));
				break;
			case BR_ATTEMPT_ACQUIRE:
				pos += (4 + sizeof(struct binder_pri_ptr_cookie));
				break;
			case BR_DEAD_BINDER:
			case BR_CLEAR_DEATH_NOTIFICATION_DONE:
				pos += (4 + sizeof(void *));
				break;
			default:
				LOGE("parse_binder_write_read: Unknown reply.");
				break;
			}
		}
	}

//	LOGIC("Thread(%d): UID-Aft: %d %s", gettid(), cid, reply_name(transReply));
	return rtnVal;
}
#define LOG_IOCTL(x) case (x): \
	                   /*LOGI("binder: " #x);*/ \
	                   break;
int binder_ioctl(int fd, int request, void* data)
{
    struct binder_write_read* bwr = (struct binder_write_read*) data;
    unsigned long write_buffer;
    signed long write_size;
    unsigned write_cmd;
    int rtnVal = -1;
    int Handled = 0;
	switch(request)
	{
	case BINDER_WRITE_READ:
		write_buffer = bwr->write_buffer;
		write_size = bwr->write_size;
		write_cmd = *(unsigned *)bwr->write_buffer;

		rtnVal = ioctl_binder_write_read(fd, request, data, &Handled);

		LOGV("    BINDER_WRITE_READ %.8x(%d)_%s, %.8x(%d)_%s", write_buffer, write_size, cmd_name(write_cmd),
				bwr->read_buffer, bwr->read_consumed, bwr->read_consumed >= 4 ? reply_name(*(unsigned*)bwr->read_buffer) : "");
		break;
	LOG_IOCTL(BINDER_SET_IDLE_TIMEOUT);
	LOG_IOCTL(BINDER_SET_MAX_THREADS);
	LOG_IOCTL(BINDER_SET_IDLE_PRIORITY);
	LOG_IOCTL(BINDER_SET_CONTEXT_MGR);
	LOG_IOCTL(BINDER_THREAD_EXIT);
	LOG_IOCTL(BINDER_VERSION);
	default:
		LOGE("binder: Unrecognized IOCTL %d", request);
		break;
	}
	if (Handled)
		return rtnVal;
	else
		return ioctl(fd, request, data);
}

int my_libc_ioctl(int fd, int request, ...)
{
    va_list ap;
    void * arg;

    va_start(ap, request);
    arg = va_arg(ap, void *);
    va_end(ap);

	char fd_link[256];
	sprintf(fd_link, "/proc/self/fd/%d", fd);
	char fd_name[256];
	int s = readlink(fd_link, fd_name, sizeof(fd_name));
	fd_name[s] = '\0';
	LOGV("my_libc_ioctl: %s(%d) %.8x %.8x.", fd_name, fd, request, arg);
	if (!strcmp(fd_name, "/dev/binder"))
	{
		return binder_ioctl(fd, request, arg);
	}
	else
		return ioctl(fd, request, arg);
}


