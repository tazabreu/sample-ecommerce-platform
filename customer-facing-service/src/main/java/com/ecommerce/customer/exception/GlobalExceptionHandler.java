package com.ecommerce.customer.exception;

import com.ecommerce.customer.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for customer-facing service.
 * 
 * <p>Handles common exceptions and maps them to appropriate HTTP status codes:</p>
 * <ul>
 *   <li>ResourceNotFoundException → 404 Not Found</li>
 *   <li>IllegalArgumentException → 400 Bad Request</li>
 *   <li>ConstraintViolationException → 400 Bad Request</li>
 *   <li>MethodArgumentNotValidException → 400 Bad Request</li>
 *   <li>OptimisticLockException → 409 Conflict</li>
 *   <li>DataIntegrityViolationException → 409 Conflict</li>
 *   <li>InsufficientInventoryException → 409 Conflict</li>
 *   <li>Exception → 500 Internal Server Error</li>
 * </ul>
 * 
 * <p>All error responses include correlation ID for tracing.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle resource not found exceptions.
     *
     * @param ex the exception
     * @return 404 error response
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        logger.warn("Resource not found: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(java.time.LocalDateTime.now(), 
                HttpStatus.NOT_FOUND.value(),
                "Resource Not Found",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle illegal argument exceptions.
     *
     * @param ex the exception
     * @return 400 error response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        logger.warn("Illegal argument: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(java.time.LocalDateTime.now(), 
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle constraint violation exceptions (Bean Validation).
     *
     * @param ex the exception
     * @return 400 error response with validation errors
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        logger.warn("Constraint violation: {}", ex.getMessage());
        
        String violations = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        
        ErrorResponse error = new ErrorResponse(java.time.LocalDateTime.now(), 
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                violations,
                request.getRequestURI(),
                null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle method argument not valid exceptions (request body validation).
     *
     * @param ex the exception
     * @return 400 error response with field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        logger.warn("Method argument validation failed: {}", ex.getMessage());
        
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Failed");
        response.put("message", "Invalid request body");
        response.put("fieldErrors", fieldErrors);
        response.put("timestamp", Instant.now());
        response.put("correlationId", null);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle optimistic locking failures (concurrent modifications).
     *
     * @param ex the exception
     * @return 409 error response
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        logger.warn("Optimistic locking failure: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(java.time.LocalDateTime.now(), 
                HttpStatus.CONFLICT.value(),
                "Conflict",
                "The resource was modified by another request. Please retry.",
                request.getRequestURI(),
                null
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle data integrity violation exceptions (duplicate keys, foreign key violations).
     *
     * @param ex the exception
     * @return 409 error response
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        logger.warn("Data integrity violation: {}", ex.getMessage());
        
        String message = "Data integrity constraint violated";
        
        // Check for common constraint violations
        String exMessage = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (exMessage.contains("unique")) {
            message = "A resource with this value already exists";
        } else if (exMessage.contains("foreign key")) {
            message = "Referenced resource does not exist";
        }
        
        ErrorResponse error = new ErrorResponse(java.time.LocalDateTime.now(), 
                HttpStatus.CONFLICT.value(),
                "Conflict",
                message,
                request.getRequestURI(),
                null
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle insufficient inventory exceptions.
     *
     * @param ex the exception
     * @return 409 error response
     */
    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientInventory(InsufficientInventoryException ex, HttpServletRequest request) {
        logger.warn("Insufficient inventory: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(java.time.LocalDateTime.now(), 
                HttpStatus.CONFLICT.value(),
                "Insufficient Inventory",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle all other exceptions.
     *
     * @param ex the exception
     * @return 500 error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        logger.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        ErrorResponse error = new ErrorResponse(java.time.LocalDateTime.now(), 
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI(),
                null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}


