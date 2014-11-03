'''
Created on Aug 19, 2011

@author: rubin
'''
import sys
from SmaliParser import *
from DexType import DexTypeArray
from cPickle import load, dump
class Analyzer(object):
    def __init__(self):
        pass
    
    def reset(self):
        # A dict that maps class names to dalvikClass instances
        self.Classes = {}
        # A dict that maps interfaces to class names that implements it
        self.InterfaceDict = {}
        # A set of all methods(definitions + referenced declaration) in classes
        self.Methods = set()
        # Backwards cross references of call graph
        self.Xrefs = {}
    def dofile(self, filepath):
        if not filepath.endswith('.smali'):
            return
        sys.stderr.write(filepath + "\n")
        content = open(filepath, "rt").readlines()
        i = 0
        while i < len(content):
            newclass = DalvikClass()
            i = newclass.parse(content, i) + 1
            if newclass.Name in self.Classes:
                print "Found redefinition of class", newclass.Name
                raise Exception("ClassRedefinition")
            #print "GV: S%.8X -> S%.8X;" %(newclass.Name.__hash__() & 0xFFFFFFFF, newclass.Superclass.__hash__() & 0xFFFFFFFF)
            self.Classes[newclass.Name] = newclass
            
    # Replace superclassName with SuperclassPtr, build SubclassList
    # Do the same with interface hierarchy
    # Also, collect all method declarations and normalize instruction targets
    def normalize(self):
        for c in self.Classes.itervalues():
            if c.SuperclassName in self.Classes:
                c.SuperclassPtr = self.Classes[c.SuperclassName]
            else:
                c.SuperclassPtr = None
            c.ImplementedInterfaceList = []
            c.SuperinterfaceList = []
            c.SubinterfaceList = []
                
        for c in self.Classes.itervalues():
            # Compute SubclassList 
            s = c.getSuperclass()
            if s:
                s.SubclassList.append(c)
            if c.isInterface:
                # Compute SuperinterfaceList and SubinterfaceList
                for ifname in c.Implements:
                    ifc = self.Classes[ifname]
                    assert ifc.isInterface
                    # c implements ifc, hence ifc is a superclass of c
                    c.SuperinterfaceList.append(ifc)
                    ifc.SubinterfaceList.append(c)
            else:
                # Compute ImplementedInterfaceList for interfaces
                for ifname in c.Implements:
                    if ifname in self.Classes:
                        ifc = self.Classes[ifname]
                        assert ifc.isInterface
                        c.ImplementedInterfaceList.append(ifc)
                    else:
                        print "Cannot locate interface", ifname
            
        # Find all methods: first adding all definitions, they are guaranteed to 
        # be duplicate-free.
        method_map = {}
        for c in self.Classes.itervalues():
            for m in c.Methods:
                method_map[m.Declaration] = m.Declaration
        # Now add all referenced declaration from code, normalize the invokeKind opcode at
        # the same time
        for c in self.Classes.itervalues():
            for m in c.Methods:
                for inst in m.Instructions:
                    if isinstance(inst, SmaliOpInvokeKind):
                        target = inst.TargetFunction
                        if target in method_map:
                            inst.TargetFunction = method_map[target]
                        else:
                            method_map[target] = target
        self.Methods = set(method_map.iterkeys())
    
    def printClsTree(self, root, ident):
        if root.isInterface:
            print '*', " " * ident, root.Name
            for sub in root.SubinterfaceList:
                self.printClsTree(sub, ident + 4)          
        else:      
            print ' ', " " * ident, root.Name
            for sub in root.SubclassList:
                self.printClsTree(sub, ident + 4)          
    def postprocess(self):
#        if self.Classes:
#            dump(self.Classes, open("af.dat", "wb"))
#        else:
#            self.Classes = load(open("af.dat", "rb"))

        self.normalize()
        self.genInterfaceDict()
        self.buildCompleteCallGraph()
        ##self.testForwardCallGraph()
        self.testBackwardsCallGraph()
        
#        self.printMethodDefinitions()
        print "-" * 64
#        allCallSites = self.generateCallSites('Lorg/apache/harmony/luni/platform/INetworkSystem;', 'socket')
#        allCallSites = self.generateCallSites('Ljava/net/SocketImpl;', 'create')
#        print "-" * 64
#        for caller in allCallSites:
#            print caller
        
####self.printClsTree(self.Classes['Ljava/lang/object;'], 0)
    def printMethodDefinitions(self):
        for cls in self.Classes.itervalues():
            print cls.Name
            for m in cls.Methods:
                print " " * 4, m.Declaration.toDalvikAssembly(False)
                for inst in m.Instructions:
                    print " " * 8, str(inst)

    def lookupMethodClass(self, methodDeclaration):
        if not methodDeclaration.ClassPtr:
            clsname =  methodDeclaration.Class.toDalvikName()
            if clsname in self.Classes: 
                cls = self.Classes[methodDeclaration.Class.toDalvikName()]
            else:
                cls = None
                print "Cannot locate class", clsname
            methodDeclaration.ClassPtr = cls
        return methodDeclaration.ClassPtr

    def lookupMethod(self, methodDeclaration):
        if not methodDeclaration.Definition:
            cls = self.lookupMethodClass(methodDeclaration)
            definition = cls.containsMethod(methodDeclaration)
            assert definition
            methodDeclaration.Definition = definition
        return (methodDeclaration.ClassPtr, methodDeclaration.Definition)


    def getCalledMethodsFromInstructions(self, instlist):
        result = []
        for inst in instlist:
            if isinstance(inst, SmaliOpInvokeKind):
                result.append(('Invoke', inst.TargetFunction))
        return result
    def iterateSubclasses(self, root, iter, init_acc):
        for child in root.SubclassList:
            init_acc = iter(child, self.iterateSubclasses(child, iter, init_acc))
        return init_acc
    # Returns a list of method declarations that may get called if the input method is invoked.
    def findMethodCallGraphDirectChildren(self, methodDeclaration):
        if isinstance(methodDeclaration.Class, DexTypeArray):
            return []
        mcls = self.lookupMethodClass(methodDeclaration)
        if not mcls:
            return []
        
        #Case split: either its a class method or an interface method
        if not mcls.isInterface:
            mdef = mcls.containsMethod(methodDeclaration)
            if mdef:
                if not mdef.isAbstract(): 
                    # We have a concrete class with a concrete method
                    return self.getCalledMethodsFromInstructions(mdef.Instructions)
                else: # This is an abstract method, it could land in any of the instantiated subclass methods 
                    def findSubclasses(node, result):
                        m = node.containsMethod(methodDeclaration)
                        if m:
                            if not m.isAbstract():
                                print m.Declaration.toDalvikAssembly(True)
                                assert not m.isAbstract()
                            return result + [('AbstCls', m.Declaration)]
                        else:
                            return result
                    return self.iterateSubclasses(mcls, findSubclasses, [])
            else: 
                #method is not defined in mcls, it must be inherited from its superclass, OR 
                #in some interface (in which case this mcls itself must be abstract 
                root = mcls.getSuperclass()
                while root and (not root.containsMethod(methodDeclaration)):
                    root = root.getSuperclass()
                if root:
                    m = root.containsMethod(methodDeclaration)
                    ##assert m # and (not m.isAbstract())
                    return [('Inherit', m.Declaration)]
                else:
                    queue = list(mcls.ImplementedInterfaceList)
                    while queue:
                        ifc = queue.pop()
                        m = ifc.containsMethod(methodDeclaration)
                        if m:
                            return [('InhrIfc', m.Declaration)]
                        queue.extend(ifc.SuperinterfaceList)
                    sys.stderr.write('Cannot find defining class/interface: ' + str(methodDeclaration))
                    return []
        else: #This is an interface call.
            # First need to find the interface where this method is actually declared.
            queue = [mcls]
            baseIfc = None
            while queue:
                ifc = queue.pop(0)
                if ifc.containsMethod(methodDeclaration):
                    baseIfc = ifc
                    break
                queue.extend(ifc.SuperinterfaceList)
            if not baseIfc:
                print methodDeclaration.toDalvikAssembly(True)
                assert baseIfc
            # Then need to find all classes that implements this interface/its subinterfaces and instantiates 
            # the required method
            result = []
            if ifc in self.InterfaceDict:
                for ifc_cls in self.InterfaceDict[ifc]:
                    m = ifc_cls.containsMethod(methodDeclaration)
                    if m:
    #                Vector extends (AbstractList implements (Interface)List)
    #                    if m.isAbstract():
    #                        print ifc_cls.Name
    #                        print m.Declaration.toDalvikAssembly(True)
    #                        assert not m.isAbstract()
                        result.append(('ImplIfc', m.Declaration))
                #assert result X509KeyManager has not concrete implementations
            return result
        
    def genInterfaceDict(self):
        self.InterfaceDict = {}
        for cls in self.Classes.itervalues():
            if cls.isInterface:
                continue
            for baseifc in cls.ImplementedInterfaceList:
                # cls implements ifc, so add cls to InterfaceDict[ifc'], where ifc' is a super of  ifc
                queue = [baseifc]
                while queue:
                    ifc = queue.pop()
                    if ifc in self.InterfaceDict:
                        self.InterfaceDict[ifc].append(cls)
                    else:
                        self.InterfaceDict[ifc] = [cls]
                    queue.extend(ifc.SuperinterfaceList)
    
    def buildCompleteCallGraph(self):
        # CG (CallGraph) is a dict from MethodDeclaration to List of MethodDeclaration,
        # representing an adjacent list of the code's call graph 
        CG = {}
        for m in self.Methods:
            CG[m] = self.findMethodCallGraphDirectChildren(m)
            
        # Xrefs is just an reversed version of GC
        self.Xrefs= {}
        for mtd, targets in CG.iteritems():
            for (comment, target) in targets:
                if target in self.Xrefs:
                    self.Xrefs[target].append((comment, mtd))
                else:
                    self.Xrefs[target] = [(comment, mtd)]
         
    def testForwardCallGraph(self):
        fgraph = open("graph.vz", "wt")
        fgraph.write("digraph test {\n")
        graphnodes =  set()
        
        callSites = set()
        target_list = [dalvikMethodDeclaration().parseInvokekind("invoke-virtual {v6, v8, v10}, Ljava/net/Socket;->connect(Ljava/net/SocketAddress;I)V")]
        # Depth first search for all possible call sites
        while target_list:
            target_method = target_list.pop(0)
            methodSignature = target_method.toDalvikAssembly(True)
            print "Processing", methodSignature
            if methodSignature in callSites:
                continue
            callSites.add(methodSignature)
            
            for cmt, callee in self.findMethodCallGraphDirectChildren(target_method):
                print "    Adding", callee.toDalvikAssembly(True)
                target_list.append(callee)
                graphnodes.add(callee.toDalvikAssembly(True))
                fgraph.write('S%.8X -> S%.8X [label="%s"]\n' % (callee.toDalvikAssembly(True).__hash__() &0xFFFFFFFF, target_method.toDalvikAssembly(True).__hash__() &0xFFFFFFFF, cmt))
                
        for node in graphnodes:
            fgraph.write('S%.8X [label="%s"]\n' % (node.__hash__() &0xFFFFFFFF, node)) 
        fgraph.write("}\n")
        fgraph.close()
    
    def testBackwardsCallGraph(self):
        fgraph = open("graph.vz", "wt")
        fgraph.write("digraph test {\n")
        graphnodes =  set()
        
        callSites = set()
#        target_list = [dalvikMethodDeclaration().parseInvokekind("invoke-interface       {v1, v2, p1, p2, v3}, Lorg/apache/harmony/luni/platform/INetworkSystem;->connect(Ljava/io/FileDescriptor;Ljava/net/InetAddress;II)V")]
        target_list = [dalvikMethodDeclaration().parseInvokekind("invoke-interface/range {v0 .. v5}, Lcom/android/internal/telephony/ISms$Stub$Proxy;->sendText(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Landroid/app/PendingIntent;Landroid/app/PendingIntent;)V")]
        # Depth first search for all possible call sites
        while target_list:
            target_method = target_list.pop(0)
            methodSignature = target_method.toDalvikAssembly(True)
            print "Processing", methodSignature
            if methodSignature in callSites:
                continue
            if methodSignature == 'Ljava/lang/Runnable;->run()V':
                continue
            graphnodes.add(methodSignature)
            callSites.add(methodSignature)
            
            if target_method in self.Xrefs:
                for cmt, caller in self.Xrefs[target_method]:
                    print "    Adding", caller.toDalvikAssembly(True), cmt
                    target_list.append(caller)
                    fgraph.write('S%.8X -> S%.8X [label="%s"]\n' % (caller.toDalvikAssembly(True).__hash__() &0xFFFFFFFF, target_method.toDalvikAssembly(True).__hash__() &0xFFFFFFFF, cmt))
                
        for node in graphnodes:
            fgraph.write('S%.8X [label="%s"]\n' % (node.__hash__() &0xFFFFFFFF, node)) 
        fgraph.write("}\n")
        fgraph.close()
    
    def findCallerMethodsAmongClasses(self, classes,  inst_predicate):
        result = []
        for cls in classes:
            for m in cls.Methods:
                skip = False
                for inst in m.Instructions:
                    if inst_predicate(cls, inst):
                        result.append((inst.opcode + ' ' + inst.TargetFunction.toShorRepr(), m.Declaration))
                        skip = True
                        break
                if skip:
                    continue
        return result
        
    def findStaticMethodCallers(self, mcls, mdef, msig):
        return self.findCallerMethodsAmongClasses(self.Classes.itervalues(), 
            lambda cls, inst :
                isinstance(inst, SmaliOpInvokeStatic) and inst.TargetFunction.toDalvikAssembly(True) == msig
        )
        
    def findClassMethodCallers(self, mcls, mdef, msig):
        msig_nocls = mdef.Declaration.toDalvikAssembly(False)
        # The method is a direct method
        result0 = self. findCallerMethodsAmongClasses(self.Classes.itervalues(), 
            lambda cls, inst :
                isinstance(inst, SmaliOpInvokeDirect) and inst.TargetFunction.toDalvikAssembly(True) == msig
        )
        # Non-direct method.
        result1 = self. findCallerMethodsAmongClasses(self.Classes.itervalues(), 
            lambda cls, inst :
                isinstance(inst, SmaliOpInvokeSuper) and inst.TargetFunction.toDalvikAssembly(True) == msig
        )
        
        # Find all possible classes whose instances may contain vtable pointers to the target method,
        PossibleClasses = set() 
        # First travesal along parent pointer until we find the defining class of this particular method
        # Either this method is an instantiation of an abstract method of some parent abstract class,
        # Or this method is actually defined at its parent class/itself
        if mcls.containsMethodSignature(msig_nocls): # We define this method, check for abstract parent classes
            PossibleClasses.add(mcls)
            root = mcls.getSuperclass()
            chain = []
            while root:
                chain.insert(0, root)
                root = root.getSuperclass()
            while chain:
                m = chain[0].containsMethodSignature(msig_nocls)
                if m and m.isAbstract():
                    for c in chain:
                        if c.isAbstract():
                            PossibleClasses.add(c)
                            result1.append(("AbstCls", m.Declaration))
                    break
                chain.pop(0)
            ##Check if it's defined by one of its interfaces 
            for ifc in mcls.ImplementedInterfaceList:
                m = ifc.containsMethodSignature(msig_nocls)
                if m:
                    result1.append(("ImpleInfc", m.Declaration))
        else: # we don't define this method, find the parent defining class
            root = mcls
            while root and not root.containsMethodSignature(msig_nocls):
                PossibleClasses.add(root.Name)
                root = root.getSuperclass()
            assert root
            PossibleClasses.add(root.Name)
            
        # Then adding all children classes of ourselves who does not override the same method (hence
        # their instance can access the target method by direct inheritance)
        queue = list(mcls.SubclassList)
        while queue:
            hd = queue.pop(0)
            if not hd.containsMethodSignature(msig_nocls):
                PossibleClasses.add(hd.Name)
                queue.extend(hd.SubclassList)
        # Now that we have the set of all possible classes, just look for calls to these classes's methods
        result1.extend(self. findCallerMethodsAmongClasses(self.Classes.itervalues(), 
            lambda cls, inst :
                isinstance(inst, SmaliOpInvokeVirtual) and 
                  inst.TargetFunction.Class.toDalvikName() in PossibleClasses and 
                  inst.TargetFunction.toDalvikAssembly(False) == msig_nocls
        ))
        
        # result0 and result1 should be mutually exclusive
        assert not (result0 and result1)
        if result0:
            return result0
        else:
            return result1 
    def findInterfaceMethodCallers(self, mcls, mdef, msig):
        msig_nocls = mdef.Declaration.toDalvikAssembly(False)
        # Find all possible interfaces which can access to the target method,
        RelatedInterfaces = set() 
        root = mcls
        # First travesal along parent pointer until we find the defining interface of this particular method
        while root.SuperinterfaceList and (not root.containsMethodSignature(msig_nocls)):
            RelatedInterfaces.add(root.Name)
            root = root.SuperinterfaceList[0]
            assert len(root.SuperinterfaceList) == 1 #TODO: Currently only support single inheritance of interfaces 
        RelatedInterfaces.add(root.Name)
        # Then adding all children interfaces of ourselves who does not override the same method (hence
        # their instance can access the target method by direct inheritance)
        queue = list(mcls.SubinterfaceList)
        while queue:
            hd = queue.pop(0)
            if not hd.containsMethodSignature(msig_nocls):
                RelatedInterfaces.add(hd.Name)
                queue.extend(hd.SubclassList)
            else:
                raise Exception("Subinterface redefines parent's method.")
        # Now that we have the set of all possible interfaces, just look for calls to these interface's methods
        result = self. findCallerMethodsAmongClasses(self.Classes.itervalues(), 
            lambda cls, inst :
                isinstance(inst, SmaliOpInvokeInterface) and 
                  inst.TargetFunction.Class.toDalvikName() in RelatedInterfaces and 
                  inst.TargetFunction.toDalvikAssembly(False) == msig_nocls
        )
        
        # Also need to find all class methods that implements the target interfaces
        for ifc in RelatedInterfaces:
            for cls in self.Classes.itervalues():
                if ifc in cls.Implements:
                    m = cls.containsMethodSignature(msig_nocls)
                    if m:
                        result.append(("Implements", m.Declaration))
        return result
    
    def findAllCallers(self, mcls, mdef):
        msig = mdef.Declaration.toDalvikAssembly(True)
        #Split cases: static method / class method / interface method
        if mdef.isStatic(): # Static method
            return map(lambda (cm, cd): ('Static:' + cm, cd), self.findStaticMethodCallers(mcls, mdef, msig))
        elif not mcls.isInterface: # class methods
            return map(lambda (cm, cd): ('Classm:' + cm, cd), self.findClassMethodCallers(mcls, mdef, msig))
        else: # interface methods
            return map(lambda (cm, cd): ('Interf:' + cm, cd), self.findInterfaceMethodCallers(mcls, mdef, msig))
        
    def generateCallSites(self, ClassName, MethodFilter):
        fgraph = open("graph.vz", "wt")
        fgraph.write("digraph test {\n")
        graphnodes =  set()
        
        target_list = []
        cls = self.Classes[ClassName]
        for m in cls.Methods:
            if m.Declaration.MethodName == MethodFilter:
                target_list.append(m.Declaration)
                graphnodes.add(m.Declaration.toDalvikAssembly(True))
        callSites = set()
        # Depth first search for all possible call sites
        while target_list:
            target_method = target_list.pop(0)
            methodSignature = target_method.toDalvikAssembly(True)
            print "Processing", methodSignature
            if methodSignature in callSites:
                continue
            callSites.add(methodSignature)
            
            (cls, mdef)  =self.lookupMethod(target_method)
            for cmt, caller in self.findAllCallers(cls, mdef):
                print "    Adding", caller.toDalvikAssembly(True)
                target_list.append(caller)
                graphnodes.add(caller.toDalvikAssembly(True))
                fgraph.write('S%.8X -> S%.8X [label="%s"]\n' % (caller.toDalvikAssembly(True).__hash__() &0xFFFFFFFF, target_method.toDalvikAssembly(True).__hash__() &0xFFFFFFFF, cmt))
                
        for node in graphnodes:
            fgraph.write('S%.8X [label="%s"]\n' % (node.__hash__() &0xFFFFFFFF, node)) 
        fgraph.write("}\n")
        fgraph.close()
        return callSites;