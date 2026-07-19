package com.smartstudy.shared.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends RuntimeException {

    private final String errorCode;

    public NotFoundException(String message) {
        super(message);
        this.errorCode = "NOT_FOUND";
    }

    public NotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
