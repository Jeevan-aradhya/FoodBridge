package com.foodbridge.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Global error handling for /api/** controllers. Never leaks stack traces or internals. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.foodbridge.controller.api")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> rule(BusinessRuleException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> conflict(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Someone else changed this at the same time. Please reload and try again.", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to do this.", req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed.", req, fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> badInput(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "The request could not be understood.", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> other(Exception ex, HttpServletRequest req) {
        if (ex instanceof org.springframework.web.ErrorResponse er && er.getStatusCode().value() < 500) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return build(status, status.getReasonPhrase() + ".", req, null);
        }
        log.error("Unexpected API error on {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.", req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req,
                                           Map<String, String> fields) {
        ApiError body = new ApiError(LocalDateTime.now(), status.value(), status.getReasonPhrase(),
                message, req.getRequestURI(), fields);
        return ResponseEntity.status(status).body(body);
    }
}
