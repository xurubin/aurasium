#define LOG_TAG "apihook_ReflectionHandler"
//#define LOG_FILE "/sdcard/log.txt"
#include "logfile.h"
#define NULL 0

#include <stdint.h>
#include <dlfcn.h>
#include <string.h>
// from dalvik/vm/Common.h
typedef uint8_t             u1;
typedef uint16_t            u2;
typedef uint32_t            u4;
typedef uint64_t            u8;
typedef int8_t              s1;
typedef int16_t             s2;
typedef int32_t             s4;
typedef int64_t             s8;

struct Object;

union JValue {
    u1      z;
    s1      b;
    u2      c;
    s2      s;
    s4      i;
    s8      j;
    float   f;
    double  d;
    Object* l;
};

// from dalvik/vm/oo/Object.h
struct ClassObject;
struct Object {
    /* ptr to class object */
    ClassObject*    clazz;

    /*
     * A word containing either a "thin" lock or a "fat" monitor.  See
     * the comments in Sync.c for a description of its layout.
     */
    u4              lock;
};
#define CLASS_FIELD_SLOTS   4
struct ClassObject : Object {
    /* leave space for instance data; we could access fields directly if we
       freeze the definition of java/lang/Class */
    u4              instanceData[CLASS_FIELD_SLOTS];

    /* UTF-8 descriptor for the class; from constant pool, or on heap
       if generated ("[C") */
    const char*     descriptor;

    /* ...And More... */
};

typedef void (*DalvikNativeFunc)(const u4* args, JValue* pResult);

// from dalvik/vm/Native.h
struct DalvikNativeMethod {
    const char* name;
    const char* signature;
    DalvikNativeFunc  fnPtr;
};

// Trampoline functions prototypes
DalvikNativeFunc old_invokeNative = NULL;
static void Dalvik_java_lang_reflect_Method_invokeNative(const u4* args, JValue* pResult);

DalvikNativeFunc old_setField = NULL;
static void Dalvik_java_lang_reflect_Field_setField(const u4* args, JValue* pResult);
DalvikNativeFunc old_setPrimitiveField = NULL;
static void Dalvik_java_lang_reflect_Field_setPrimitiveField(const u4* args, JValue* pResult);

struct HookMethod {
	const char* MethodName;
	const char* MethodSignature;
	DalvikNativeFunc* OriginalFuncPP;
	DalvikNativeFunc NewFuncPtr;
};

struct HookMethod HookList_reflect_Method[] = //dvm_java_lang_reflect_Method
{
	{
		"invokeNative",
		"(Ljava/lang/Object;[Ljava/lang/Object;Ljava/lang/Class;[Ljava/lang/Class;Ljava/lang/Class;IZ)Ljava/lang/Object;",
		&old_invokeNative,
		Dalvik_java_lang_reflect_Method_invokeNative
	},
	{NULL, NULL, NULL, NULL}
};

struct HookMethod HookList_reflect_Field[] = // dvm_java_lang_reflect_Field
{
	{
		"setField",
		"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;IZLjava/lang/Object;)V",
		&old_setField,
		Dalvik_java_lang_reflect_Field_setField
	},
	{
		"setBField",
		// Android 2.3 and 4.0 have different signatures on the last but one argument: I(int) <-> C(char)
		"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;IZIB)V",
		&old_setPrimitiveField,
		Dalvik_java_lang_reflect_Field_setPrimitiveField
	},
	{
		"setCField",
		"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;IZIC)V",
		&old_setPrimitiveField,
		Dalvik_java_lang_reflect_Field_setPrimitiveField
	},
	{
		"setDField",
		"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;IZID)V",
		&old_setPrimitiveField,
		Dalvik_java_lang_reflect_Field_setPrimitiveField
	},
	{
		"setFField",
		"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;IZIF)V",
		&old_setPrimitiveField,
		Dalvik_java_lang_reflect_Field_setPrimitiveField
	},
	{
		"setIField",
		"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;IZII)V",
		&old_setPrimitiveField,
		Dalvik_java_lang_reflect_Field_setPrimitiveField
	},
	{
		"setJField",
		"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;IZIJ)V",
		&old_setPrimitiveField,
		Dalvik_java_lang_reflect_Field_setPrimitiveField
	},
	{
		"setSField",
		"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;IZIS)V",
		&old_setPrimitiveField,
		Dalvik_java_lang_reflect_Field_setPrimitiveField
	},
	{
		"setZField",
		"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;IZIZ)V",
		&old_setPrimitiveField,
		Dalvik_java_lang_reflect_Field_setPrimitiveField
	},
	{NULL, NULL, NULL, NULL}
};

struct HookList {
	const char* ClassName;
	const char* ObjectName;
	struct HookMethod* Methods;
} reflection_hooks[] = {
		{"Ljava/lang/reflect/Method;", "dvm_java_lang_reflect_Method", HookList_reflect_Method},
		{"Ljava/lang/reflect/Field;", "dvm_java_lang_reflect_Field", HookList_reflect_Field},
		{NULL, NULL}
};

typedef ClassObject* (*dvmFindClassNoInitFunc)(const char* descriptor, Object* loader);
typedef void* (*dvmFindDirectMethodByDescriptorFunc)(const ClassObject* clazz, const char* methodName, const char* signature);
typedef void (*dvmSetNativeFuncFunc)(void* method, DalvikNativeFunc func, const u2* insns);
/*
ClassObject* dvmFindClassNoInit(const char* descriptor, Object* loader);
Method* dvmFindDirectMethodByDescriptor(const ClassObject* clazz, const char* methodName, const char* signature);
void dvmSetNativeFunc(Method* method, DalvikBridgeFunc func, const u2* insns);
 */
void HookReflectionMethods()
{
	void* libdvm = dlopen("libdvm.so", RTLD_NOW);
	if (!libdvm)
	{
		LOGEC("Invalid libdvm.so handle!");
		return;
	}
	dvmFindClassNoInitFunc dvmFindClassNoInit = (dvmFindClassNoInitFunc)dlsym(libdvm, "dvmFindClassNoInit");
	if (!dvmFindClassNoInit)
		dvmFindClassNoInit =  (dvmFindClassNoInitFunc)dlsym(libdvm, "_Z18dvmFindClassNoInitPKcP6Object");

	dvmFindDirectMethodByDescriptorFunc dvmFindDirectMethodByDescriptor = (dvmFindDirectMethodByDescriptorFunc)dlsym(libdvm, "dvmFindDirectMethodByDescriptor");
	if (!dvmFindDirectMethodByDescriptor)
		dvmFindDirectMethodByDescriptor = (dvmFindDirectMethodByDescriptorFunc)dlsym(libdvm, "_Z31dvmFindDirectMethodByDescriptorPK11ClassObjectPKcS3_");

	dvmSetNativeFuncFunc dvmSetNativeFunc = (dvmSetNativeFuncFunc)dlsym(libdvm, "dvmSetNativeFunc");
	if (!dvmSetNativeFunc)
		dvmSetNativeFunc = (dvmSetNativeFuncFunc)dlsym(libdvm, "_Z16dvmSetNativeFuncP6MethodPFvPKjP6JValuePKS_P6ThreadEPKt");

	if ((!dvmFindClassNoInit) || (!dvmFindDirectMethodByDescriptor) ||(!dvmSetNativeFunc))
	{
		LOGEC("Cannot find methods: ");
		LOGEC("dvmFindClassNoInit: %p", dvmFindClassNoInit);
		LOGEC("dvmFindDirectMethodByDescriptor: %p", dvmFindDirectMethodByDescriptor);
		LOGEC("dvmSetNativeFunc: %p", dvmSetNativeFunc);
		return;
	}
	for(int i = 0; reflection_hooks[i].ObjectName; i++)
	{
		// Replace fun ptrs in dvm_methods with hook_methods
		ClassObject* clazz = dvmFindClassNoInit(reflection_hooks[i].ClassName, NULL);
		LOGIC("dvmFindClassByName(%s) returns %p.", reflection_hooks[i].ClassName, clazz);
		HookMethod* hook_methods = reflection_hooks[i].Methods;
		struct DalvikNativeMethod* dvm_methods = (struct DalvikNativeMethod*)dlsym(libdvm, reflection_hooks[i].ObjectName);

		if (!dvm_methods)
		{
			LOGEC("Cannot locate %s in libdvm.so", reflection_hooks[i].ObjectName);
			continue;
		}
		for (int j=0; hook_methods[j].MethodName; j++)
		{
			bool hooked = false;
			// Search in dvm_methods for matching hook_methods[j] and hook it
			for(int k = 0; dvm_methods[k].name; k++)
				if (!strcmp(dvm_methods[k].name, hook_methods[j].MethodName)
				&& !strcmp(dvm_methods[k].signature, hook_methods[j].MethodSignature))
				{
					hooked = true;
					if (*hook_methods[j].OriginalFuncPP && (*hook_methods[j].OriginalFuncPP != dvm_methods[k].fnPtr))
						LOGIC("Double-use OriginalFuncPP in %s.", hook_methods[j].MethodName);
					// Store original entry in dvm_methods[k];
					LOGIC("Replace %p @ %p with %p", dvm_methods[k].fnPtr, &(dvm_methods[k].fnPtr), hook_methods[j].NewFuncPtr);
					*hook_methods[j].OriginalFuncPP = dvm_methods[k].fnPtr;
					// Hook
					//TODO:
					dvm_methods[k].fnPtr = hook_methods[j].NewFuncPtr;
					if (dvm_methods[k].fnPtr != hook_methods[j].NewFuncPtr)
						LOGEC("Error");
					if (clazz)
					{
						void *method = dvmFindDirectMethodByDescriptor(clazz, hook_methods[j].MethodName, hook_methods[j].MethodSignature);
						if (method)
						{
							LOGIC("dvmSetNativeFunc(%p) invoked. %x %x %x", method, *(int*)((char*)method+0x24), *(int*)((char*)method+0x28), *(int*)((char*)method+0x2C));
							dvmSetNativeFunc(method, hook_methods[j].NewFuncPtr, NULL);
							LOGIC("dvmSetNativeFunc(%p) invoked. %x %x %x", method, *(int*)((char*)method+0x24), *(int*)((char*)method+0x28), *(int*)((char*)method+0x2C));
						}
						else
							LOGEC("dvmFindDirectMethodByDescriptor returns NULL.");
					}
				}
			if (!hooked)
				LOGEC("%s %s is not found in libdvm.", hook_methods[j].MethodName, hook_methods[j].MethodSignature);
			else
				LOGIC("Hooked %s(%s).", hook_methods[j].MethodName, hook_methods[j].MethodSignature);
		}
	}

}

bool allowReflectionAccess(ClassObject* cls)
{
//	LOGIC("check access: %s", cls->descriptor);
	return !strstr(cls->descriptor, "Lcom/rx201/apkmon/");
}

static void Dalvik_java_lang_reflect_Method_invokeNative(const u4* args,
    JValue* pResult)
{
    ClassObject* declaringClass = (ClassObject*) args[3];
	if (allowReflectionAccess(declaringClass) && old_invokeNative)
		old_invokeNative(args, pResult);
	else
		LOGIC("Block access of invokeNative on %s", declaringClass->descriptor);
}

static void Dalvik_java_lang_reflect_Field_setField(const u4* args, JValue* pResult)
{
	ClassObject* declaringClass = (ClassObject*) args[2];
	if (allowReflectionAccess(declaringClass) && old_setField)
		old_setField(args, pResult);
	else
		LOGIC("Block access of setField on %s", declaringClass->descriptor);
}

static void Dalvik_java_lang_reflect_Field_setPrimitiveField(const u4* args, JValue* pResult)
{
	ClassObject* declaringClass = (ClassObject*) args[2];
	if (allowReflectionAccess(declaringClass) && old_setPrimitiveField)
		old_setPrimitiveField(args, pResult);
	else
		LOGIC("Block access of setPrimitiveField on %s", declaringClass->descriptor);
}

