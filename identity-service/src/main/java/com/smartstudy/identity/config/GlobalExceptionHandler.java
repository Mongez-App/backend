package com.smartstudy.identity.config;

import com.smartstudy.shared.dto.ErrorResponse;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.exception.ConflictException;
import com.smartstudy.shared.exception.NotFoundException;
import com.smartstudy.shared.exception.UnauthorizedException;
import com.smartstudy.shared.logging.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        log.warn("BadRequestException: {} - {}", ex.getErrorCode(), ex.getMessage());
        return buildResponse(ex.getErrorCode(), ex.getMessage(), ex.getStatus());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("UnauthorizedException: {} - {}", ex.getErrorCode(), ex.getMessage());
        return buildResponse(ex.getErrorCode(), ex.getMessage(), ex.getStatus());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        log.warn("NotFoundException: {} - {}", ex.getErrorCode(), ex.getMessage());
        return buildResponse(ex.getErrorCode(), ex.getMessage(), ex.getStatus());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        log.warn("ConflictException: {} - {}", ex.getErrorCode(), ex.getMessage());
        return buildResponse(ex.getErrorCode(), ex.getMessage(), ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        log.warn("ValidationException: {}", message);
        return buildResponse("INVALID_REQUEST", message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("HttpMessageNotReadableException: Malformed request body.");
        return buildResponse("INVALID_REQUEST", "Malformed request body.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected exception: ", ex);
        return buildResponse("INTERNAL_SERVER_ERROR", "An unexpected error occurred.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> buildResponse(String errorCode, String message, HttpStatus status) {
        ErrorResponse errorResponse = new ErrorResponse(
                errorCode,
                message,
                Instant.now().toString()
        );
        return new ResponseEntity<>(errorResponse, status);
    }
}
