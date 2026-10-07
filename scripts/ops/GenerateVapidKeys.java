import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.ECGenParameterSpec;
import java.math.BigInteger;
import java.util.*;

/** Creates a private local file; never logs keys or overwrites an existing configuration. */
public final class GenerateVapidKeys {
    static byte[] bytes(BigInteger value) {
        byte[] raw=value.toByteArray(),out=new byte[32];
        System.arraycopy(raw,Math.max(0,raw.length-32),out,Math.max(0,32-raw.length),Math.min(32,raw.length));return out;
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Usage: java GenerateVapidKeys.java .local/push.env");
        Path file=Path.of(args[0]).toAbsolutePath();Files.createDirectories(file.getParent());
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair=generator.generateKeyPair();var point=((ECPublicKey)pair.getPublic()).getW();byte[] publicKey=new byte[65];publicKey[0]=4;
        System.arraycopy(bytes(point.getAffineX()),0,publicKey,1,32);System.arraycopy(bytes(point.getAffineY()),0,publicKey,33,32);
        var base64=Base64.getUrlEncoder().withoutPadding();String content="APP_PUSH_PUBLIC_KEY="+base64.encodeToString(publicKey)+"\nAPP_PUSH_PRIVATE_KEY="+
                base64.encodeToString(bytes(((ECPrivateKey)pair.getPrivate()).getS()))+"\n";
        if(Files.getFileStore(file.getParent()).supportsFileAttributeView("posix"))
            Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        else Files.createFile(file);
        Files.writeString(file,content,StandardOpenOption.WRITE);
        System.out.println("VAPID keys written to the new local file. Protect its Windows ACL/OS permissions; do not upload it.");
    }
}
