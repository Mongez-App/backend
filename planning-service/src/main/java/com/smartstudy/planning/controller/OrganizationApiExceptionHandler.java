package com.smartstudy.planning.controller;

import com.smartstudy.planning.exception.OrgApiException;
import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Renders errors of the organization-admin module in the contract's
 * {"error": ..., "message": ...} shape. Scoped to OrganizationAdminController
 * so the rest of the service keeps its existing error format.
 */
@RestControllerAdvice(assignableTypes = OrganizationAdminController.class)
public class OrganizationApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OrganizationApiExceptionHandler.class);

    public record OrgErrorResponse(String error, String message) {
    }

    @ExceptionHandler(OrgApiException.class)
    public ResponseEntity<OrgErrorResponse> handleOrgApi(OrgApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new OrgErrorResponse(ex.getError(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<OrgErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed.");
        return ResponseEntity.badRequest().body(new OrgErrorResponse("ValidationError", message));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<OrgErrorResponse> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(new OrgErrorResponse("ValidationError", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<OrgErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new OrgErrorResponse("ValidationError", "Request body is missing or malformed."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<OrgErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new OrgErrorResponse("PayloadTooLarge", "File exceeds the maximum allowed size."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OrgErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error in organization API: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new OrgErrorResponse("InternalServerError",
                        "An unexpected error occurred. Please try again."));
    }
}
