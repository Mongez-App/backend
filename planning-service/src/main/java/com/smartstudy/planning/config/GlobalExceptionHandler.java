package com.smartstudy.planning.config;

import com.smartstudy.planning.dto.response.ApiErrorResponse;
import com.smartstudy.planning.exception.ValidationException;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.exception.ConflictException;
import com.smartstudy.shared.exception.NotFoundException;
import com.smartstudy.shared.exception.UnauthorizedException;
import com.smartstudy.shared.logging.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        log.warn("ResponseStatusException: {} - {}", String.valueOf(ex.getStatusCode().value()), ex.getReason());
        ApiErrorResponse error = new ApiErrorResponse(
                false,
                String.valueOf(ex.getStatusCode().value()),
                ex.getReason(),
                null
        );
        return new ResponseEntity<>(error, ex.getStatusCode());
    }


    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("MissingRequestHeaderException: {}", ex.getHeaderName());
        ApiErrorResponse error = new ApiErrorResponse(
                false,
                "Bad Request",
                ex.getHeaderName() + " header is required.",
                null
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("UnauthorizedException: {}", ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(
                false,
                "Unauthorized Access",
                ex.getMessage(),
                null
        );
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex) {
        log.warn("BadRequestException: {} - {}", ex.getErrorCode(), ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(
                false,
                "Bad Request",
                ex.getMessage(),
                null
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex) {
        log.warn("ConflictException: {} - {}", ex.getErrorCode(), ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(
                false,
                "Conflict",
                ex.getMessage(),
                null
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex) {
        log.warn("NotFoundException: {} - {}", ex.getErrorCode(), ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(
                false,
                "Not Found",
                ex.getMessage(),
                null
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ValidationException ex) {
        log.warn("ValidationException: {}", ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(
                false,
                "Validation Failed",
                ex.getMessage(),
                ex.getDetails()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        log.warn("ValidationException: {}", message);
        ApiErrorResponse error = new ApiErrorResponse(
                false,
                "Validation Failed",
                message,
                ex.getBindingResult().getFieldErrors().stream()
                        .map(err -> err.getField() + ": " + err.getDefaultMessage())
                        .toList()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        String detailMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        log.warn("HttpMessageNotReadableException: {}", detailMessage);
        ApiErrorResponse error = new ApiErrorResponse(
                false,
                "Validation Failed",
                "Malformed request body: " + (detailMessage != null ? detailMessage : "Invalid JSON format"),
                java.util.List.of(detailMessage != null ? detailMessage : "Malformed request body.")
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected exception: ", ex);
        ApiErrorResponse error = new ApiErrorResponse(
                false,
                "Internal Server Error",
                "An unexpected error occurred.",
                null
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
