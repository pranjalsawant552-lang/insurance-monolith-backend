package com.insurance.agent.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Converter
@Component("agentEncryptedStringConverter")
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH  = 12;
    private static final int    TAG_LENGTH = 128;

    private final SecretKeySpec secretKey;
    private final boolean encryptionEnabled;

    public EncryptedStringConverter(@Value("${encryption.field-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("encryption.field-key not set — bank account numbers stored in plaintext in dev mode.");
            this.secretKey = null;
            this.encryptionEnabled = false;
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            this.encryptionEnabled = true;
        }
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null || !encryptionEnabled) return plaintext;
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(IV_LENGTH + ciphertext.length);
            buf.put(iv); buf.put(ciphertext);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) { throw new RuntimeException("Encryption failed", e); }
    }

    @Override
    public String convertToEntityAttribute(String encrypted) {
        if (encrypted == null || !encryptionEnabled) return encrypted;
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            ByteBuffer buf = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH]; buf.get(iv);
            byte[] ct = new byte[buf.remaining()]; buf.get(ct);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) { return encrypted; }
    }
}
