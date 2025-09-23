package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {}