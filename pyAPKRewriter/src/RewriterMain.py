'''
Created on Aug 4, 2011

@author: rubin
'''
import sys
import os
from Rewriter import Rewriter
from Analyzer import Analyzer
from Gluer    import Gluer
from Attacher import Attacher
def main():
    if len(sys.argv) < 2:
        print "To inline hook some function   :   python RewriterMain.py rewrite ApkDir FunctionSignature"
        print "To perform static analysis     :   python RewriterMain.py analyze ApkDir"
        print "To glue APIHook to the package :   python RewriterMain.py glue ApkDir"
        return
    
    Command = sys.argv[1]
    ApkDir = sys.argv[2]
    if Command == "rewrite":
        FuncSig = sys.argv[3]
        worker = Rewriter()
        worker.reset(FuncSig)
    elif Command == "analyze":
        worker = Analyzer()
        worker.reset()
    elif Command == "glue":
        worker = Gluer()
        worker.reset(ApkDir)
    elif Command == "attach":
        worker = Attacher()
        worker.reset(ApkDir)

    for dirname, _, filenames in os.walk(ApkDir):
        for filename in filenames:
            fname = os.path.join(dirname, filename)
            worker.dofile(fname)
            
    worker.postprocess()

if __name__ == '__main__':
#    j = {}
#    d = {}
#    print patchInvokeKind("invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J", "LClass;", d, j)
    main()