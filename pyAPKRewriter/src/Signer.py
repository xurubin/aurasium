#!/usr/bin/env python
'''
Created on 3 Jul 2012

@author: rx201
'''
import sys
import argparse
from subprocess import Popen, PIPE
import PKCS7SignedData 
import hashlib
import os.path
from zipfile import ZipFile
from pyasn1.codec.der import encoder as der_encoder
from asn1 import dn
import base64
import os

keystore_location = os.environ.get('AURASIUM_KEYSTORE', 
                          os.path.join(os.path.dirname(os.path.realpath(__file__)), "aurasium.store"))
keystore_password = "C4hSmUku3gC2e2ramsey"

def runProgram(program, *arguments):
    p = Popen([program] + list(arguments), stdout = PIPE, stderr = PIPE)
    #sts = os.waitpid(p.pid, 0)[1]
    p.wait()
    return (p.returncode, p.stdout.read(), p.stderr.read())

class keytoolWrapper():
    if os.name == "nt":
        KeyTool="c:\\Program Files\\Java\\jdk1.6.0_25\\bin\\keytool.exe"
    else:
        KeyTool="keytool"
    #TODO: key_uid is base64 encoded. Due to constraints in keystore, only 
    # the first 8 characters are used, leaving us around 2^(6*4) available
    # spaces in keystore only due to collision.
    @staticmethod
    def lookup(key_uid):
        rtn, out, errmsg = runProgram(keytoolWrapper.KeyTool, "-list", 
                                          "-keystore", keystore_location,
                                          "-storepass", keystore_password,
                                          "-alias", key_uid)
        #print out
        #print "-" * 16
        #print  errmsg
        return rtn == 0
    @staticmethod
    def generate(key_uid, key_password, dname, validity):
        rtn, out, errmsg = runProgram(keytoolWrapper.KeyTool, "-genkeypair", 
                                          "-keystore", keystore_location,
                                          "-storepass", keystore_password,
                                          "-alias", key_uid,
                                          "-keypass", key_password,
                                          "-dname", dname,
                                          "-validity", validity,
                                          "-keyalg", "RSA",
                                          )
        if rtn != 0:
            print out, errmsg
        return rtn == 0
class jarsignerWrapper():
    if os.name == "nt":
        JarSigner="c:\\Program Files\\Java\\jdk1.6.0_25\\bin\\jarsigner.exe"
    else:
        JarSigner="jarsigner"
    @staticmethod
    def verify(apk):
        rtn, out, _ = runProgram(jarsignerWrapper.JarSigner, "-verify", apk)
        print out
        # Fail if verify returns error, or the apk contains unsigned files.
        if rtn != 0 or out.find("This jar contains unsigned entries") != -1:
            return False
        else:
            return True
    @staticmethod
    def sanitizeSFname(s):
        result = []
        for c in s:
            x = ord(c.upper())
            if (x >= ord('A') and x <= ord('Z') ) or \
               (x >= ord('0') and x <= ord('9') ):
                result.append(c)
        return "".join(result)  
    @staticmethod
    def resign(apk, sf_name, chain_uid, chain_password):
        rtn, out, _ = runProgram(jarsignerWrapper.JarSigner, "-keystore", keystore_location,
                                            "-digestalg", "SHA1",
                                            "-sigalg", "MD5withRSA",
                                            "-storepass", keystore_password,
                                            "-sigfile", jarsignerWrapper.sanitizeSFname(sf_name), 
                                            "-keypass", chain_password, apk, chain_uid)
        print out
        return rtn == 0

def escapeDNStr(s):
    cmap = { ',' : '\\2C', '+' : '\\2B', '"' : '\\22', '\\' : '\\5C',
             '<' : '\\3C', '>' : '\\3E', ';' : '\\3B', '\r' : '\\0D',
            '\n' : '\\0A', '/' : '\\2F', '=' : '\\3D', }
    result = []
    for c in s:
        if c in cmap:
            result.append(cmap[c])
        else:
            result.append(c)
    return "".join(result)
    
def canonicalizeDN(dn):
    result = []
    for name, value in dn.iteritems():
        result.append("%s=%s" % (escapeDNStr(name), escapeDNStr(value)))
    return ",".join(result)
    
def getChainFingerprint(certChain):
    fingerprint = hashlib.sha1()
    secret = hashlib.sha1()
    for cert in certChain:
        fingerprint.update(der_encoder.encode(cert))
        secret.update(der_encoder.encode(cert[0][0]['serialNumber']))
    return fingerprint.hexdigest(), base64.b64encode(secret.digest())
    
def prepareCertificates(signatureBlock):
    signature = PKCS7SignedData.AuthData(signatureBlock)
    chain = signature.GetJarSigningChain()
    chain_uid, chain_secret = getChainFingerprint(chain)
    # invoke keytool to generate/lookup new key
    if not keytoolWrapper.lookup(chain_uid):
        issuer = chain[0][0][0]['issuer']
        issuer_dn = canonicalizeDN(dn.DistinguishedName.TraverseRdn(issuer[0]))
        print "Generate new certificate", chain_uid, issuer_dn
        if not keytoolWrapper.generate(chain_uid, chain_secret, issuer_dn, "10000"):
            return None, None    
    else:
        print "Reuse existing certificate."
    return chain_uid, chain_secret
 
def readSignatureFilelist(apk_archive):
    sf = []
    for zipinfo in apk_archive.infolist():
        dirname, name = os.path.split(zipinfo.filename)
        if dirname.upper() != "META-INF": continue
        basename, ext = os.path.splitext(name)
        if ext.upper() == ".RSA" or ext.upper() == ".DSA":
            if not apk_archive.getinfo("META-INF/%s.SF" % basename):
                print "Missing %s.SF file." % basename
                continue
            sf.append(name)      
    return sf    
        
def resign(target_apk, template_apk):
    with ZipFile(template_apk, 'r') as apkfile:
        for sf in readSignatureFilelist(apkfile):
            print sf
            sf_content = apkfile.open("META-INF/%s" % sf).read()
            #TODO: Verify sf validity to prevent spoofing
            chain_uid, chain_secret = prepareCertificates(sf_content)
            if not chain_uid:
                print "prepareCertificates failed."
                return False
            print "Signing...",
            if not jarsignerWrapper.resign(target_apk, os.path.splitext(sf)[0], chain_uid, chain_secret):
                print "jarsigner signing failed."
                return False
            print "succeed."
    return True

def main():
    parser = argparse.ArgumentParser(description='Aurasium Apk Re-signer.')
    parser.add_argument('-verify', nargs = 1, metavar= ('apk_file'),  help='Verify apk_file\'s signature ')
    parser.add_argument('-resign', nargs = 2, metavar= ('target_apk', 'template_apk'), help='Re-sign target_apk using template_apk as a template')
    
    args = parser.parse_args()
    if args.verify:
        sys.exit(0 if jarsignerWrapper.verify(args.verify[0]) else 1)
    elif args.resign:
        sys.exit(0 if resign(args.resign[0], args.resign[1]) else 1)
    else:
        parser.print_help()
if __name__ == "__main__":
    main()
