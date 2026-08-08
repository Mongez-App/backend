package com.smartstudy.identity.config;

import com.smartstudy.identity.controller.OrganizationAuthController;
import com.smartstudy.identity.dto.response.OrganizationAuthErrorResponse;
import com.smartstudy.shared.exception.BadRequestException;
import com.smartstudy.shared.exception.ConflictException;
import com.smartstudy.shared.exception.NotFoundException;
import com.smartstudy.shared.exception.UnauthorizedException;
import com.smartstudy.shared.logging.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;

@RestControllerAdvice(assignableTypes = OrganizationAuthController.class)
public class OrganizationAuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OrganizationAuthExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<OrganizationAuthErrorResponse> handleBadRequest(BadRequestException ex) {
        return buildResponse("ValidationError", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<OrganizationAuthErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return buildResponse("Unauthorized", ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<OrganizationAuthErrorResponse> handleNotFound(NotFoundException ex) {
        return buildResponse("NotFound", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<OrganizationAuthErrorResponse> handleConflict(ConflictException ex) {
        return buildResponse("Conflict", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<OrganizationAuthErrorResponse> handleForbidden(AccessDeniedException ex) {
        return buildResponse("Forbidden", ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OrganizationAuthErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error in organization auth: {}", ex.getMessage(), ex);
        return buildResponse("InternalServerError", "An unexpected error occurred.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<OrganizationAuthErrorResponse> buildResponse(String error, String message, HttpStatus status) {
        return new ResponseEntity<>(new OrganizationAuthErrorResponse(error, message), status);
    }
}
