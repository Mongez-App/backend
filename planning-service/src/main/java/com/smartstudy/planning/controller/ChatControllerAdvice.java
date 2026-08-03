package com.smartstudy.planning.controller;

import com.smartstudy.planning.chat.ChatException;
import com.smartstudy.planning.dto.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Controller advice to map chat exceptions to standard HTTP error responses according to the API contract.
 */
@RestControllerAdvice(assignableTypes = ChatController.class)
public class ChatControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(ChatControllerAdvice.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("Chat HTTP error: status={} | reason={}", ex.getStatusCode(), ex.getReason());
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getStatusCode().toString(),
                ex.getReason() != null ? ex.getReason() : ex.getMessage(),
                Instant.now().toString()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Chat validation failed: {}", ex.getMessage());
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("Message must not be empty");

        ErrorResponse errorResponse = new ErrorResponse(
                "BAD_REQUEST",
                message,
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<ErrorResponse> handleChatException(ChatException ex) {
        log.error("AI Chat error: errorCode={} | status={} | message={}",
                ex.getErrorCode(), ex.getHttpStatus(), ex.getMessage(), ex);
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getProvider(),
                Instant.now().toString()
        );
        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }
}
