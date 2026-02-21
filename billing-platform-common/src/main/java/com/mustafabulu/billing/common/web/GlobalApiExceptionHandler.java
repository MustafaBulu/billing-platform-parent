package com.mustafabulu.billing.common.web;

import com.mustafabulu.billing.common.exception.ApiErrorCode;
import com.mustafabulu.billing.common.exception.ConflictException;
import com.mustafabulu.billing.common.exception.DomainValidationException;
import com.mustafabulu.billing.common.exception.ResourceNotFoundException;
import com.mustafabulu.billing.common.exception.UpstreamServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_ERROR,
                "Request validation failed",
                request,
                details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_JSON,
                "Malformed JSON request body",
                request,
                Map.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String path = violation.getPropertyPath() == null ? "request" : violation.getPropertyPath().toString();
            details.put(path, violation.getMessage());
        }
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.CONSTRAINT_VIOLATION,
                "Request constraint validation failed",
                request,
                details);
    }

    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainValidation(DomainValidationException ex,
                                                                   HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.DOMAIN_VALIDATION_ERROR,
                ex.getMessage(),
                request,
                Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                           HttpServletRequest request) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.NOT_FOUND,
                ex.getMessage(),
                request,
                Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex,
                                                           HttpServletRequest request) {
        return buildResponse(
                HttpStatus.CONFLICT,
                ApiErrorCode.CONFLICT,
                ex.getMessage(),
                request,
                Map.of());
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleUpstreamService(UpstreamServiceException ex,
                                                                  HttpServletRequest request) {
        return buildResponse(
                HttpStatus.FAILED_DEPENDENCY,
                ApiErrorCode.FAILED_DEPENDENCY,
                ex.getMessage(),
                request,
                Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex,
                                                                 HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return buildResponse(
                status,
                "HTTP_" + status.value(),
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(),
                request,
                Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex,
                                                             HttpServletRequest request) {
        log.error("Unhandled exception for path {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.INTERNAL_ERROR,
                "Unexpected server error",
                request,
                Map.of());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status,
                                                           ApiErrorCode code,
                                                           String message,
                                                           HttpServletRequest request,
                                                           Map<String, String> details) {
        return buildResponse(status, code.value(), message, request, details);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status,
                                                           String code,
                                                           String message,
                                                           HttpServletRequest request,
                                                           Map<String, String> details) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                getRequestId(request),
                details
        );
        return ResponseEntity.status(status).body(response);
    }

    private String getRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTR);
        if (requestId != null) {
            return requestId.toString();
        }
        return request.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
    }
}
