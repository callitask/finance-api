package com.treishvaam.financeapi.security;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - JPA AttributeConverter that transparently encrypts User email fields before
 * persisting to MariaDB. Implements Phase 6 (ENC-Domain).
 *
 * <p>Scope: - AES-256-GCM encryption/decryption strictly for the User.email field.
 *
 * <p>Critical Dependencies: - Backend: Requires env var USER_EMAIL_ENCRYPTION_KEY (Base64- encoded
 * 32-byte AES key).
 *
 * <p>Security Constraints: - Sourced exclusively from env var USER_EMAIL_ENCRYPTION_KEY.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Phase 6 - ENC-Domain): • Created
 * UserEmailConverter to isolate User email encryption. • Why: Ensures a compromise of one
 * encryption key does not expose all encrypted fields.
 */
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
public class UserEmailConverter implements AttributeConverter<String, String> {

  private static final Logger logger = LoggerFactory.getLogger(UserEmailConverter.class);

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 128;
  private static final String KEY_VERSION_PREFIX = "v1:";

  private final SecretKey secretKey;

  public UserEmailConverter() {
    String encodedKey = System.getenv("USER_EMAIL_ENCRYPTION_KEY");
    if (encodedKey == null || encodedKey.trim().isEmpty()) {
      throw new IllegalStateException(
          "[UserEmailConverter] USER_EMAIL_ENCRYPTION_KEY environment variable is missing.");
    }
    byte[] keyBytes = Base64.getDecoder().decode(encodedKey.trim());
    if (keyBytes.length != 32) {
      throw new IllegalStateException("[UserEmailConverter] Key must be 32 bytes.");
    }
    this.secretKey = new SecretKeySpec(keyBytes, "AES");
  }

  @Override
  public String convertToDatabaseColumn(String plaintext) {
    if (plaintext == null) return null;
    try {
      byte[] iv = new byte[GCM_IV_LENGTH];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

      ByteBuffer combined = ByteBuffer.allocate(iv.length + ciphertext.length);
      combined.put(iv);
      combined.put(ciphertext);

      return KEY_VERSION_PREFIX + Base64.getEncoder().encodeToString(combined.array());
    } catch (Exception e) {
      throw new RuntimeException("Encryption failed.", e);
    }
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) return null;
    if (!dbData.startsWith(KEY_VERSION_PREFIX)) return dbData;

    try {
      String base64Payload = dbData.substring(KEY_VERSION_PREFIX.length());
      byte[] combined = Base64.getDecoder().decode(base64Payload);

      ByteBuffer buffer = ByteBuffer.wrap(combined);
      byte[] iv = new byte[GCM_IV_LENGTH];
      buffer.get(iv);
      byte[] ciphertext = new byte[buffer.remaining()];
      buffer.get(ciphertext);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      byte[] plaintext = cipher.doFinal(ciphertext);

      return new String(plaintext, "UTF-8");
    } catch (Exception e) {
      logger.error("Decryption failed.", e);
      return null;
    }
  }
}
