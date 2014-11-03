import re
import sys, os
import codecs
#http://androguard.blogspot.com/2011/03/androids-binary-xml.html
from apk import AXMLPrinter
from xml.dom import minidom

if sys.argv[1] != '-':
    ap = AXMLPrinter(open(sys.argv[1], "rb").read())
else:
    ap = AXMLPrinter(sys.stdin.read())
    
xml = minidom.parseString(ap.getBuff())

if sys.argv[2] != '-':
    xml.writexml(codecs.open(sys.argv[2], "wb", "utf-8"))
else:
    xml.writexml(codecs.getwriter('utf-8')(sys.stdout))


