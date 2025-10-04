package com.ecommerce.order.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for order management service.
 * Returns RFC 7807 Problem Details for all exceptions.
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
     * Handle invalid order state exceptions.
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return 400 RFC 7807 Problem Detail response
     */
    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOrderState(InvalidOrderStateException ex, HttpServletRequest request) {
        logger.warn("Invalid order state: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://api.ecommerce.com/problems/invalid-order-state"));
        problem.setTitle("Invalid Order State");
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
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
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

