package org.example.districtpulse.source;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM encryption for source credentials. The key comes from the SOURCE_SECRET_KEY environment variable. */
@Component
public class SecretBox {

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(@Value("${source.secret-key:}") String secret) {
        if (secret == null || secret.isBlank()) {
            this.key = null;
        } else {
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
                this.key = new SecretKeySpec(hash, "AES");
            } catch (Exception e) {
                throw new IllegalStateException("Could not initialise secret key", e);
            }
        }
    }

    public boolean available() {
        return key != null;
    }

    public String encrypt(String plain) {
        if (key == null) throw new IllegalStateException("SOURCE_SECRET_KEY is not configured");
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        if (key == null) throw new IllegalStateException("SOURCE_SECRET_KEY is not configured");
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[12];
            System.arraycopy(in, 0, iv, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(in, 12, in.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
