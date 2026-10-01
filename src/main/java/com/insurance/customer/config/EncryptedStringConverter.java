package com.insurance.customer.config;

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

/**
 * Transparent JPA converter — encrypts sensitive fields before writing to DB,
 * decrypts on read. Uses AES-256-GCM (authenticated encryption).
 * Set FIELD_ENCRYPTION_KEY env var to a 32-byte Base64 key.
 * Generate with: openssl rand -base64 32
 */
@Slf4j
@Converter
@Component("customerEncryptedStringConverter")
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM    = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH    = 12;   // 96-bit IV recommended for GCM
    private static final int    TAG_LENGTH   = 128;  // GCM authentication tag bits

    private final SecretKeySpec secretKey;
    private final boolean encryptionEnabled;

    public EncryptedStringConverter(
            @Value("${encryption.field-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("FIELD_ENCRYPTION_KEY not set — sensitive fields stored in plaintext. Set encryption.field-key in production.");
            this.secretKey = null;
            this.encryptionEnabled = false;
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != 32) {
                throw new IllegalStateException("encryption.field-key must be 32 bytes (256 bits) base64-encoded.");
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            this.encryptionEnabled = true;
            log.info("Field-level encryption enabled (AES-256-GCM)");
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

            // Prepend IV to ciphertext so we can extract it on decrypt
            ByteBuffer buffer = ByteBuffer.allocate(IV_LENGTH + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String encrypted) {
        if (encrypted == null || !encryptionEnabled) return encrypted;
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed — returning raw value (may be legacy plaintext): {}", e.getMessage());
            return encrypted; // Graceful fallback for legacy unencrypted records
        }
    }
}
