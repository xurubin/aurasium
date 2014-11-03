#!/bin/sh
DIR="$( cd "$( dirname "$0" )" && pwd )/../"
PyAXML=$DIR"/src/axml.py"
aapt=$DIR"/aapt"
ApkFile=$1

$aapt d xmltree $ApkFile AndroidManifest.xml | sed 's/(line=[0-9]*)//' > orig.out
unzip -p $ApkFile AndroidManifest.xml > new.axml
python $PyAXML new.axml AndroidManifest.xml
rm new.apk
$aapt p -F new.apk -I "/homes/rubin/apktool/framework/1.apk" -M AndroidManifest.xml
rc=$?
if [ $rc != 0 -o ! -f new.apk ] ; then
    echo "aapt failed.", $ApkFile
    exit $rc
fi
if [ ! -f new.apk ] ; then
    echo "aapt failed.", $ApkFile
    exit $rc
fi
$aapt d xmltree new.apk AndroidManifest.xml| sed 's/(line=[0-9]*)//' > new.out
diff -b orig.out new.out || echo "Failed:" $ApkFile


