package com.nexswitch.acquiring.rest;

import com.nexswitch.acquiring.rest.dto.ApiError;
import com.nexswitch.acquiring.rest.dto.Violation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

// LEARN: @RestControllerAdvice — a @ControllerAdvice that applies globally to all @RestController
//        classes. @ExceptionHandler methods here intercept exceptions thrown anywhere in the
//        web layer (controllers + service calls they invoke), so validation errors never
//        reach the caller as a raw 500 — they're translated to structured 400 responses.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<Violation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new Violation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        log.debug("rest.validation_failed violations={}", violations.size());
        return ResponseEntity.badRequest()
                .body(new ApiError(400, "Validation Failed", null, violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("rest.malformed_json message={}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ApiError(400, "Bad Request", "Malformed or missing JSON body", null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("rest.illegal_argument message={}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ApiError(400, "Bad Request", ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("rest.unhandled_exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(500, "Internal Server Error", "An unexpected error occurred", null));
    }
}
