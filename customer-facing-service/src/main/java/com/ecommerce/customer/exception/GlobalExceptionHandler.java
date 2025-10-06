package com.ecommerce.customer.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for customer-facing service.
 * Returns RFC 7807 Problem Details for all exceptions.
 *
 * <p>Handles common exceptions and maps them to appropriate HTTP status codes:</p>
 * <ul>
 *   <li>ResourceNotFoundException → 404 Not Found</li>
 *   <li>IllegalArgumentException → 400 Bad Request</li>
 *   <li>IllegalStateException → 400 Bad Request</li>
 *   <li>ConstraintViolationException → 400 Bad Request</li>
 *   <li>MethodArgumentNotValidException → 400 Bad Request</li>
 *   <li>OptimisticLockException → 409 Conflict</li>
 *   <li>DataIntegrityViolationException → 409 Conflict</li>
 *   <li>InsufficientInventoryException → 409 Conflict</li>
 *   <li>Exception → 500 Internal Server Error</li>
 * </ul>
 *
 * <p>All error responses include correlation ID for distributed tracing.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_ID_KEY = "correlationId";

    /**
     * Handle resource not found exceptions.
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 404 RFC 7807 Problem Detail response
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        logger.warn("Resource not found: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://api.ecommerce.com/problems/resource-not-found"));
        problem.setTitle("Resource Not Found");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    /**
     * Handle missing request header exceptions (e.g., missing Idempotency-Key).
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 400 RFC 7807 Problem Detail response
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        logger.warn("Missing required header: {}", ex.getHeaderName());

        String detail = String.format("Missing required header '%s'", ex.getHeaderName());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create("https://api.ecommerce.com/problems/bad-request"));
        problem.setTitle("Bad Request");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Handle illegal argument exceptions.
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 400 RFC 7807 Problem Detail response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        logger.warn("Illegal argument: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://api.ecommerce.com/problems/bad-request"));
        problem.setTitle("Bad Request");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Handle illegal state exceptions.
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 400 RFC 7807 Problem Detail response
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        logger.warn("Illegal state: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://api.ecommerce.com/problems/bad-request"));
        problem.setTitle("Bad Request");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Handle constraint violation exceptions (Bean Validation).
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 400 RFC 7807 Problem Detail response with validation errors
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        logger.warn("Constraint violation: {}", ex.getMessage());

        String violations = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, violations);
        problem.setType(URI.create("https://api.ecommerce.com/problems/validation-failed"));
        problem.setTitle("Validation Failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Handle method argument not valid exceptions (request body validation).
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 400 RFC 7807 Problem Detail response with field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        logger.warn("Method argument validation failed: {}", ex.getMessage());

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request body");
        problem.setType(URI.create("https://api.ecommerce.com/problems/validation-failed"));
        problem.setTitle("Validation Failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));
        problem.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Handle optimistic locking failures (concurrent modifications).
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 409 RFC 7807 Problem Detail response
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(OptimisticLockingFailureException ex, HttpServletRequest request) {
        logger.warn("Optimistic locking failure: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The resource was modified by another request. Please retry.");
        problem.setType(URI.create("https://api.ecommerce.com/problems/conflict"));
        problem.setTitle("Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * Handle data integrity violation exceptions (duplicate keys, foreign key violations).
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 409 RFC 7807 Problem Detail response
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        logger.warn("Data integrity violation: {}", ex.getMessage());

        String message = "Data integrity constraint violated";

        // Check for common constraint violations
        String exMessage = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (exMessage.contains("unique")) {
            message = "A resource with this value already exists";
        } else if (exMessage.contains("foreign key")) {
            message = "Referenced resource does not exist";
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
        problem.setType(URI.create("https://api.ecommerce.com/problems/conflict"));
        problem.setTitle("Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * Handle insufficient inventory exceptions.
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 409 RFC 7807 Problem Detail response
     */
    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientInventory(InsufficientInventoryException ex, HttpServletRequest request) {
        logger.warn("Insufficient inventory: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://api.ecommerce.com/problems/insufficient-inventory"));
        problem.setTitle("Insufficient Inventory");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * Handle duplicate resource exceptions.
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 409 RFC 7807 Problem Detail response
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateResource(DuplicateResourceException ex, HttpServletRequest request) {
        logger.warn("Duplicate resource: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://api.ecommerce.com/problems/duplicate-resource"));
        problem.setTitle("Duplicate Resource");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * Handle method argument type mismatch exceptions (e.g., invalid UUID format).
     * Returns 404 if the target type is UUID (path variable), 400 otherwise.
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 404 or 400 RFC 7807 Problem Detail response
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        logger.warn("Method argument type mismatch: {} - expected type: {}", ex.getValue(), ex.getRequiredType());

        // If the target type is UUID and it's a path variable, treat as "resource not found" (404)
        // This handles cases like /api/v1/categories/invalid-uuid
        if (ex.getRequiredType() != null && UUID.class.isAssignableFrom(ex.getRequiredType())) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                    String.format("Resource not found with id: %s", ex.getValue()));
            problem.setType(URI.create("https://api.ecommerce.com/problems/resource-not-found"));
            problem.setTitle("Resource Not Found");
            problem.setInstance(URI.create(request.getRequestURI()));
            problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        }

        // For other type mismatches, return 400 Bad Request
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName()));
        problem.setType(URI.create("https://api.ecommerce.com/problems/bad-request"));
        problem.setTitle("Bad Request");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Handle all other exceptions.
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 500 RFC 7807 Problem Detail response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        logger.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
        problem.setType(URI.create("https://api.ecommerce.com/problems/internal-server-error"));
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}

