#!/bin/sh
DIR="$( cd "$( dirname "$0" )" && pwd )"
PyRewrite=$DIR"/src/RewriterMain.py"
AppMonRoot="/export/u1/homes/rubin/workspace/ApkMonitor/"

WorkDir=$DIR"/tmp/"
ApkFile=$1
NewApkFile=${ApkFile%.apk}_new.apk

rm -rf ${WorkDir} 2>&1  >/dev/null
echo "Unpacking.."
apktool d -r $ApkFile $WorkDir
echo "Gluing.."
python ${PyRewrite} glue ${WorkDir}
echo "Dexing.."
CurDir=`pwd`
cd ${AppMonRoot}"/bin"
dx --dex --output=$WorkDir"/APIHook.dex" "./com/rx201/apkmon/APIHook.class"
echo "Baksmaling.."
cd $WorkDir
baksmali -o smali APIHook.dex
echo "Adding native lib.."
cd $CurDir
mkdir -p $WorkDir"/lib/armeabi"
cp $AppMonRoot"/libs/armeabi/libapihook.so" $WorkDir"/lib/armeabi/"
echo "Repacking.."
apktool b $WorkDir ${NewApkFile}
echo "Signing.."
jarsigner  -keystore ~/.android/debug.keystore -storepass android -keypass android  $NewApkFile androiddebugkey
unzip -p ${NewApkFile}  classes.dex  > classes.dex


