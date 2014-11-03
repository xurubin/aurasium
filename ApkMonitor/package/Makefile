SRC_DIR=../
dx=/cygdrive/c/Windows/system32/java.exe -Xmx1024M -jar C:/Android/android-sdk/platform-tools/lib/dx.jar
baksmali=/cygdrive/c/Windows/system32/java.exe -Xmx1024M -jar c:/apktool/baksmali-1.2.8.jar

SRC_JAVA=$(shell cd $(SRC_DIR)/bin/classes/; find com/rx201/apkmon/ -name *.class )
SRC_NATIVE=$(wildcard $(SRC_DIR)/libs/armeabi/*.so)
SRC_NATIVE_v7a=$(wildcard $(SRC_DIR)/libs/armeabi-v7a/*.so)

CurDir=$(shell pwd)

.PHONY: all,clean

all: aurasium.zip

aurasium.zip: smali lib
	rm -f $@
	zip -r $@ $+
	cp $@ ../../dependencies/
clean:
	rm -rf smali
	rm -rf lib
	rm -f APIHook.dex
	rm -f aurasium.zip
	
lib: $(SRC_NATIVE) $(SRC_NATIVE_v7a)
	rm -rf $@
	mkdir -p lib/armeabi
	mkdir -p lib/armeabi-v7a
	echo $+
	cp $(filter $(SRC_DIR)/libs/armeabi/%, $+) lib/armeabi/
	cp $(filter $(SRC_DIR)/libs/armeabi-v7a/%, $+) lib/armeabi-v7a/
	
smali: APIHook.dex
	rm -rf $@
	$(baksmali) -o smali APIHook.dex
	
APIHook.dex: $(addprefix $(SRC_DIR)/bin/classes/, $(SRC_JAVA))
	cd  $(SRC_DIR)/bin/classes/; $(dx) --dex --output="$(shell cygpath -d $(CurDir))/APIHook.dex" $(subst $$,\$$, $(SRC_JAVA))
