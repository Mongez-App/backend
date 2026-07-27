package com.smartstudy.planning.chat;

/**
 * Domain-specific runtime exception for chat/LLM failures.
 */
public class ChatException extends RuntimeException {

    private final String errorCode;

    public ChatException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ChatException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
