package com.treishvaam.financeapi.exception;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<Map<String, String>> handleOptimisticLockingFailure(
      ObjectOptimisticLockingFailureException ex) {
    logger.warn("Optimistic locking failure detected: {}", ex.getMessage());
    Map<String, String> error = new HashMap<>();
    error.put("error", "Conflict detected");
    error.put(
        "message",
        "This record has been modified by another user. Please refresh the page and try again.");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
    logger.error("Unhandled exception: ", ex);
    Map<String, String> error = new HashMap<>();
    error.put("error", "Internal Server Error");
    error.put("message", ex.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
}
