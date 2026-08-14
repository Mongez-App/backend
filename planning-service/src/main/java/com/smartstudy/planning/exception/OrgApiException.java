package com.smartstudy.planning.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for the organization-admin API module. Rendered by
 * OrganizationApiExceptionHandler as {"error": ..., "message": ...} per the
 * org API contract.
 */
public class OrgApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    public OrgApiException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public static OrgApiException validation(String message) {
        return new OrgApiException(HttpStatus.BAD_REQUEST, "ValidationError", message);
    }

    public static OrgApiException unauthorized(String message) {
        return new OrgApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", message);
    }

    public static OrgApiException forbidden(String message) {
        return new OrgApiException(HttpStatus.FORBIDDEN, "Forbidden", message);
    }

    public static OrgApiException notFound(String message) {
        return new OrgApiException(HttpStatus.NOT_FOUND, "NotFound", message);
    }

    public static OrgApiException conflict(String message) {
        return new OrgApiException(HttpStatus.CONFLICT, "Conflict", message);
    }

    public static OrgApiException payloadTooLarge(String message) {
        return new OrgApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PayloadTooLarge", message);
    }

    public static OrgApiException unsupportedMediaType(String message) {
        return new OrgApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UnsupportedMediaType", message);
    }

    public static OrgApiException unprocessable(String message) {
        return new OrgApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UnprocessableEntity", message);
    }
}
