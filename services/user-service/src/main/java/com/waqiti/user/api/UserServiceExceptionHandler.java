package com.waqiti.user.api;

import com.waqiti.common.exception.ErrorResponse;
import com.waqiti.common.exception.GlobalExceptionHandler;
import com.waqiti.common.kyc.exception.KycVerificationFailedException;
import com.waqiti.user.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * User service specific exception handler extending the common global handler
 */
@RestControllerAdvice
@Slf4j
public class UserServiceExceptionHandler extends GlobalExceptionHandler {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UserServiceExceptionHandler.class);

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex, WebRequest request) {
        logger.error("User not found", ex);
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException ex, WebRequest request) {
        logger.error("User already exists", ex);
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailed(AuthenticationFailedException ex, WebRequest request) {
        logger.error("Authentication failed", ex);
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        logger.error("Bad credentials", ex);
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password", request);
    }

    @ExceptionHandler(InvalidVerificationTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVerificationToken(InvalidVerificationTokenException ex, WebRequest request) {
        logger.error("Invalid verification token", ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidUserStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUserState(InvalidUserStateException ex, WebRequest request) {
        logger.error("Invalid user state", ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(KycVerificationFailedException.class)
    public ResponseEntity<ErrorResponse> handleKycVerificationFailed(KycVerificationFailedException ex, WebRequest request) {
        logger.error("KYC verification failed", ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }
}