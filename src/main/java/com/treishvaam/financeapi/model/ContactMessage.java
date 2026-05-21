package com.treishvaam.financeapi.model;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Represents a contact form submission.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED (Phase 6): • Changed `email` and `message`
 * column definitions to `TEXT` and added `@Convert(converter = EncryptedStringConverter.class)`. •
 * Why: Encrypt user-submitted PII at rest to comply with Zero-Trust architecture.
 *
 * <p>- EDITED (Phase 6 Fix): • Replaced `EncryptedStringConverter` with domain-specific
 * `ContactEmailConverter` and `ContactMessageConverter`. • Why: To isolate encryption keys per
 * domain field.
 *
 * <p>- EDITED (Phase 3 — Form Security): • Added `@Transient honeypot` field. • Why: Required to
 * deserialize honeypot payload without persisting it to database.
 */
import com.treishvaam.financeapi.security.ContactEmailConverter;
import com.treishvaam.financeapi.security.ContactMessageConverter;
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

  // Phase 6: PII Encryption at Rest (Domain-Specific)
  @Column(columnDefinition = "TEXT")
  @Convert(converter = ContactEmailConverter.class)
  private String email;

  // Phase 6: PII Encryption at Rest (Domain-Specific)
  @Column(columnDefinition = "TEXT")
  @Convert(converter = ContactMessageConverter.class)
  private String message;

  // Phase 3: Honeypot field for bot detection
  // AI-CONTEXT: This field is hidden from humans via CSS. Bots fill all fields.
  @jakarta.persistence.Transient private String honeypot;

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

  public String getHoneypot() {
    return honeypot;
  }

  public void setHoneypot(String honeypot) {
    this.honeypot = honeypot;
  }
}
