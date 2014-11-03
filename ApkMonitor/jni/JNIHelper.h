/*
 * JNIHelper.h
 *
 *  Created on: Sep 2, 2011
 *      Author: rubin
 */

#ifndef JNIHELPER_H_
#define JNIHELPER_H_
#include <jni.h>
class JNIHelper {
private:
	static JNIHelper* instance;
	JavaVM* jvm;

	JNIHelper();
public:
	struct parcel_metadata_t
	{
		jclass    Clazz;
		jmethodID Constructor;
	    jfieldID  mObject;
	} gParcelMetadata;

	struct apihook_metadata_t
	{
		jclass    Clazz;

		jmethodID onBcTransact;
		jmethodID onBrTransact;
		jmethodID onBcReply;
		jmethodID onBrReply;

		jmethodID onBeforeConnect;
		jmethodID onDNSResolve;

		jmethodID onDlOpen;

		jmethodID onBeforeExecvp;
	} gApiHookMetadata;

	static JNIHelper& Self();
	void Init(JavaVM* jvm, JNIEnv* env);
	virtual ~JNIHelper();
	JNIEnv* GetEnv();
};

#endif /* JNIHELPER_H_ */
