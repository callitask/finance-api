package com.treishvaam.financeapi.exception;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Centralized exception handling for the finance-api REST layer.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • ObjectOptimisticLockingFailureException
 * handler returning 409 Conflict. - EDITED (Phase 6 - Malformed JSON 400 & Security 401/403
 * Preservation - 2026-10-31): Added explicit handlers for HttpMessageNotReadableException,
 * MethodArgumentTypeMismatchException, MissingServletRequestParameterException (400 Bad Request),
 * NoResourceFoundException (404 Not Found), and rethrow for AccessDeniedException /
 * AuthenticationException so Spring Security's ExceptionTranslationFilter returns native 401/403.
 */
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {
        logger.warn("Malformed request payload: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        Map.of(
                                "error",
                                true,
                                "message",
                                "Malformed request payload or invalid parameter."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        logger.warn("Method argument type mismatch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        Map.of(
                                "error",
                                true,
                                "message",
                                "Malformed request payload or invalid parameter."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex) {
        logger.warn("Missing servlet request parameter: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        Map.of(
                                "error",
                                true,
                                "message",
                                "Malformed request payload or invalid parameter."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex) {
        logger.warn("Requested resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", true, "message", "Requested resource not found."));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void handleSecurityExceptions(Exception ex) throws Exception {
        // Rethrow so Spring Security's ExceptionTranslationFilter natively returns 401/403
        throw ex;
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
