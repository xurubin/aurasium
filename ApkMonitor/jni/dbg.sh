#!/bin/sh
adb shell ps | grep com.rx201.apkmon | awk '{print $2}' | xargs adb shell kill
adb push ../libs/armeabi/libapihook.so /data/data/com.rx201.apkmon/lib/libapihook.so
~/android-ndk-r6/ndk-gdb --start


