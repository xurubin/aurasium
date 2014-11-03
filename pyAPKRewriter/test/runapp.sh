#!/bin/bash
DELAY=5


APP=$1
adb_arg=$2

DIR="$( cd "$( dirname "$0" )" && pwd )"
PyAXML=$DIR"/../src/axml.py"
awk_packagename=$DIR"/extract-package-name.awk"
awk_launchable=$DIR"/extract-launchable.awk"
awk_extractpid=$DIR"/extract-pid.awk"
adb=adb


unzip -p $APP AndroidManifest.xml > AndroidManifest.axml
python $PyAXML AndroidManifest.axml AndroidManifest.xml
PACKAGE_NAME=`awk -f $awk_packagename AndroidManifest.xml`
LAUNCHABLE=`awk -f $awk_launchable AndroidManifest.xml | sed 2q`

echo "Uninstalling.."
$adb $adb_arg uninstall $PACKAGE_NAME

echo "Installing.."
rc=`$adb $adb_arg install $APP`
echo $rc
if [[ $rc == *Failure* ]] ; then
    echo "Installing failed."
    exit 1
fi

$adb $adb_arg shell am start -n $PACKAGE_NAME/$LAUNCHABLE
rc=$?
if [ $rc != 0 ] ; then
    echo "Starting failed."
    exit $rc
fi

$adb $adb_arg shell monkey -p $PACKAGE_NAME -s 1 500

echo "Uninstalling.."
$adb $adb_arg uninstall $PACKAGE_NAME

