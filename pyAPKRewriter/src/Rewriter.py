'''
Created on Aug 19, 2011

@author: rubin
'''
import re
import sys, os
from DexType import *
from SmaliParser import dalvikMethodDeclaration


Java_Prototype_ClassName = "APIHook"
def genInvokeCommand(kind, arglist, method):
    if method.hasImplicitThisPtr:
        assert len(arglist) == method.getArgumentsRegCount() + 1
    else:
        assert len(arglist) == method.getArgumentsRegCount()
    #non-range method is sufficient
    if len(arglist) <= 4 and all(map(lambda x: x < 16, arglist)):
        return "invoke-%s {%s}, %s" % (kind, ",".join(["v%d" % x for x in arglist]), method.toDalvikAssembly(True))
    else:
        #Make sure the arglist are consecutive
        for i in range(len(arglist) - 1):
            assert arglist[i] + 1 == arglist[i+1]
        return "invoke-%s/range {v%d .. v%d}, %s" % (kind, arglist[0], arglist[-1], method.toDalvikAssembly(True))
            
def FilterNonstandardObject(Param):
    return Param
#    javaName = Param.toJavaName()
#    ## TODO Array of objects
#    if isinstance(Param, DexTypeObject) and \
#        ((javaName[:8] != "android.") and (javaName[:5] != "java.")):
#        return DexType.fromDalvikAssembly("Ljava/lang/Object;")[0]
#    else:
#        return Param
        
def patchInvokeKind(origLine, curClassName, newCode, javaCode):
    #invoke-virtual {v1, v2, v3}, Lcom/android/demo/notepad3/NotesDbAdapter;->fetchNote(J)Landroid/database/Cursor;
    r = re.match(r"([^\s]*)\s*\{(.*)\},\s*([^\s]*)", origLine.lstrip())
    opcode = r.group(1)
    InstantiatedArgs = r.group(2)
    #methodSignature = r.group(3)
    
    if origLine.find("invoke-super/range") != -1:
        pass
    isInvokeRange = opcode[-6:] =='/range'
    opcode_kind = opcode[opcode.find('-') + 1:]
    if isInvokeRange:
        opcode_kind = opcode_kind[:-6]
        
    method = dalvikMethodDeclaration()
    method.parseInvokekind(r.group(0))
    
    methodUniqueName = method.getUniqueMethodName()
    OverloadedFuncUID = "".join([x.toDalvikName() for x in method.ArgumentList]).__hash__()
    
    TrampolineMethod = method.clone()
    # Make the implicit this ptr to explicit
    TrampolineMethod.Class = DexType.fromDalvikAssembly(curClassName)[0]
    if method.hasImplicitThisPtr:
        TrampolineMethod.hasImplicitThisPtr = False
        TrampolineMethod.ArgumentList.insert(0, method.Class)
    # Trampoline function is implemented as static method in curClass
    TrampolineMethod.MethodName = "Trampoline_" + methodUniqueName
    
    # Before Hook function takes all input arguments of the original function, returns a self-defined object
    BeforeHookMethod = TrampolineMethod.clone()
    BeforeHookMethod.Class = DexTypeObject(Java_Prototype_ClassName)
    BeforeHookMethod.MethodName = "BeforeHook_" + methodUniqueName
    BeforeHookMethod.ReturnType = DexType.fromDalvikAssembly("Ljava/lang/Object;")[0]
    BeforeHookMethod.ArgumentList = map(FilterNonstandardObject, BeforeHookMethod.ArgumentList)
    # After Hook function takes the returned object of Before Hook,  the return value of API plus 
    # all input argument. Be careful with the ordering. as API may return a pair and we may need 
    # invoke/range which has constraints on argument registers
    AfterHookMethod = TrampolineMethod.clone()
    AfterHookMethod.Class = DexTypeObject(Java_Prototype_ClassName)
    AfterHookMethod.MethodName = "AfterHook_" + methodUniqueName
    if not isinstance(method.ReturnType, DexTypeVoid):
        AfterHookMethod.ArgumentList.insert(0, FilterNonstandardObject(method.ReturnType))
    AfterHookMethod.ArgumentList.insert(0, BeforeHookMethod.ReturnType)
    AfterHookMethod.ArgumentList = map(FilterNonstandardObject, AfterHookMethod.ArgumentList)
    AfterHookMethod.ReturnType = DexType.fromDalvikAssembly("V")[0]
        
    if not methodUniqueName in javaCode:
        javaCode[methodUniqueName] = {}
    javaCode[methodUniqueName][OverloadedFuncUID] = []
    javaCode[methodUniqueName][OverloadedFuncUID].append("public static " + BeforeHookMethod.toJavaSource())
    javaCode[methodUniqueName][OverloadedFuncUID].append("{")
    javaCode[methodUniqueName][OverloadedFuncUID].append("    // {UID %.8X} TODO" % OverloadedFuncUID) #
    javaCode[methodUniqueName][OverloadedFuncUID].append('    android.util.Log.v("APIHook","Before: %s %s");' % (method.Class.toJavaName(), methodUniqueName))
    javaCode[methodUniqueName][OverloadedFuncUID].append("    return null;")
    javaCode[methodUniqueName][OverloadedFuncUID].append("}")
    javaCode[methodUniqueName][OverloadedFuncUID].append("public static " + AfterHookMethod.toJavaSource())
    javaCode[methodUniqueName][OverloadedFuncUID].append("{")
    javaCode[methodUniqueName][OverloadedFuncUID].append("    // TODO")
    javaCode[methodUniqueName][OverloadedFuncUID].append('    android.util.Log.v("APIHook","After: %s %s");' % (method.Class.toJavaName(), methodUniqueName))
    javaCode[methodUniqueName][OverloadedFuncUID].append("}")
    javaCode[methodUniqueName][OverloadedFuncUID].append("")
    
    
    TempObj_RegID = 3 - method.ReturnType.regsize()
    if not methodUniqueName in newCode:
        newCode[methodUniqueName] = {}
    newCode[methodUniqueName][OverloadedFuncUID] = []
    newCode[methodUniqueName][OverloadedFuncUID].append(".method private static %s" % TrampolineMethod.toDalvikAssembly(False))
    newCode[methodUniqueName][OverloadedFuncUID].append("    .locals 4")
    newCode[methodUniqueName][OverloadedFuncUID].append("    .prologue")
    newCode[methodUniqueName][OverloadedFuncUID].append("     " + genInvokeCommand("static", range(4, 4 + BeforeHookMethod.getArgumentsRegCount()), BeforeHookMethod))
    newCode[methodUniqueName][OverloadedFuncUID].append("     move-result-object v%d" % TempObj_RegID)
    #Debugging Information: newCode[methodUniqueName].append(".local v0, beforeHook_r:Ljava/lang/Object;")
    newCode[methodUniqueName][OverloadedFuncUID].append("     " + genInvokeCommand(opcode_kind, range(4, 4 + TrampolineMethod.getArgumentsRegCount()), method))
    
    if not isinstance(method.ReturnType, DexTypeVoid):
        newCode[methodUniqueName][OverloadedFuncUID].append("    move-result%s v%d" % (method.ReturnType.getResultType(), TempObj_RegID + 1) )
        
    newCode[methodUniqueName][OverloadedFuncUID].append("     " + genInvokeCommand("static", range(TempObj_RegID, TempObj_RegID + AfterHookMethod.getArgumentsRegCount()), AfterHookMethod))
    
    if not isinstance(method.ReturnType, DexTypeVoid):
        newCode[methodUniqueName][OverloadedFuncUID].append("    return%s v%d" % (method.ReturnType.getResultType(), TempObj_RegID + 1) )
    else:
        newCode[methodUniqueName][OverloadedFuncUID].append("    return-void")
    newCode[methodUniqueName][OverloadedFuncUID].append(".end method")
    newCode[methodUniqueName][OverloadedFuncUID].append("")

    if isInvokeRange:
        range_suffix = "/range"
    else:
        range_suffix = ""
    newInst = "invoke-static%s {%s}, %s" % ( range_suffix,
                                             InstantiatedArgs, 
                                             TrampolineMethod.toDalvikAssembly(True)
                                            )
    return '#' + origLine + '\n' + newInst
    
blacklist = set(["Landroid/support/v4", "Lorg/apache/http/entity/mime", "Landroid/widget/TiVideoView4", 
                 "Lorg/json/Test/1Obj", "Lorg/xmlpull/mxp1", "Lorg/json/simple/", "Lorg/json/me/", 
                 "Lorg/apache/http/nio", "Ldalvik/system/VMRuntime"])

whitelist = set(["Landroid/", "Ldalvik/", "Ljava/", "Ljavax/crypto", "Ljavax/microedition/khronos", "Ljavax/net", 
                 "Ljavax/security", "Ljavax/sql", "Ljavax/xml", "Lorg/apache/http", "Lorg/xml/", "Lorg/xmlpull/", "Lorg/json"])
def isFrameworkCall(line):
    for s in blacklist:
        if line.find(', ' + s) != -1:
            return False
    for s in whitelist:
        if line.find(', ' + s) != -1:
            return True
    return False


def rewrite(fname, foutname, funcname, javaCode):
    fout = open(foutname, "wt")
    
    curClass = None
    extraCode = {}
    
    for line in open(fname, "rt"):
        line = line[:-1]
        inst = line.lstrip().split(" ")
        opcode = inst[0]
        if opcode == ".class":
            curClass = inst[-1]
            for _, extraFuncs in extraCode.iteritems():
                for _, extraLine in extraFuncs.iteritems():
                    fout.write('\n'.join(extraLine))
                    fout.write('\n')
            extraCode = {}
        
        if opcode[:6] == "invoke" and line.find(funcname) != -1 and line.find("<") == -1 and isFrameworkCall(line):
            line = patchInvokeKind(line, curClass, extraCode, javaCode)
        
        fout.write(line + '\n')
        
    for _, extraFuncs in extraCode.iteritems():
        for _, extraLine in extraFuncs.iteritems():
            fout.write('\n'.join(extraLine))
    fout.close()

class Rewriter(object):
    def __init__(self):
        pass
    
    def reset(self, target_fun):
        self.javaCode = {}
        self.target_fun = target_fun
    
    def dofile(self, filepath):
        if os.path.splitext(filepath)[1] != '.smali':
            return
        ftmpname = filepath + ".tmp"
        sys.stderr.write(filepath + "\n")
        rewrite(filepath, ftmpname , self.target_fun, self.javaCode)
        #os.rename(fname, fname + '.bak')
        os.rename(ftmpname, filepath)
                
    def postprocess(self):
        print "public class", Java_Prototype_ClassName
        print "{"
        for _, javaFuncs in self.javaCode.iteritems():
            for _, javaLine in javaFuncs.iteritems():
                for l in javaLine:
                    print "\t", l
        print "}"
