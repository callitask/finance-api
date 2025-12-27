package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.dto.ContactInfoDTO;
import com.treishvaam.financeapi.model.ContactMessage;
import com.treishvaam.financeapi.repository.ContactMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contact")
public class ContactController {

  @Autowired private ContactMessageRepository contactMessageRepository;

  @PostMapping
  public ResponseEntity<String> submitContactForm(@RequestBody ContactMessage message) {
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
