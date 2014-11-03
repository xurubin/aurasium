'''
Created on Aug 8, 2011

@author: rubin
'''
class DexType(object):
    def __init__(self):
        self.DexRepr = ""
        self.JavaRepr = ""
    def regsize(self):
        return 1
    
    def toJavaName(self):
        raise Exception("Not implemented")
    def toDalvikName(self):
        raise Exception("Not implemented")
    @classmethod
    ## return a pair of type object and the consumed string characters. 
    def fromDalvikAssembly(cls, s, consumeAll = False):
        if s[0] in PrimitiveTypeMap:
            assert (not consumeAll) or len(s) == 1 
            return (PrimitiveTypeMap[s[0]](), 1)
        elif s[0] == 'L':
            j = s.find(';', 1)
            if j == -1:
                raise Exception("Object type parse error")
            assert (not consumeAll) or j == len(s) - 1 
            return (DexTypeObject(s[1:j]), j + 1)
        else:
            assert s[0] == '['
            (SubType, Len) = DexType.fromDalvikAssembly(s[1:])
            assert (not consumeAll) or len(s) == Len + 1 
            return (DexTypeArray(SubType), Len + 1)
    
    @classmethod
    def parseDalvikMethodSignature(cls, s):
        i = 0
        r = []
        while i < len(s):
            (Type, Len) = DexType.fromDalvikAssembly(s[i:])
            r.append(Type)
            i += Len
        return r

    def getResultType(self):
        if isinstance(self, DexTypePrimitive):
            if self.regsize() == 1:
                return ""       # move-result
            else:
                return "-wide"   # move-result-wide
        else:
            return "-object"     # move-result-object 
class DexTypePrimitive(DexType):
    def __init__(self, DexName, JavaName):
        DexType.__init__(self)
        self.DexRepr = DexName
        self.JavaRepr = JavaName
    def toJavaName(self):
        return self.JavaRepr
    def toDalvikName(self):
        return self.DexRepr

class DexTypeBoolean(DexTypePrimitive):
    def __init__(self):
        DexTypePrimitive.__init__(self, 'Z', 'boolean')
class DexTypeChar(DexTypePrimitive):
    def __init__(self):
        DexTypePrimitive.__init__(self, 'C', 'char')
class DexTypeFloat(DexTypePrimitive):
    def __init__(self):
        DexTypePrimitive.__init__(self, 'F', 'float')
class DexTypeByte(DexTypePrimitive):
    def __init__(self):
        DexTypePrimitive.__init__(self, 'B', 'byte')
class DexTypeShort(DexTypePrimitive):
    def __init__(self):
        DexTypePrimitive.__init__(self, 'S', 'short')
class DexTypeInt(DexTypePrimitive):
    def __init__(self):
        DexTypePrimitive.__init__(self, 'I', 'int')
class DexTypeVoid(DexTypePrimitive):
    def __init__(self):
        DexTypePrimitive.__init__(self, 'V', 'void')
    def regsize(self):
        return 0
class DexTypeDouble(DexTypePrimitive):
    def __init__(self):
        DexTypePrimitive.__init__(self, 'D', 'double')
    def regsize(self):
        return 2
class DexTypeLong(DexTypePrimitive):
    def __init__(self):
        DexTypePrimitive.__init__(self, 'J', 'long')
    def regsize(self):
        return 2
class DexTypeArray(DexType):
    def __init__(self, element):
        assert isinstance(element, DexType)
        self.ElementType = element
    def toJavaName(self):
        return self.ElementType.toJavaName() + '[]'
    def toDalvikName(self):
        return '[' + self.ElementType.toDalvikName()
        
class DexTypeObject(DexType):
    def __init__(self, objname):
        # ObjectName should look like com/android/.../Activity.
        if objname[0] == 'L' and objname[-1] == ';':
            self.ObjectName = objname[1:-1]
        else:
            self.ObjectName = objname
    def toJavaName(self):
        #android.view.ViewGroup$LayoutParams
        if self.ObjectName.startswith('java/lang/'):
            return self.ObjectName[10:].replace('/', '.').replace('$', '.')
        else:
            return self.ObjectName.replace('/', '.').replace('$', '.')
    def toDalvikName(self):
        return 'L' + self.ObjectName + ';'


PrimitiveTypeMap = {'Z' : DexTypeBoolean,
                    'C' : DexTypeChar,
                    'F' : DexTypeFloat,
                    'B' : DexTypeByte,
                    'S' : DexTypeShort,
                    'I' : DexTypeInt,
                    'V' : DexTypeVoid,
                    'D' : DexTypeDouble,
                    'J' : DexTypeLong   }
