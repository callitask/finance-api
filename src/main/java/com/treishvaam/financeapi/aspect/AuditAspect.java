package com.treishvaam.financeapi.aspect;

import com.treishvaam.financeapi.model.AuditLog;
import com.treishvaam.financeapi.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditAspect {

  private static final Logger logger = LoggerFactory.getLogger(AuditAspect.class);

  @Autowired private AuditLogRepository auditLogRepository;

  @Around("@annotation(logAudit)")
  public Object logAuditAction(ProceedingJoinPoint joinPoint, LogAudit logAudit) throws Throwable {
    String action = logAudit.action();
    String target = logAudit.target();
    String status = "SUCCESS";
    String details = "Action completed successfully.";
    String username = "ANONYMOUS";
    String ipAddress = "0.0.0.0";

    // 1. Get User Info
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated()) {
      username = auth.getName();
    }

    // 2. Get IP Address
    HttpServletRequest request =
        ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    if (request != null) {
      ipAddress = request.getHeader("X-Forwarded-For");
      if (ipAddress == null || ipAddress.isEmpty()) {
        ipAddress = request.getRemoteAddr();
      }
    }

    long start = System.currentTimeMillis();
    try {
      // 3. Execute the actual method
      return joinPoint.proceed();
    } catch (Exception e) {
      status = "FAILURE";
      details = "Error: " + e.getMessage();
      throw e; // Re-throw to not break app logic
    } finally {
      // 4. Save Log Asynchronously (Fire and forget, conceptually)
      try {
        // Determine dynamic target if needed (e.g. from args), here simplified
        AuditLog log = new AuditLog(action, username, target, details, ipAddress, status);
        auditLogRepository.save(log);
        logger.info("AUDIT: {} by {} -> {}", action, username, status);
      } catch (Exception ex) {
        logger.error("Failed to save audit log", ex);
      }
    }
  }
}
