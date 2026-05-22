package com.treishvaam.financeapi.security;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - JPA AttributeConverter that transparently encrypts sensitive string fields (e.g.,
 * LinkedIn OAuth tokens) before persisting to MariaDB and decrypts on read. Implements CVE-007 fix:
 * LinkedIn access tokens must never be stored in plaintext.
 *
 * <p>Scope: - Responsible for: AES-256-GCM encryption/decryption of a single String field. - Must
 * NEVER be responsible for: key rotation, key storage, or authentication logic.
 *
 * <p>Critical Dependencies: - Backend: Requires env var LINKEDIN_TOKEN_ENCRYPTION_KEY (Base64-
 * encoded 32-byte AES key). Must be injected via Docker Compose .env and Infisical. - Frontend:
 * None — this is a pure persistence layer concern. - Worker / SEO: None.
 *
 * <p>Security Constraints: - The encryption key MUST come from env var
 * LINKEDIN_TOKEN_ENCRYPTION_KEY. NEVER hardcode the key. - AES-256-GCM is used: authenticated
 * encryption that detects tampering (unlike AES-CBC). - A fresh 12-byte IV is generated per
 * encryption call. The IV is prepended to the ciphertext and stored together (IV + ciphertext,
 * Base64-encoded). - If the env var is missing or invalid, the converter throws
 * IllegalStateException at startup to prevent silent plaintext storage.
 *
 * <p>Non-Negotiables: - The stored format is: Base64(IV[12 bytes] + Ciphertext). - Decryption reads
 * the first 12 bytes as IV and the remainder as ciphertext. - convertToEntityAttribute() must
 * return null gracefully if the DB value is null (user has no LinkedIn token). - This converter
 * must be registered on User.linkedinAccessToken
 * via @Convert(converter=EncryptedStringConverter.class).
 *
 * <p>Change Intent: - CVE-007: LinkedIn access tokens were stored as VARCHAR(1024) plaintext in
 * MariaDB. A database compromise would expose all LinkedIn OAuth tokens. AES-256-GCM encryption at
 * the JPA layer ensures tokens are encrypted at rest without any application logic changes.
 *
 * <p>Future AI Guidance: - Do NOT replace AES-256-GCM with AES-CBC or AES-ECB. GCM provides
 * authenticated encryption; CBC does not detect tampering; ECB is insecure. - Do NOT store the IV
 * separately from the ciphertext — the combined Base64 format is intentional. - If key rotation is
 * needed in future: add a key version prefix to the stored value and maintain a key map. Do NOT
 * re-encrypt all rows in a single transaction. - The column length in MariaDB must accommodate the
 * Base64-encoded (IV + ciphertext). A 1024-char token becomes ~1400 chars after encryption. The
 * Liquibase migration V42 increases the column to 2048 chars.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (2026-05-14 CVE-007 Fix): • Created
 * EncryptedStringConverter implementing JPA AttributeConverter<String, String>. • Uses AES-256-GCM
 * with a per-encryption random 12-byte IV prepended to ciphertext. • Key sourced exclusively from
 * env var LINKEDIN_TOKEN_ENCRYPTION_KEY (Base64-encoded 32 bytes). • Applied to
 * User.linkedinAccessToken via @Convert annotation. • Why: LinkedIn OAuth tokens stored plaintext
 * in MariaDB — database compromise exposes all tokens (CVE-007, CVSS 5.3). * - EDITED (Phase 6 -
 * ENC-03, ENC-04): • Implemented `v1:` prefixing for all new ciphertexts to support future key
 * rotation and domain-specific key separation. • Added seamless fallback to return plaintext if the
 * `v1:` prefix is missing. • Why: Allows encryption to be rolled out across live data (like emails)
 * without crashing the application on old plaintext rows. Legacy rows will be automatically
 * encrypted on their next update.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
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
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger logger = LoggerFactory.getLogger(EncryptedStringConverter.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96-bit IV — recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128-bit authentication tag
    private static final String KEY_VERSION_PREFIX = "v1:"; // Prefix for key rotation support

    private final SecretKey secretKey;

    public EncryptedStringConverter() {
        String encodedKey = System.getenv("LINKEDIN_TOKEN_ENCRYPTION_KEY");
        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "[EncryptedStringConverter] LINKEDIN_TOKEN_ENCRYPTION_KEY environment variable is"
                            + " missing or empty. Cannot start application with plaintext LinkedIn token"
                            + " storage. Set a Base64-encoded 32-byte AES key in your .env file.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "[EncryptedStringConverter] LINKEDIN_TOKEN_ENCRYPTION_KEY is not valid Base64.",
                    e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "[EncryptedStringConverter] LINKEDIN_TOKEN_ENCRYPTION_KEY must decode to exactly 32"
                            + " bytes (256-bit AES key). Got: "
                            + keyBytes.length
                            + " bytes.");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        logger.info("[EncryptedStringConverter] AES-256-GCM converter initialized successfully.");
    }

    /**
     * Encrypts the plaintext token before persisting to the database. Stored format:
     * v1:Base64(IV[12 bytes] || Ciphertext).
     *
     * @param plaintext the raw string (may be null)
     * @return Version-prefixed, Base64-encoded encrypted value, or null if input is null
     */
    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            // Prepend IV to ciphertext and Base64-encode the combined result
            ByteBuffer combined = ByteBuffer.allocate(iv.length + ciphertext.length);
            combined.put(iv);
            combined.put(ciphertext);

            return KEY_VERSION_PREFIX + Base64.getEncoder().encodeToString(combined.array());
        } catch (Exception e) {
            throw new RuntimeException("[EncryptedStringConverter] Encryption failed.", e);
        }
    }

    /**
     * Decrypts the stored Base64-encoded value back to the plaintext token. Stored format:
     * v1:Base64(IV[12 bytes] || Ciphertext). Supports seamless fallback for unencrypted legacy
     * data.
     *
     * @param dbData the string value from the database (may be null)
     * @return the original plaintext token, or null if input is null
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        // Fallback logic: If it doesn't start with our key version prefix,
        // it is legacy plaintext data. Return as-is so the system doesn't crash.
        if (!dbData.startsWith(KEY_VERSION_PREFIX)) {
            return dbData;
        }

        try {
            // Strip the prefix before decoding
            String base64Payload = dbData.substring(KEY_VERSION_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(base64Payload);

            // Extract IV (first 12 bytes) and ciphertext (remainder)
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
            logger.error(
                    "[EncryptedStringConverter] Decryption failed. Data may be corrupted or key mismatch.",
                    e);
            // Return null rather than throwing — prevents a single corrupted record from
            // crashing the entire API load.
            return null;
        }
    }
}
