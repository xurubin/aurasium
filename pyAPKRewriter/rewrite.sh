#!/bin/sh

DIR="$( cd "$( dirname "$0" )" && pwd )"
PyRewrite=$DIR"/src/RewriterMain.py"
PyResign=$DIR"/src/Signer.py"

WorkDir="./tmp"
ApkFile=$1
NewApkFile=${ApkFile%.apk}_new.apk

echo "Verifying original signature.."
$PyResign -verify $ApkFile`
rc=$?
if [ $rc !=0 ] ; then
    echo "Bad signature.Exiting.."
    exit $rc
fi

rm -rf ${WorkDir} 2>&1  >/dev/null
echo "Unpacking.."
apktool d -r $ApkFile  $WorkDir
CurDir=`pwd`
cd ${WorkDir}
echo "Rewriting.."
python ${PyRewrite} rewrite smali invoke > APIHook.java
echo "Javac-ing.."
javac -cp /homes/rubin/android-sdk-linux_x86/platforms/android-10/android.jar APIHook.java
rc=$?
if [ $rc != 0 ] ; then
    echo "Compilation failed."
    exit $rc
fi
echo "Dexing.."
dx --dex --output=APIHook.dex APIHook.class
echo "Baksmaling.."
baksmali -o smali APIHook.dex
echo "Repacking.."
cd $CurDir
apktool b $WorkDir ${NewApkFile}
echo "Signing.."
$PyResign -resign $NewApkFile $ApkFile


