aurasium
========

Practical security policy enforcement for Android apps via bytecode rewriting and in-place reference monitor.

### Code Structure
* ApkMonitor/: The main native and java policy logic, bundled with a demo app.
* pyAPKRewriter/: The APK patching scripts.
* dependencies/: pyAPKRewriter's dependencies
* SecurityManager/: ASM for Aurasium

### Dependency
* Android SDK
* `apt-get install unzip python python-pyasn1` 

### Usage
To repackage an APK file:
    pyAPKRewriter/attach.sh source.apk [desktination.apk]

To build a new version of Aurasium for repackaging:
* First build ApkMonitor/ under Eclispe. 
* Then create an updated Aurasium blob by `make` in ApkMonitor/package.
* Finally copy aurasium.zip to dependencies/
