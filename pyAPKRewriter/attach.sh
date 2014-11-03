#!/bin/bash
DIR="$( cd "$( dirname "$0" )" && pwd )"
root_dir=$DIR"/../dependencies"
## env variable AURASIUM_KEYSTORE, used by PyResign script
if [ -z ${AURASIUM_DEBUG_PAYLOAD} ]; then
	echo "Repackage for release."
	AurasiumData=${root_dir}"/aurasium-release.zip"
else
	echo "Repackage for debug."
	AurasiumData=${root_dir}"/aurasium.zip"
fi
PyRewrite=$DIR"/src/RewriterMain.py"
PyResign=$DIR"/src/Signer.py"

apktool="java -jar ${root_dir}/apktool/apktool.jar"
apktool_res=${root_dir}"/apktool/1.apk"
aapt_dir=${root_dir}"/aapt/"
aapt=${aapt_dir}"/aapt"
jarsigner=jarsigner ## Requires JDK to be installed
apk_keystore=${root_dir}"/debug.keystore"

CurDir=`pwd`
WorkDir=`mktemp -d` ##${CurDir}"/tmp/"
ApkFile=$1
ApkBaseName=`basename $ApkFile`
if [ -z $2 ]; then
	NewApkFile=${CurDir}"/"${ApkBaseName%.apk}.aurasium_apk
else
	## Must have an extension, see below.
	NewApkFile=$2
fi

rm -rf ${WorkDir} 2>&1  >/dev/null

echo "Verifying original signature.."
$PyResign -verify $ApkFile
rc=$?
if [ $rc != 0 ] ; then
    echo "{ERR:BAD_SIG}Bad signature.Exiting.."
    exit $rc
fi

echo "Unpacking.."
PATH=${aapt_dir}:$PATH $apktool d -r $ApkFile $WorkDir
rc=$?
if [ $rc != 0 ] ; then
    echo "{ERR:APKTOOL}apktool failed."
    rm -rf ${WorkDir}
    exit $rc
fi

echo "Integrating smali & native libs.."
unzip ${AurasiumData} -d ${WorkDir} 2>&1 >/dev/null
if [ `ls -l ${WorkDir}"/lib/armeabi-v7a/" | wc -l` == 2 ] #Remove armeabi-v7a if the application originally does not use it.
  then
	rm -rf ${WorkDir}"/lib/armeabi-v7a/"
  fi
  
echo "Attaching..."
python ${PyRewrite} attach ${WorkDir}
rc=$?
if [ $rc != 0 ] ; then
    echo "{ERR:PYATTACH}Attaching failed."
    rm -rf ${WorkDir}
    exit $rc
fi
cd $CurDir
echo "Repacking.."
$aapt p -F $WorkDir"/am.apk" -I ${apktool_res} -M $WorkDir"/AndroidManifest.xml"
rc=$?
if [ $rc != 0 ] ; then
    echo "{ERR:AAPT}aapt failed."
    rm -rf ${WorkDir}
    exit $rc
fi
mv $WorkDir"/AndroidManifest.xml" $WorkDir"/AndroidManifest.bak"
unzip -p $WorkDir"/am.apk" AndroidManifest.xml > $WorkDir"/AndroidManifest.xml"
rm -f ${NewApkFile}
PATH=${aapt_dir}:$PATH $apktool b $WorkDir ${NewApkFile}
rc=$?
if [ $rc != 0 ] ; then
    echo "{ERR:APKTOOL}apktool failed."
    #rm -rf ${WorkDir}
    exit $rc
fi

echo "Adding extra files.."
## Check for additional non-standard files in the original apk file
cd $CurDir

## What a hack to pare zip file content and deal with white spaces in filenames at the same time
unzip -l -qq ${ApkFile} | sed 's/^ *[^ ]*//' |  sed 's/^ *[^ ]*//' |  sed 's/^ *[^ ]* *//' | while read f
  do
    if [ ! -e ${WorkDir}"/""$f" ] ; then
	    if [[ $f != META-INF* ]] ; then #ignore META_INF directory
	    if [[ $f != classes.dex ]] ; then #ignore classes.dex
			echo "$f"
			cd $CurDir
			unzip ${ApkFile} "$f" -d $WorkDir
			cd ${WorkDir}
			zip ${NewApkFile} "$f"
		fi
		fi
	fi
  done
cd $CurDir
## Not allowed to compress >1MB resources.arsc 
unzip -p ${NewApkFile} resources.arsc > $WorkDir"/resources.arsc"
zip -0 -j ${NewApkFile} $WorkDir"/resources.arsc" ## This will behave incorrectly if NewApkFile does not have an extension.
echo "Signing.."
#${jarsigner}  -keystore ${apk_keystore} -storepass android -keypass android  $NewApkFile androiddebugkey
$PyResign -resign $NewApkFile $ApkFile
rc=$?
if [ $rc != 0 ] ; then
    echo "{ERR:RESIGN}Signing failed."
    rm -rf ${WorkDir}
    exit $rc
fi
rm -rf ${WorkDir} 2>&1  >/dev/null
##unzip -p ${NewApkFile}  classes.dex  > classes.dex


