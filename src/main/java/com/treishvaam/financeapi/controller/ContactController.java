package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.dto.ContactInfoDTO;
import com.treishvaam.financeapi.model.ContactMessage;
import com.treishvaam.financeapi.repository.ContactMessageRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI-CONTEXT: IMMUTABLE CHANGE HISTORY: - EDITED (Phase 3 — Form Security): • Added server-side
 * input validation (name: max 100 chars, email: RFC format, message: max 5000 chars). • Added
 * honeypot field check (_website) — bots fill it, humans don't. • Why: Contact form had zero
 * server-side validation, enabling spam/abuse.
 */
@RestController
@RequestMapping("/api/v1/contact")
public class ContactController {

  @Autowired private ContactMessageRepository contactMessageRepository;

  @PostMapping
  public ResponseEntity<String> submitContactForm(
      @RequestBody ContactMessage message, HttpServletRequest request) {

    // 1. HONEYPOT CHECK: Bots auto-fill all fields. If this has a value, it's a bot.
    if (message.getHoneypot() != null && !message.getHoneypot().isBlank()) {
      // Return 200 OK to bots — don't reveal detection
      return ResponseEntity.ok("Message received successfully!");
    }

    // 2. SERVER-SIDE VALIDATION
    if (message.getName() == null
        || message.getName().trim().isEmpty()
        || message.getName().length() > 100) {
      return ResponseEntity.badRequest().body("Invalid name.");
    }
    if (message.getEmail() == null
        || !message.getEmail().matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")) {
      return ResponseEntity.badRequest().body("Invalid email address.");
    }
    if (message.getMessage() == null
        || message.getMessage().trim().isEmpty()
        || message.getMessage().length() > 5000) {
      return ResponseEntity.badRequest().body("Message must be between 1 and 5000 characters.");
    }

    // 3. Sanitize before persist
    message.setName(
        message.getName().trim().substring(0, Math.min(message.getName().trim().length(), 100)));

    contactMessageRepository.save(message);
    return ResponseEntity.ok("Message received successfully!");
  }

  @GetMapping("/info")
  public ResponseEntity<ContactInfoDTO> getContactInfo() {
    ContactInfoDTO contactInfo =
        new ContactInfoDTO(
            "treishvaamfinance@mail.com", "(+91)-8178527633", "Bengaluru, Karnataka, India");
    return ResponseEntity.ok(contactInfo);
  }
}
