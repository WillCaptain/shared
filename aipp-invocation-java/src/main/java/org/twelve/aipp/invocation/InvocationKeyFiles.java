package org.twelve.aipp.invocation;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;

/** Provisioned keys only; private keys never enter application properties as inline values. */
public final class InvocationKeyFiles {
    private InvocationKeyFiles() {}
    public static RSAPrivateKey privateKey(String file) {
        byte[] bytes=null;
        try {
            var path=Path.of(file);
            if(!path.isAbsolute() || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException();
            var perms=Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS);
            if(!Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE).containsAll(perms)
                    || !perms.contains(PosixFilePermission.OWNER_READ)) throw new IllegalArgumentException();
            try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) { bytes=in.readNBytes(16385); }
            if(bytes.length>16384) throw new IllegalArgumentException();
            var key=(RSAPrivateKey)KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
            if(key.getModulus().bitLength()<2048) throw new IllegalArgumentException(); return key;
        } catch(Exception e) { throw new IllegalStateException("invalid_signing_key_configuration"); }
        finally { if(bytes!=null) Arrays.fill(bytes,(byte)0); }
    }
    public static RSAPublicKey publicKey(String pem) {
        try {
            if(pem==null || pem.length()>16384) throw new IllegalArgumentException();
            byte[] bytes=Base64.getDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s",""));
            var key=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
            if(key.getModulus().bitLength()<2048) throw new IllegalArgumentException(); return key;
        } catch(Exception e) { throw new IllegalStateException("invalid_verifier_key_configuration"); }
    }
}
