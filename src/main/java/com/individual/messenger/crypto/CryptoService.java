package com.individual.messenger.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CryptoService {

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();
    private static final int GCM_TAG_LEN_BITS = 128;
    private static final int GCM_IV_LEN_BYTES = 12;

    public CryptoService(@Value("${app.crypto.aesKeyBase64}") String base64Key) {
        byte[] k = Base64.getDecoder().decode(base64Key);
        if (!(k.length == 16 || k.length == 24 || k.length == 32)) {
            throw new IllegalArgumentException("AES key must be 16/24/32 bytes");
        }
        this.key = new SecretKeySpec(k, "AES");
    }

    public String encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[GCM_IV_LEN_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN_BITS, iv));
            byte[] ct = cipher.doFinal(plain);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out); // URL-safe
        } catch (Exception e) {
            throw new RuntimeException("Encrypt failed", e);
        }
    }

    public byte[] decrypt(String token) {
        try {
            byte[] in = Base64.getUrlDecoder().decode(token);
            byte[] iv = new byte[GCM_IV_LEN_BYTES];
            System.arraycopy(in, 0, iv, 0, GCM_IV_LEN_BYTES);
            byte[] ct = new byte[in.length - GCM_IV_LEN_BYTES];
            System.arraycopy(in, GCM_IV_LEN_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN_BITS, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new RuntimeException("Decrypt failed", e);
        }
    }

    public String encryptString(String plain) {
        return encrypt(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public String decryptString(String token) {
        return new String(decrypt(token), java.nio.charset.StandardCharsets.UTF_8);
    }
}
