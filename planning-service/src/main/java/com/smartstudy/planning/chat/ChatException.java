package com.smartstudy.planning.chat;

import org.springframework.http.HttpStatus;

/**
 * Domain-specific runtime exception for chat/LLM failures.
 * Carries specific error codes, HTTP status, and provider metadata.
 */
public class ChatException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final String provider;

    public ChatException(String errorCode, String message) {
        this(errorCode, message, HttpStatus.INTERNAL_SERVER_ERROR, "Gemini", null);
    }

    public ChatException(String errorCode, String message, HttpStatus httpStatus) {
        this(errorCode, message, httpStatus, "Gemini", null);
    }

    public ChatException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        this(errorCode, message, httpStatus, "Gemini", cause);
    }

    public ChatException(String errorCode, String message, HttpStatus httpStatus, String provider, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus != null ? httpStatus : HttpStatus.INTERNAL_SERVER_ERROR;
        this.provider = provider;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getProvider() {
        return provider;
    }
}
