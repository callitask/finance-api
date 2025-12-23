package com.treishvaam.financeapi.aspect;

import com.treishvaam.financeapi.model.AuditLog;
import com.treishvaam.financeapi.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletableFuture; // ADDED: For Async execution
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

    // 1. Get User Info (Must be done on main thread)
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated()) {
      username = auth.getName();
    }

    // 2. Get IP Address (Must be done on main thread)
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      HttpServletRequest request = attributes.getRequest();
      ipAddress = request.getHeader("X-Forwarded-For");
      if (ipAddress == null || ipAddress.isEmpty()) {
        ipAddress = request.getRemoteAddr();
      }
    }

    try {
      // 3. Execute the actual method (Blocking)
      return joinPoint.proceed();
    } catch (Exception e) {
      status = "FAILURE";
      details = "Error: " + e.getMessage();
      throw e; // Re-throw to not break app logic
    } finally {
      // 4. Save Log Asynchronously (Fire and forget)
      // Capturing variables effectively final for the lambda
      String finalAction = action;
      String finalUsername = username;
      String finalTarget = target;
      String finalDetails = details;
      String finalIpAddress = ipAddress;
      String finalStatus = status;

      CompletableFuture.runAsync(
          () -> {
            try {
              AuditLog log =
                  new AuditLog(
                      finalAction,
                      finalUsername,
                      finalTarget,
                      finalDetails,
                      finalIpAddress,
                      finalStatus);
              auditLogRepository.save(log);
              logger.debug(
                  "AUDIT (Async): {} by {} -> {}", finalAction, finalUsername, finalStatus);
            } catch (Exception ex) {
              // Log error but do not fail the request
              logger.error("Failed to save audit log asynchronously", ex);
            }
          });
    }
  }
}
