package com.sitegenius.hrms.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Central exception handler for the entire HRMS application.
 * Every exception is caught here — the server NEVER crashes due to unhandled exceptions.
 * All errors return a consistent JSON structure.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 1. Resource Not Found (404) ───────────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage());
    }

    // ── 2. Bad Request / Business Rule Violation (400) ────────────────────────
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    // ── 3. Invalid Status Transition (422) ────────────────────────────────────
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStatusTransitionException ex) {
        log.warn("Invalid status transition: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STATUS_TRANSITION", ex.getMessage());
    }

    // ── 4. Unauthorized / Forbidden Access (403) ──────────────────────────────
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized access attempt: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    // ── 5. Spring Security: Access Denied (403) ───────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action.");
    }

    // ── 6. Spring Security: Authentication Failed (401) ───────────────────────
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication failed. Please login again.");
    }

    // ── 7. Bean Validation Errors (@Valid) ────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(field, message);
        });

        log.warn("Validation failed: {}", fieldErrors);

        Map<String, Object> response = new HashMap<>();
        response.put("status", 400);
        response.put("errorCode", "VALIDATION_FAILED");
        response.put("message", "Request validation failed. Please check the errors.");
        response.put("errors", fieldErrors);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.badRequest().body(response);
    }

    // ── 8. Wrong HTTP Method (405) ────────────────────────────────────────────
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint.");
    }

    // ── 9. URL Not Found (404) ────────────────────────────────────────────────
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException ex) {
        log.warn("No handler found: {}", ex.getRequestURL());
        return build(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND",
                "The requested endpoint '" + ex.getRequestURL() + "' does not exist.");
    }

    // ── 10. Missing Required Request Param ────────────────────────────────────
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing.");
    }

    // ── 11. Wrong Type in Request Param (e.g. passing "abc" for a Long ID) ────
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for param '{}': value '{}'", ex.getName(), ex.getValue());
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER_TYPE",
                "Parameter '" + ex.getName() + "' has invalid value '" + ex.getValue() +
                        "'. Expected type: " + ex.getRequiredType().getSimpleName());
    }

    // ── 12. Malformed JSON in Request Body ────────────────────────────────────
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON in request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_JSON",
                "Request body is malformed or has invalid JSON. Please check your request.");
    }

    // ── 13. Database Integrity Violation (e.g. duplicate entry, FK violation) ─
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "A database constraint was violated. The record may already exist or " +
                        "a referenced record does not exist.");
    }

    // ── 14. General Database / JPA Errors ─────────────────────────────────────
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex) {
        log.error("Database error occurred: {}", ex.getMessage(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_ERROR",
                "A database error occurred. Please try again shortly.");
    }

    // ── 15. IllegalArgumentException (e.g. invalid enum value) ───────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "ILLEGAL_ARGUMENT", ex.getMessage());
    }

    // ── 16. NullPointerException — should never happen but catch it safely ────
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponse> handleNullPointer(NullPointerException ex) {
        log.error("NullPointerException caught (investigate immediately): {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Our team has been notified.");
    }

    // ── 17. Catch-All — ensures the server NEVER returns an unhandled 500 ─────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex) {
        log.error("Unhandled exception caught: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again or contact support.");
    }

    // ── Builder Helper ────────────────────────────────────────────────────────
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String errorCode, String message) {
        ErrorResponse body = new ErrorResponse(
                status.value(),
                errorCode,
                message,
                LocalDateTime.now().toString()
        );
        return ResponseEntity.status(status).body(body);
    }

    // ── Standard Error Response Structure ─────────────────────────────────────
    public record ErrorResponse(
            int status,
            String errorCode,
            String message,
            String timestamp
    ) {}
}