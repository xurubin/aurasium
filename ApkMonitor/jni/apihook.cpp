/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <string.h>
#include <jni.h>
#include <sys/mman.h>
#include <errno.h>
#include <unistd.h>
#include <stdio.h>
#include <elf.h>

#define R_ARM_NONE       0
#define R_ARM_COPY       20
#define R_ARM_GLOB_DAT   21
#define R_ARM_JUMP_SLOT  22
#define R_ARM_RELATIVE   23

/* According to the AAPCS specification, we only
 * need the above relocations. However, in practice,
 * the following ones turn up from time to time.
 */
#define R_ARM_ABS32      2
#define R_ARM_REL32      3

#define R_386_32         1
#define R_386_PC32       2
#define R_386_GLOB_DAT   6
#define R_386_JUMP_SLOT  7
#define R_386_RELATIVE   8

#ifndef NDEBUG
#define LOG_FILE "/sdcard/log.txt"
#endif
#define  LOG_TAG "apihook_ldr"
#include "logfile.h"

#include "reflection_blocker.h"
#include "JNIHelper.h"
bool hasStrictMode;

#include "hook_func.h"
/* Hook functions  */
struct hook_func {
	const char* so_filename;
	const char* func_name;
	void * original_ptr;
	void * hook_ptr;
};
struct hook_func hook_funcs[] = {
//		{"libc.so",  "read", NULL, (void*)my_libc_read},
		{"libc.so",  "getaddrinfo", NULL, (void*)my_libc_getaddrinfo},
		{"libc.so",  "connect", NULL, (void*)my_libc_connect},
		{"libc.so", "ioctl", NULL, (void*)my_libc_ioctl},
		{"libc.so", "dlopen", NULL, (void*)my_libc_dlopen},
		{"libc.so", "fork", NULL, (void*)my_libc_fork},
		{"libc.so", "execvp", NULL, (void*)my_libc_execvp},
//		{"libc.so", "close", NULL, (void*)my_libc_close},
//		{"libc.so", "write",NULL,(void*)my_libc_write},
//		{"libc.so", "read",NULL,(void*)my_libc_read},
//		{"libc.so", "fopen", NULL, (void*)my_libc_fopen},
//		{"libc.so", "open", NULL, (void*)my_libc_open},
		{"libc.so", "__system_property_read", NULL, (void*)my__system_property_read},
		{NULL, NULL, NULL, NULL}
};

unsigned dvmRelroStart, dvmRelroSize;

#define FALSE   0
#define  TRUE   1

/* Process memory map data */
struct mem_map {
	unsigned int start;
	unsigned int  end;
	char permission[4];
	char* name;
};

struct mem_map memmap[1024];
int memmap_count;

/* elf file information */
struct soinfo
{
	Elf32_Ehdr *ehdr;
    Elf32_Phdr *phdr;
    int phnum;
    unsigned base;
    unsigned size;

    unsigned *dynamic;


    const char *strtab;
    Elf32_Sym *symtab;

    unsigned *plt_got;

    Elf32_Rel *plt_rel;
    unsigned plt_rel_count;

    Elf32_Rel *rel;
    unsigned rel_count;
};


static int skipchars(const char* str, int i, int skipWhiteSpace)
{
	if (skipWhiteSpace)
	{
		while ( str[i] && ((str[i] == ' ') || (str[i] == '\t'))) i++;
		return i;
	}
	else
	{
		while ( str[i] && ((str[i] != ' ') && (str[i] != '\t'))) i++;
		return i;
	}
}
static int nextField(const char* str, int i)
{
	i = skipchars(str, i, 0);
	i = skipchars(str, i, 1);
	return i;
}
int readMemMap(void)
{
	FILE* fd = fopen("/proc/self/maps", "rb");
	char line[1024];
	int i;
	memmap_count = 0;
	while (fgets(line, sizeof(line), fd))
	{
		i = strlen(line) - 1;
		while((line[i] == '\r') || (line[i] == '\n')) line[i--] = '\0';

		i = 0;
		sscanf(line, "%8x-%8x", &memmap[memmap_count].start, &memmap[memmap_count].end);
		i = nextField(line, i);

		strncpy(memmap[memmap_count].permission, line + i, 4);
		i = nextField(line, i);

		i = nextField(line, i);
		i = nextField(line, i);
		i = nextField(line, i);

		memmap[memmap_count].name = strdup(line + i);

		memmap_count++;
}
	fclose(fd);
	return memmap_count;
}

void printMemMap(void)
{
	int i;
	for (i = 0; i < memmap_count; i++)
	{
		LOGI("%.8X-%.8X   %.4s   %s", memmap[i].start, memmap[i].end, memmap[i].permission, memmap[i].name);
	}

}

int load_elf(Elf32_Ehdr* ehdr, struct soinfo* si)
{
	memset(si, 0, sizeof(*si));

	si->base = (unsigned) ehdr;
	si->ehdr = ehdr;
    si->phdr = (Elf32_Phdr *)((unsigned char *)ehdr + ehdr->e_phoff);
    si->phnum = ehdr->e_phnum;
    si->dynamic = (unsigned *)-1;

    int is_exec = (ehdr->e_type == ET_EXEC);
    if (is_exec)
    	si->base = 0;

    Elf32_Phdr * phdr = si->phdr;
    int phnum = si->phnum;
	for(; phnum > 0; --phnum, ++phdr) {
		if (phdr->p_type == PT_DYNAMIC) {
			if (si->dynamic != (unsigned *)-1) {
				LOGE("multiple PT_DYNAMIC segments found. "
					  "Segment at 0x%08x, previously one found at 0x%08x",
					  si->base + phdr->p_vaddr,
					  (unsigned)si->dynamic);
				return FALSE;
			}
			si->dynamic = (unsigned *) (si->base + phdr->p_vaddr);
		}
		else if (phdr->p_type == PT_LOAD) {
            if (phdr->p_vaddr + phdr->p_memsz > si->size)
                si->size = phdr->p_vaddr + phdr->p_memsz;
		}
	}


    if (si->dynamic == (unsigned *)-1) {
    	LOGE("missing PT_DYNAMIC?!");
        return FALSE;
    }

    LOGI("dynamic = %p\n", si->dynamic);

    unsigned *d;
    /* extract useful information from dynamic section */
    for(d = si->dynamic; *d; d++){
//        LOGE("d = %p, d[0] = 0x%08x d[1] = 0x%08x\n", d, d[0], d[1]);
        switch(*d++){
        case DT_STRTAB:
            si->strtab = (const char *) (si->base + *d);
            break;
        case DT_SYMTAB:
            si->symtab = (Elf32_Sym *) (si->base + *d);
            break;
        case DT_PLTREL:
        	LOGI("DT_PLTREL");
            if(*d != DT_REL) {
            	// Only support implicit addends for relocation table
                LOGE("DT_RELA not supported");
            	return FALSE;
            }
            break;

        case DT_JMPREL:
            si->plt_rel = (Elf32_Rel*) (si->base + *d);
        	LOGI("DT_JMPREL %.8X", si->plt_rel);
            break;
        case DT_PLTRELSZ:
            si->plt_rel_count = *d / 8;
        	LOGI("DT_PLTRELSZ %.8X", si->plt_rel_count);
            break;
//        case DT_RELA:
//           LOGE("DT_RELA not supported");
//           return FALSE;
        case DT_REL:
            si->rel = (Elf32_Rel*) (si->base + *d);
        	LOGI("DT_REL %.8X", si->rel);
            break;
        case DT_RELENT:
            if (*d != 8)
            {
            	LOGE("DT_RELENT != 8 !");
            	return FALSE;
            }
            break;
        case DT_RELSZ:
            si->rel_count = *d / 8;
        	LOGI("DT_RELSZ %.8X", si->rel_count);
            break;
        case DT_PLTGOT:
            /* Save this in case we decide to do lazy binding. We don't yet. */
            si->plt_got = (unsigned *)(si->base + *d);
        	LOGI("DT_PLTGOT %.8X", si->plt_got);
            break;
        case DT_TEXTREL:
            /* TODO: make use of this. */
            /* this means that we might have to write into where the text
             * segment was loaded during relocation... Do something with
             * it.
             */
            LOGI("Text segment should be writable during relocation.");
            break;
        }
    }

    LOGI("si->strtab = %p, si->symtab = %p\n",
            si->strtab, si->symtab);

    if((si->strtab == 0) || (si->symtab == 0)) {
    	LOGE("missing essential tables");
        return FALSE;
    }
	return TRUE;
}

// Returns if addr belongs to si
int containsAddr(struct soinfo* si, unsigned addr)
{
    return (addr >= si->base) && (addr - si->base < si->size);
}
/*
 * Replace symbolic references of SymbolName in module si with newAddr
 * if *oldAddr != 0, only hook places where the old value equals to *oldAddr
 * Otherwise substitute the value at first occurrence into oldAddr.
 * if oldAddr == 0 then do not apply this check at all.
 * Returns the number of hooked places.
 */
int patchReloc(struct soinfo* si,  const char* SymbolName, unsigned* pOldAddr, unsigned NewAddr)
{
    Elf32_Sym *symtab = si->symtab;
    const char *strtab = si->strtab;
    unsigned idx, i, patched_count = 0;
    unsigned SymbolValue = 0;
    if (pOldAddr)
    	SymbolValue = *pOldAddr;


    Elf32_Rel* reloc_entry[] = {si->rel, si->plt_rel};
	unsigned reloc_count[] = {si->rel_count, si->plt_rel_count};
	for(i=0;i<2;i++)
	{
		Elf32_Rel * rel = reloc_entry[i];
		if (!rel) continue;
	    for (idx = 0; idx < reloc_count[i]; ++idx, ++rel) {
	        unsigned type = ELF32_R_TYPE(rel->r_info);
	        unsigned sym = ELF32_R_SYM(rel->r_info);
	        unsigned reloc = (unsigned)(rel->r_offset + si->base);
	        unsigned sym_addr = 0;
	        char *sym_name = NULL;

	        if(sym != 0) {
	            sym_name = (char *)(strtab + symtab[sym].st_name);
				unsigned reloc_v =  *((unsigned*)reloc);

				// Check if this is the relocation we want to patch
				// First compare if the old value matches the target (succeed by default if we don't know the target value yet.)
				// Then do the string comparison to be sure.
				if ( (pOldAddr == 0) || (SymbolValue == 0) || (reloc_v == SymbolValue) )
				{
					if (!strcmp(SymbolName, sym_name))
					{
						SymbolValue = reloc_v;
						*((unsigned*)reloc) = NewAddr;
						patched_count++;
					}
					else
					{
						if ((!SymbolValue) && reloc_v == SymbolValue )
							LOGI("sym_name mismatch: %s, %.8x", sym_name, reloc_v);
					}
				}
	        }
/*
	        switch(type){
	        case R_ARM_JUMP_SLOT:
	        	LOGE("R_ARM_JUMP_SLOT %s %.8x", sym_name, reloc);
//	            *((unsigned*)reloc) = sym_addr;
	            break;
	        case R_ARM_GLOB_DAT:
	        	LOGE("R_ARM_GLOB_DAT %s %.8x", sym_name, reloc);
//	            *((unsigned*)reloc) = sym_addr;
	            break;
	        case R_ARM_ABS32:
	        	LOGE("R_ARM_ABS32 %s %.8x", sym_name, reloc);
//            *((unsigned*)reloc) += sym_addr;
	            break;
	        case R_ARM_REL32:
	        	LOGE("R_ARM_REL32 %s %.8x", sym_name, reloc);
//            *((unsigned*)reloc) += sym_addr - rel->r_offset;
	            break;
	        case R_ARM_RELATIVE:
//	            if(sym){
//	                DL_ERR("%5d odd RELATIVE form...", pid);
//	                return -1;
//	            }
	        	LOGE("R_ARM_RELATIVE %s %.8x", sym_name, reloc);
//	            *((unsigned*)reloc) += si->base;
	            break;
	        case R_ARM_COPY:
	        	LOGE("R_ARM_COPY %s %.8x", sym_name, reloc);
//	            memcpy((void*)reloc, (void*)sym_addr, s->st_size);
	            break;
	        case R_ARM_NONE:
	        	LOGE("R_ARM_NONE %s %.8x", sym_name, reloc);
	        	break;
	        default:
	        	LOGE("UNKNOWN Reloc %s %d %.8x", sym_name, type, reloc);
	            break;
	        }
*/
	    } // for idx in reloc
	} // for i in [0,1]
	if (pOldAddr)
		*pOldAddr = SymbolValue;
    return patched_count;
}
int hookAll()
{
	struct hook_func* hc;
	int i;
	int total_count = 0;
	for (hc = hook_funcs; hc->so_filename; hc++)
	{
		hc->original_ptr = NULL;
	}
	for (i = 0; i < memmap_count; i++)
	{
		Elf32_Ehdr* ehdr = (Elf32_Ehdr*)memmap[i].start;
		LOGI("Processing %s(%.8X).", memmap[i].name, memmap[i].start);
		if(!memmap[i].name[0]) continue;
		if(memmap[i].permission[0] != 'r')
		{
			LOGI("No permission.");
			continue;
		}
		if(!IS_ELF(*ehdr))
		{
			LOGI("Bad header.");
			continue;
		}
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

		// Handle RELRO
		int relro_start = 0, relro_size = 0;
		for (int j = i + 1; j < memmap_count; j++) {
			if ((unsigned)si.plt_got >= memmap[j].start && (unsigned)si.plt_got < memmap[j].end && memmap[j].permission[1] != 'w') {
				relro_start = memmap[j].start;
				relro_size = memmap[j].end - relro_start;
				if (mprotect((void*)relro_start, relro_size, PROT_READ | PROT_WRITE)) {
					LOGE("Cannot unprotect RELRO.");
				}
				//libdvm's data section is only restored after hookReflectionMethods
				if (!strcmp(memmap[j].name, "/system/lib/libdvm.so")) {
					dvmRelroStart = relro_start;
					dvmRelroSize = relro_size;
					relro_start = relro_size = 0;
				}
				break;
			}
		}
		for (hc = hook_funcs; hc->so_filename; hc++)
		{
			int pcount = patchReloc(&si, hc->func_name, (unsigned *)&hc->original_ptr, (unsigned)hc->hook_ptr);
			LOGI("Hooking %s (%d places patched.)", hc->func_name, pcount);
			total_count += pcount;
			if (pcount)
				LOGIC("hookAll: %s %s.", memmap[i].name, hc->func_name);
		}

		// Restore relro
		if (relro_start && relro_size) {
			mprotect((void*)relro_start, relro_size, PROT_READ);
		}

	}
	LOGIC("hookAll: %d places patched.", total_count);
	return TRUE;
}

extern "C" {
JNIEXPORT jstring Java_com_rx201_apkmon_APIHook_WriteTest( JNIEnv* env, jobject thiz );
JNIEXPORT void    Java_com_rx201_apkmon_APIHook_Hook( JNIEnv* env, jobject thiz, jboolean bHasStrictMode );
JNIEXPORT void    Java_com_rx201_apkmon_APIHook_KillMe( JNIEnv* env, jobject thiz);
}

char* p = (char*)0xAFD0C020;
JNIEXPORT jstring Java_com_rx201_apkmon_APIHook_WriteTest( JNIEnv* env, jobject thiz )
{
    char txt[256];
    unsigned int base = (unsigned int)p;

    int r = mprotect((const void*)0xAFD00000, 0x40000, PROT_READ | PROT_WRITE);
    if (0 == r)
    {
        *p = 0xAB;
        sprintf(txt, "Hello from JNI: %.8X.", *(unsigned int*)p);
        return env->NewStringUTF(txt);
    }
    else
    {
        sprintf(txt, "mprotect(%.8X) failed: %.8X.", base, errno); 
        return env->NewStringUTF(txt);
    }
}
int isHooked = 0;

int IsUIDialogProcess()
{
	const static char UIDialogSuffix[] = ":APIHookDialog";
	FILE *fp;
	char nice_name[255];

	fp=fopen("/proc/self/cmdline","r");
	if (fp)
	{
		if (fgets(nice_name, sizeof(nice_name), fp))
		{
			// The UI dialog's process name will always end with UIDialogSuffix
			int offset =  strlen(nice_name) - strlen(UIDialogSuffix);
			if (offset >= 0 && strcmp(nice_name + offset, UIDialogSuffix) == 0)
				return 1;
		}
		fclose(fp);
	}
	return 0;
}
JNIEXPORT void Java_com_rx201_apkmon_APIHook_Hook( JNIEnv* env, jobject thiz, jboolean bHasStrictMode )
{
	hasStrictMode = bHasStrictMode;
	if (!isHooked && !IsUIDialogProcess())
	{
		isHooked = 1;
		readMemMap();
		printMemMap();
		hookAll();
		HookReflectionMethods();
		if (dvmRelroStart && dvmRelroSize) {
			mprotect((void*)dvmRelroStart, dvmRelroSize, PROT_READ);
		}
	}
	return;
}

JNIEXPORT void    Java_com_rx201_apkmon_APIHook_KillMe( JNIEnv* env, jobject thiz)
{
	_exit(1);
}

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
	JNIEnv* env;
	if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
		LOGE("ERROR: GetEnv failed\n");
		return -1;
	}

	JNIHelper::Self().Init(vm, env);
	/* success -- return valid version number */
	return JNI_VERSION_1_4;
}
