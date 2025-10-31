package com.individual.messenger.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public final class Aes256 {
    private final SecretKeySpec key;
    private final IvParameterSpec iv;

    public Aes256(String key32, String iv16) {
        // key32: 32諛붿씠???? 32-length ASCII), iv16: 16諛붿씠??
        this.key = new SecretKeySpec(key32.getBytes(), "AES");
        this.iv  = new IvParameterSpec(iv16.getBytes());
    }

    public String encrypt(String plain) {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, key, iv);
            return Base64.getEncoder().encodeToString(c.doFinal(plain.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt failed", e);
        }
    }

    public String decrypt(String base64) {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, key, iv);
            return new String(c.doFinal(Base64.getDecoder().decode(base64)), "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt failed", e);
        }
    }
}


