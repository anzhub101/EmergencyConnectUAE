// =====================================================================
// EmergencyConnectUAE | CSC408 Distributed Information Systems - Assignment 3
// Abu Dhabi University | Spring 2026
// Section: 103   Group: 4
// Students: 1095305, 1092093, 1089507
// =====================================================================
package com.emergencyconnectuae.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps exceptions to HTTP status codes in one place (SRS 5.6 / 9):
 *  400 malformed input / path traversal / bad path param
 *  404 missing resource
 *  409 Redisson lock contention
 *  422 bean-validation failure / invalid state transition
 *  429 rate limit exceeded
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static Map<String, String> body(String error, String message) {
        Map<String, String> m = new HashMap<>();
        m.put("error", error);
        m.put("message", message);
        return m;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ResourceLockedException.class)
    public ResponseEntity<Map<String, String>> handleLocked(ResourceLockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body("RESOURCE_LOCKED", ex.getMessage()));
    }

    @ExceptionHandler(MfaRequiredException.class)
    public ResponseEntity<Map<String, String>> handleMfa(MfaRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body("MFA_REQUIRED", ex.getMessage()));
    }

    /**
     * Method-level @PreAuthorize denials throw AuthorizationDeniedException
     * (a subclass of AccessDeniedException) at the controller method, where this
     * @ControllerAdvice intercepts it before Spring Security's accessDeniedHandler
     * can. Without this handler the generic Exception fallback below turned RBAC
     * denials into 500s instead of 403s.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body("FORBIDDEN", "Insufficient privileges"));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, String>> handleTransition(InvalidStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body("INVALID_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body("TOO_MANY_REQUESTS", ex.getMessage()));
    }

    /** Body-level @Valid failures -> 422 with a field-level error map. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("error", "VALIDATION_FAILED");
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errors);
    }

    /** Query/path @Validated failures (e.g. proximity lat/lng bounds) -> 422. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleParamValidation(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body("VALIDATION_FAILED", ex.getMessage()));
    }

    /** Malformed JSON body -> 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body("BAD_REQUEST", "Malformed request body"));
    }

    /** Wrong type in path/query (e.g. non-UUID id) -> 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body("BAD_REQUEST", "Invalid parameter: " + ex.getName()));
    }

    /** Fallback — never leak internal exception details in the response body. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_SERVER_ERROR", "An unexpected error occurred"));
    }
}
