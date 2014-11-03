'''
New implementation of glueing APIHook to existing application:
Change the <application>:<android.name> to APIHook class (with proper subclassing if 
the item already exists in manifest). 

Will take a binary manifest file and produce a textual xml, which is then
handled separately by a modified version of aapt.

Created on Aug 19, 2011

@author: rubin
'''
import os, codecs
from xml.dom import minidom
from apk import AXMLPrinter
APIHook_Class= "com.rx201.apkmon.APIHook"

class Attacher(object):
    """ Patch APK's smali code & Manifest file such that the target APK's Application class is 
    extended on top of com.rx201.apkmon.APIHook """
    def __init__(self):
        pass
    def PatchOriginalAppClass(self, AppClass, clsfile):
        print clsfile
        tmpfile = clsfile + '.tmp'
        os.rename(clsfile, tmpfile)
        fout = open(clsfile, "wt")
        ApiHookClass = 'L' + APIHook_Class.replace('.', '/') + ';'
        curClass = None
        for line in open(tmpfile, "rt"):
            line = line.rstrip()
            inst = line.lstrip().split(" ")
            opcode = inst[0]
            if opcode == ".class":
                curClass = inst[-1][1:-1].replace('/', '.') #Convert from "Lxx;" form back to dotted form
            elif curClass == AppClass:
                if opcode == ".super": # replace superclass
                    line = ".super %s" % ApiHookClass
                    ParentClass  = inst[-1][1:-1].replace('/', '.')
                    if ParentClass != "android.app.Application":
                        #raise Exception("BUG-WARNING: %s does not inherit from androi.app.Application." % self.AppClass)
                        """ This class still inherits some customized base class, need to track them down instead."""
                        os.rename(tmpfile, clsfile)
                        self.PatchOriginalAppClass(ParentClass, self.getClassPath(ParentClass))
                elif opcode[:6] == "invoke": # replace calls to superclass methods
                    line = line.replace('Landroid/app/Application;->', ApiHookClass + "->")
            fout.write(line + '\n')
        fout.close()
        if not curClass:
            print "*"*16, "Attention: This file is not modified."
            
    def ParseManifest(self, ManifestFilename):
        mfile = open(ManifestFilename, "rb")
        xml = minidom.parseString(AXMLPrinter(mfile.read()).getBuff())
        mfile.close()
        
        package = xml.documentElement.getAttribute( "package" )

        ## Replace <application>:<android:name> with APIHook
        app = xml.getElementsByTagName("application")[0]
        self.AppClass = app.getAttribute("android:name")
        
        if self.AppClass:
            ## The APK already implements an Application class, we need to 
            ## do a bit of work later: modify the Application class so that it
            ## extends on top of our Application class. Here we just record the 
            ## full name of the original class.
            if self.AppClass.find('.') == -1:
                self.AppClass = package + '.' + self.AppClass
            elif self.AppClass[0] == '.':
                self.AppClass = package + self.AppClass
            elif self.AppClass[:2] == '/.':     
                self.AppClass = package + self.AppClass[1:]
        else:
            ## Otherwise just insert our Application definition into the manifest
            app.setAttribute("android:name", APIHook_Class)
        
        ## Add the dialog activity
        activity_element = xml.createElement("activity")
        activity_element.setAttribute("android:process", ":APIHookDialog")
        activity_element.setAttribute(  "android:theme", "@android:style/Theme.Translucent.NoTitleBar")
        activity_element.setAttribute(   "android:name", "com.rx201.apkmon.APIHookDialogActivity")
        app.appendChild(activity_element)
        
        ##DEBUG: Add permission to write to SD card.
	if 'AURASIUM_DEBUG_PAYLOAD' in os.environ:
            #  <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"></uses-permission>
            manifest = xml.getElementsByTagName("manifest")[0]
            permission_element = xml.createElement("uses-permission")
            permission_element.setAttribute(   "android:name", "android.permission.WRITE_EXTERNAL_STORAGE")
            manifest.appendChild(permission_element)
        
        mfile = codecs.open(ManifestFilename, "wb", "utf-8")
        xml.writexml(mfile)
        mfile.close();

    def reset(self, ApkDir):
        self.javaCode = {}
        self.ApkDir = ApkDir
        self.ParseManifest(ApkDir + "/AndroidManifest.xml")
        if self.AppClass:
            print "*********Found existing Application class*****************"
            ## Modify the Application classso that it
            ## extends on top of our Application class. 
            self.PatchOriginalAppClass(self.AppClass, self.getClassPath(self.AppClass))
            
    def getClassPath(self, ClassName):
        return self.ApkDir + "/smali/" + ClassName.replace('.', '/') + '.smali'
    
    def dofile(self, filepath):
        pass
    def postprocess(self):
        pass
