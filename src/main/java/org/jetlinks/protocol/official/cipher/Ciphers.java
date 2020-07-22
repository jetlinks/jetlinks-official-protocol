package org.jetlinks.protocol.official.cipher;

import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Optional;

public enum Ciphers {
    AES {
        @SneakyThrows
        public byte[] encrypt(byte[] src, String key) {
            if (key == null || key.length() != 16) {
                throw new IllegalArgumentException("illegal key");
            }
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            return cipher.doFinal(src);
        }

        @SneakyThrows
        public byte[] decrypt(byte[] src, String key) {
            if (key == null || key.length() != 16) {
                throw new IllegalArgumentException("illegal key");
            }
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            return cipher.doFinal(src);
        }
    };


    public static Optional<Ciphers> of(String name) {
        try {
            return Optional.of(valueOf(name.toUpperCase()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public abstract byte[] encrypt(byte[] src, String key);

    public abstract byte[] decrypt(byte[] src, String key);

    String encryptBase64(String src, String key) {
        return Base64.encodeBase64String(encrypt(src.getBytes(), key));
    }

    byte[] decryptBase64(String src, String key) {
        return decrypt(Base64.decodeBase64(src), key);
    }

}
