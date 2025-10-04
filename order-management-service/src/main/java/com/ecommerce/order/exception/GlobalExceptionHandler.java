package com.ecommerce.order.exception;

import com.ecommerce.order.dto.ErrorResponse;
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
 * Global exception handler for order management service.
 * 
 * <p>Handles common exceptions and maps them to appropriate HTTP status codes:</p>
 * <ul>
 *   <li>ResourceNotFoundException → 404 Not Found</li>
 *   <li>InvalidOrderStateException → 400 Bad Request</li>
 *   <li>IllegalArgumentException → 400 Bad Request</li>
 *   <li>ConstraintViolationException → 400 Bad Request</li>
 *   <li>MethodArgumentNotValidException → 400 Bad Request</li>
 *   <li>OptimisticLockException → 409 Conflict</li>
 *   <li>DataIntegrityViolationException → 409 Conflict</li>
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
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        logger.warn("Resource not found: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.NOT_FOUND,
                "Resource Not Found",
                ex.getMessage(),
                MDC.get("correlationId")
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle invalid order state exceptions.
     *
     * @param ex the exception
     * @return 400 error response
     */
    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderState(InvalidOrderStateException ex) {
        logger.warn("Invalid order state: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "Invalid Order State",
                ex.getMessage(),
                MDC.get("correlationId")
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle illegal argument exceptions.
     *
     * @param ex the exception
     * @return 400 error response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("Illegal argument: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                ex.getMessage(),
                MDC.get("correlationId")
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
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        logger.warn("Constraint violation: {}", ex.getMessage());
        
        String violations = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                violations,
                MDC.get("correlationId"),
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
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        logger.warn("Method argument validation failed: {}", ex.getMessage());
        
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                "Invalid request body",
                MDC.get("correlationId"),
                fieldErrors.entrySet().stream()
                        .map(entry -> entry.getKey() + ": " + entry.getValue())
                        .collect(Collectors.toList())
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle optimistic locking failures (concurrent modifications).
     *
     * @param ex the exception
     * @return 409 error response
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        logger.warn("Optimistic locking failure: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT,
                "Conflict",
                "The resource was modified by another request. Please retry.",
                MDC.get("correlationId")
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
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        logger.warn("Data integrity violation: {}", ex.getMessage());
        
        String message = "Data integrity constraint violated";
        
        // Check for common constraint violations
        String exMessage = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (exMessage.contains("unique")) {
            message = "A resource with this value already exists";
        } else if (exMessage.contains("foreign key")) {
            message = "Referenced resource does not exist";
        }
        
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT,
                "Conflict",
                message,
                MDC.get("correlationId"),
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
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                MDC.get("correlationId")
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

