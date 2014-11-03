'''
Created on Aug 19, 2011

Obsolete way of attaching APIHook to existing application (used with insert.sh).
Incompatible with new attach.sh as this one needs binary manifest.xml.
@author: rubin
'''
import re
import sys, os

#http://androguard.blogspot.com/2011/03/androids-binary-xml.html
from apk import AXMLPrinter
from xml.dom import minidom

## Add statements to load APIHook() class to <init> methods in TargetClasses
def glue(fname, foutname, TargetClasses):
    fout = open(foutname, "wt")
    
    curClass = None
    SeenInitMethod = True
    PatchedClasses = set()
    for line in open(fname, "rt"):
        line = line.rstrip()
        inst = line.lstrip().split(" ")
        opcode = inst[0]
        if opcode == ".class":
            if not SeenInitMethod:
                raise Exception("Encounter class %s without a <init> constructor" % curClass)
            curClass = inst[-1][1:-1].replace('/', '.') #Convert from "Lxx;" form back to dotted form
            if not curClass in TargetClasses:
                curClass = None
                SeenInitMethod = True
            else:
                SeenInitMethod = False
            inConstructor = False
        elif curClass:
            if opcode == ".method":
                curMethod = inst[-1]
                inConstructor = (curMethod == "<init>()V")
                SeenInitMethod = SeenInitMethod or inConstructor
            elif inConstructor and line.strip() == "return-void":
                sys.stderr.write("Rewriting " + curClass + "\n")
                line = "new-instance v0, Lcom/rx201/apkmon/APIHook;\n"+\
                       "invoke-direct {v0}, Lcom/rx201/apkmon/APIHook;-><init>()V\n"+\
                        line
                PatchedClasses.add(curClass)
        fout.write(line + '\n')
    fout.close()
    if not PatchedClasses:
        print "*"*16, "Attention: This file is not modified."
    return PatchedClasses
class Gluer(object):
    def __init__(self):
        pass
    
    def ParseManifest(self, ManifestFile):
        ap = AXMLPrinter(open(ManifestFile, "rb").read())
        xml = minidom.parseString(ap.getBuff())
        package = xml.documentElement.getAttribute( "package" )

        classes = set()
        classnames = set()

        for tag_name in ["activity", "service", "receiver", "provider"]:
            for item in xml.getElementsByTagName(tag_name) :
                value = item.getAttribute("android:name")
               
                if len(value) > 0 :
                    if value[0] == "." : # Short form with implicit package name
                        classes.add(package + value)
                        classnames.add(value.split('.')[-1])
                    else : # Fully-qualified name
                        classes.add(value)
                        classnames.add(value.split('.')[-1])
                        # Hack: Some apke does not follow this rule as set in the SDK
                        # As a workround treat them as short form as well
                        classes.add(package + '.' + value)
                        
        return classes, classnames


    def reset(self, ApkDir):
        self.javaCode = {}
        self.TargetClasses, self.TargetClassNames = self.ParseManifest(ApkDir + "/AndroidManifest.xml")
        self.PatchedClasses = set()
    def dofile(self, filepath):
        basename = os.path.basename(filepath)
        filename, fileext = os.path.splitext(basename)
        if fileext != '.smali' or (not filename in self.TargetClassNames):
            return
        ftmpname = filepath + ".tmp"
        sys.stderr.write(filepath + "\n")
        self.PatchedClasses.update(glue(filepath, ftmpname , self.TargetClasses))
        os.rename(ftmpname, filepath)
                
    def postprocess(self):
        if len(self.PatchedClasses) < len(self.TargetClassNames):
            sys.stderr.write("Warning: Only %d/%d potential entry points are patched with API hooks." % (len(self.PatchedClasses), len(self.TargetClassNames)))
