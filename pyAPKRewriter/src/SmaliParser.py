'''
Created on Aug 18, 2011

@author: rubin
'''

import copy
import re
from DexType import DexType

def lazyfind(str_iter, s):
    for si in str_iter:
        if si == s:
            return True
    return False

class SmaliOpcode(object):
    mnemonic = "!@#$%"
    def __init__(self, s):
        inst = s.split(' ')
        self.opcode = inst[0]
        self.operands = inst[1:]
        
    def __repr__(self):
        return self.opcode + '\t' + ' '.join(self.operands)
    
    @classmethod
    def tryparse(cls, rootcls, s): #recursively try to parse 
        for subcls in rootcls.__subclasses__():
            if subcls.canParse(s):
                return subcls(s)
            else:
                r = cls.tryparse(subcls, s)
                if r:
                    return r
        return None
    @classmethod
    def parse(cls ,s):
        return cls.tryparse(SmaliOpcode, s)
    @classmethod 
    def canParse(cls, s):
        return s.startswith(cls.mnemonic)
#class SmaliOpPseudoDirective(SmaliOpcode):
#    mnemonic = "."
    
class SmaliOpInvokeKind(SmaliOpcode):
    ## SmaliOpInvokeKind is an abstract base class for various invokes, so we assign
    ## it an arbitrary name to stop it from being instantiated by tryparse()
    mnemonic = "!@#$%" 
    def __init__(self, s):
#        if s.endswith('/range'):
#            s = s[:-6]
        SmaliOpcode.__init__(self, s)
        self.CallsiteArguments = self.operands[0]
        self.TargetFunction = dalvikMethodDeclaration().parseInvokekind(s)
     
class SmaliOpInvokeStatic(SmaliOpInvokeKind):
    mnemonic = "invoke-static"
class SmaliOpInvokeVirtual(SmaliOpInvokeKind):
    mnemonic = "invoke-virtual"
class SmaliOpInvokeDirect(SmaliOpInvokeKind):
    mnemonic = "invoke-direct"
class SmaliOpInvokeSuper(SmaliOpInvokeKind):
    mnemonic = "invoke-super"
class SmaliOpInvokeInterface(SmaliOpInvokeKind):
    mnemonic = "invoke-interface"
     
class dalvikMethodDeclaration(object):
# com/dexTest/Function_Hook;->Before_Hook(Ljava/lang/Object;I)Ljava/lang/Object;
    def __init__(self):
        self.ReturnType = None
        self.Class = None
        self.MethodName = "??"
        self.ArgumentList = []
        self.hasImplicitThisPtr = None
        
        # Following additional arguments used for analyzing purposes.
        self.ClassPtr = None
        self.Definition = None
        
    ##Parse a invoke statement
    def parseDefinition(self, dotMethodStatement, ClassName):
        inst = dotMethodStatement.split(" ")
        self.parseSignature(ClassName + '->' + inst[-1])
        self.hasImplicitThisPtr = not lazyfind(inst[1:-2], 'static')
        return self
    def parseInvokekind(self, invokeStatement):
        r = re.match(r"(invoke[^\s]*)\s*\{(.*)\},\s*([^\s]*)", invokeStatement)
        opcode = r.group(1)
        CallSiteArgList = r.group(2)
        signature = r.group(3)
        self.parseSignature(signature)

        ArgsRegCount = self.getArgumentsRegCount()
        if CallSiteArgList == '': #Empty argument
            CallsiteRegCount = 0
        elif CallSiteArgList.find("..") == -1: # Comma-separated argument list
            CallsiteRegCount = len(CallSiteArgList.split(','))
        else: #Its a invoke-kind-range method
            r = re.match(r"\s*[v|p](\d+)\s*\.\.\s*[v|p](\d+)\s*", CallSiteArgList)
            CallsiteRegCount = int(r.group(2)) - int (r.group(1)) + 1
        self.hasImplicitThisPtr = CallsiteRegCount == ArgsRegCount + 1
        assert self.hasImplicitThisPtr or (ArgsRegCount == CallsiteRegCount)
        # hasThisPtr == not (static method) 
        assert self.hasImplicitThisPtr != (opcode.find('static') != -1)
        return self
    ## Initialize instance from dalvik disassembly  
    def parseSignature(self, signature):
        r = re.match(r"(.*)->(.*)\((.*)\)(.*)", signature)
        if not r:
            print signature
        self.Class          = DexType.fromDalvikAssembly(r.group(1), True)[0]
        self.MethodName     = r.group(2)
        self.ArgumentList   = DexType.parseDalvikMethodSignature(r.group(3))
        self.ReturnType     = DexType.fromDalvikAssembly(r.group(4), True)[0]
        
        
        
    ## Generate an identifier as in the Dalvik disassembly code
    def toDalvikAssembly(self, withClassName):
        r = ''
        if withClassName:
            r += self.Class.toDalvikName() + '->'
        r += self.MethodName
        r += '(' + ''.join([x.toDalvikName() for x in self.ArgumentList]) + ')'
        r += self.ReturnType.toDalvikName()
        return r
    
    def toShorRepr(self):
        r = ''
        r += self.Class.toDalvikName().split('/')[-1] + '->'
        r += self.MethodName
        r += '(' + ''.join([x.toDalvikName().split('/')[-1] for x in self.ArgumentList]) + ')'
        r += self.ReturnType.toDalvikName().split('/')[-1]
        return r
    
    def toJavaSource(self):
        r = ''
        r += self.ReturnType.toJavaName()
        r += ' ' + self.MethodName
        r += '(' + ', '.join([x.toJavaName() + " p%d" % i for (i, x) in zip(range(len(self.ArgumentList)), self.ArgumentList)]) + ')'
        return r
    
    def getUniqueMethodName(self):
        #self.MethodName + "%.4X" % "".join(self.ArgumentList).__hash__()
        cls = self.Class.ObjectName.replace('/', '.')
        uid = cls.__hash__() 
        
        ## Deal with internal classes 
        return cls.split('.')[-1].replace('$', '_').replace('[]','_Array') + '__' + self.MethodName + "_%.2X" % (uid & 0xFF)

    def getArgumentsRegCount(self):
        return sum([x.regsize() for x in self.ArgumentList])
    def clone(self):
        return copy.deepcopy(self)
    
    def __repr__(self):
        return self.toJavaSource()
    
    def __hash__(self):
        return self.toDalvikAssembly(True).__hash__()
    def __eq__(self, other):
        return self.toDalvikAssembly(True) == other.toDalvikAssembly(True)
class DalvikMethodDefintion(object):
    def __init__(self):
        self.Declaration = None
        self.Instructions = []
        # public, private, protected, static, final, synchronized, native, final, super, interface, abstract, annotation, enum, bridge/volatile, transient/varargs
        self.AccessSpecifier = []
    
    def parse(self, linelist, ClassName):
        hdr = linelist[0].split(' ')
        assert hdr[0] == '.method'
        self.AccessSpecifier = hdr[1:-1]
        self.Declaration = dalvikMethodDeclaration().parseDefinition(hdr[-1], ClassName)
        self.Instructions = []
        for line in linelist[1:]:
            s = line.strip()
            inst = SmaliOpcode.parse(s)
            if inst:
                self.Instructions.append(inst) 
        return self
    
    def isStatic(self):
        return lazyfind(self.AccessSpecifier, 'static')
    def isAbstract(self):
        return lazyfind(self.AccessSpecifier, 'abstract')
class DalvikClass(object):
    def __init__(self):
        self.clear()
    def __repr__(self):
        return self.Name
    
    def clear(self):
        self.Name = ''
        self.isInterface = False
        self.Modifiers = []
        self.SuperclassName = ''
        self.SubclassList = []
        self.Implements = []
        self.Methods = []

        # These two fields are meaningful for classes
        self.SuperclassPtr = None
        self.ImplementedInterfaceList = []
        
        # These two fields are meaningful for interfaces.
        self.SuperinterfaceList = []
        self.SubinterfaceList = []
        
    def getSuperclass(self):
        return self.SuperclassPtr
    
    def isAbstract(self):
        return lazyfind(self.Modifiers, 'abstract')

    def parse(self, linelist, initial_lineno):
        self.clear()

        Scope_None = 1
        Scope_Class = 2
        Scope_Method = 4
        Scope_Annotation = 5
        Scope = [Scope_None]
        
        method_start = -1
        for i in range(initial_lineno, len(linelist)):
            line = linelist[i].strip()
            if len(line) == 0 or line[0] == '#': #Skip comment line
                continue
            else:
                if Scope[-1] == Scope_None: 
                    if line.startswith('.class'): # Encounter the first class definition
                        Scope.append(Scope_Class)
                        ##Example: .class public interface abstract Lorg/apache/harmony/luni/platform/IFileSystem;
                        inst = line.split(' ')
                        self.Name = inst[-1]
                        self.Modifiers = inst[1:-1]
                        self.isInterface = lazyfind(self.Modifiers, "interface")
                        continue
                elif Scope[-1] == Scope_Class:
                    if line.startswith('.class'): # Encounter an new class definition
                        return i - 1
                    elif line.startswith('.super'):
                        inst = line.split(' ')
                        assert len(inst) == 2
                        self.SuperclassName = inst[-1]
                        continue
                    elif line.startswith('.implements'):
                        inst = line.split(' ')
                        assert len(inst) == 2
                        self.Implements.append(inst[-1])
                        continue
                    elif line.startswith('.method'): #Goes into Method state, recording the current
                        Scope.append(Scope_Method)
                        assert method_start == -1 #Make sure previous method is already ended   
                        method_start = i
                        continue
                    elif line.startswith('.annotation'):
                        Scope.append(Scope_Annotation)
                        continue
                    elif line.startswith('.field') or line.startswith('.end field'):
                        continue
                    elif line.startswith('.source'): ## TODO
                        continue
                    elif line.startswith('.'):
                        print "Unparsed directive:", line
                        continue
                elif Scope[-1] == Scope_Method:
                # .method paired with .end method, handle over the parsing to self.parseMethod()
                        if line.startswith('.end method'):
                            Scope.pop()
                            self.parseMethod(linelist[method_start:i])
                            method_start = -1
                        continue
                elif Scope[-1] == Scope_Annotation:
                        if line.startswith('.end annotation'):
                            Scope.pop()
                        continue    
                else:
                    raise Exception('ParseErrorUnknownScope: ' + str(Scope[-1]))
            
            raise Exception("ParseErrorInvalidLine(Scope %d): %s" % (Scope[-1], line))
            
        assert Scope[-1] == Scope_Class
        return i 
                
    def parseMethod(self, linelist):
        method = DalvikMethodDefintion().parse(linelist, self.Name)
        self.Methods.append(method)
        
    def containsMethodSignature(self, signature):
        for m in self.Methods:
            if m.Declaration.toDalvikAssembly(False) == signature:
                return m
        return None

    def containsMethod(self, method):
        sig = method.toDalvikAssembly(False)
        return self.containsMethodSignature(sig)
    