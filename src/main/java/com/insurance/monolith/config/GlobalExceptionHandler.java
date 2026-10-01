package com.insurance.monolith.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // =============================================================
    // ResponseStatusException
    // =============================================================

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(
            ResponseStatusException ex) {

        HttpStatus status = HttpStatus.resolve(
                ex.getStatusCode().value()
        );

        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        Map<String, Object> response = new HashMap<>();

        response.put("success", false);
        response.put(
                "message",
                ex.getReason() != null
                        ? ex.getReason()
                        : "Request failed"
        );
        response.put(
                "status",
                status.value()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }


    // =============================================================
    // Validation Errors
    // =============================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        for (FieldError fieldError :
                ex.getBindingResult().getFieldErrors()) {

            errors.put(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            );
        }

        Map<String, Object> response = new HashMap<>();

        response.put("success", false);
        response.put("message", "Validation failed");
        response.put("errorCode", "VALIDATION_ERROR");
        response.put("errors", errors);

        return ResponseEntity
                .badRequest()
                .body(response);
    }


    // =============================================================
    // Illegal Argument
    // =============================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        Map<String, Object> response = new HashMap<>();

        response.put("success", false);
        response.put("message", ex.getMessage());
        response.put("errorCode", "INVALID_ARGUMENT");

        return ResponseEntity
                .badRequest()
                .body(response);
    }


    // =============================================================
    // Generic Exception
    // =============================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(
            Exception ex) {

        log.error(
                "Unhandled application exception: {}",
                ex.getMessage(),
                ex
        );

        Map<String, Object> response = new HashMap<>();

        response.put("success", false);
        response.put("message", "Internal server error");
        response.put("errorCode", "INTERNAL_ERROR");

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}