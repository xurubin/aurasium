/*
 * logfile.cpp
 *
 *  Created on: Sep 6, 2011
 *      Author: rubin
 */
#include <stdio.h>
#include <stdarg.h>
#include <android/log.h>

void logi_file(const char* fname, const char * str, ...)
{
	FILE* flog = fopen(fname, "a");
	if (!flog)
	{
		__android_log_print(ANDROID_LOG_ERROR, "apihook", "Cannot write to SD Card.");
	}
    va_list argptr;
    va_start(argptr, str); //Requires the last fixed parameter (to get the address)
    vfprintf(flog, str, (__va_list)argptr);
    va_end(argptr);
    fprintf(flog, "\n");
	fclose(flog);
}
