package com.smartstudy.planning.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends RuntimeException {

    private final java.util.List<String> details;

    public ValidationException(String message, java.util.List<String> details) {
        super(message);
        this.details = details;
    }

    public java.util.List<String> getDetails() {
        return details;
    }

    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
