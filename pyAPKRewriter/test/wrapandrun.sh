#!/bin/bash
DIR="$( cd "$( dirname "$0" )" && pwd )"
attach=$DIR"/../attach.sh"
runapp=$DIR"/runapp.sh"

APP=$1
adb_arg=$2

ApkBaseName=`basename $APP`
NewApkFile=${ApkBaseName%.apk}_new.apk

$attach $APP
rc=$?
if [ $rc != 0 ] ; then
    echo "Attaching failed."
    exit $rc
fi

$runapp $NewApkFile $adb_arg
