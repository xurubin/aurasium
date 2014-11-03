#include <sys/system_properties.h>
#include <string.h>
#define  LOG_TAG "apihook_misc"
#include "logfile.h"

const char* fake_Fingerprint = "generic/google_sdk/generic:2.3.4/GINGERBREAD/123630:eng/release-keys";
//TODO: This is useless because android.os.BUILD.<cinit> has been called when zygote started.
int my__system_property_read(const char* *pi, char *name, char *value)
{
	LOGIC("Read Property: %s", name);
	int len = __system_property_read((const prop_info*)pi, name, value);
	if (len)
	{
		LOGIC("Read Property: %s = %s", name, value);
		if (!strcmp(name, "ro.build.fingerprint"))
		{
			strcpy(value, fake_Fingerprint);
			return strlen(fake_Fingerprint);
		}
	}
	return len;
}
