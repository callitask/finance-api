package com.treishvaam.financeapi.model;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Represents a contact form submission.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED (Phase 6): • Changed `email` and `message`
 * column definitions to `TEXT` and added `@Convert(converter = EncryptedStringConverter.class)`. •
 * Why: Encrypt user-submitted PII at rest to comply with Zero-Trust architecture.
 */
import com.treishvaam.financeapi.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class ContactMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  // Phase 6: PII Encryption at Rest
  @Column(columnDefinition = "TEXT")
  @Convert(converter = EncryptedStringConverter.class)
  private String email;

  // Phase 6: PII Encryption at Rest
  @Column(columnDefinition = "TEXT")
  @Convert(converter = EncryptedStringConverter.class)
  private String message;

  public ContactMessage() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
