/*
 * logfile.h
 *
 *  Created on: Sep 6, 2011
 *      Author: rubin
 */

#ifndef LOGFILE_H_
#define LOGFILE_H_

#include <android/log.h>

#ifndef LOG_TAG
#define  LOG_TAG    "apihook"
#endif

#if DISABLE_LOGGING
#define LOGIC(...)
#define LOGEC(...)
#else
#define  LOGIC(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGEC(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#endif

#if DISABLE_LOGGING
#define LOGI(...)
#define LOGE(...)
#elif defined(LOG_FILE)
void logi_file(const char* fname, const char * str, ...);
#define  LOGI(...)  logi_file(LOG_FILE, __VA_ARGS__)
#define  LOGE(...)  logi_file(LOG_FILE, __VA_ARGS__)
#else
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#endif


#define LOGV(...)

#endif /* LOGFILE_H_ */
