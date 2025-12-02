package com.waqiti.user.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Centralized exception definitions for user domain
 * Follows domain-driven design principles
 */
public class UserExceptions {

    /**
     * Base exception for all user-related errors
     */
    public static class UserDomainException extends RuntimeException {
        private final String errorCode;

        public UserDomainException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public UserDomainException(String message, String errorCode, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    /**
     * User not found exception
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class UserNotFoundException extends UserDomainException {
        public UserNotFoundException(String userId) {
            super("User not found with ID: " + userId, "USER_NOT_FOUND");
        }

        public UserNotFoundException(String field, String value) {
            super(String.format("User not found with %s: %s", field, value), "USER_NOT_FOUND");
        }
    }

    /**
     * User already exists exception
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class UserAlreadyExistsException extends UserDomainException {
        public UserAlreadyExistsException(String email) {
            super("User already exists with email: " + email, "USER_ALREADY_EXISTS");
        }
    }

    /**
     * Invalid user state exception
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InvalidUserStateException extends UserDomainException {
        public InvalidUserStateException(String message) {
            super(message, "INVALID_USER_STATE");
        }

        public InvalidUserStateException(String currentState, String attemptedAction) {
            super(String.format("Cannot perform action '%s' on user in state '%s'",
                  attemptedAction, currentState), "INVALID_USER_STATE");
        }
    }

    /**
     * User account locked exception
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class UserAccountLockedException extends UserDomainException {
        public UserAccountLockedException(String userId, String reason) {
            super(String.format("User account %s is locked. Reason: %s", userId, reason),
                  "USER_ACCOUNT_LOCKED");
        }
    }

    /**
     * User account suspended exception
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class UserAccountSuspendedException extends UserDomainException {
        public UserAccountSuspendedException(String userId, String reason) {
            super(String.format("User account %s is suspended. Reason: %s", userId, reason),
                  "USER_ACCOUNT_SUSPENDED");
        }
    }

    /**
     * User account closed/terminated exception
     */
    @ResponseStatus(HttpStatus.GONE)
    public static class UserAccountClosedException extends UserDomainException {
        public UserAccountClosedException(String userId) {
            super("User account " + userId + " has been permanently closed",
                  "USER_ACCOUNT_CLOSED");
        }
    }

    /**
     * Email verification required exception
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class EmailVerificationRequiredException extends UserDomainException {
        public EmailVerificationRequiredException(String userId) {
            super("Email verification required for user: " + userId,
                  "EMAIL_VERIFICATION_REQUIRED");
        }
    }

    /**
     * Phone verification required exception
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class PhoneVerificationRequiredException extends UserDomainException {
        public PhoneVerificationRequiredException(String userId) {
            super("Phone verification required for user: " + userId,
                  "PHONE_VERIFICATION_REQUIRED");
        }
    }

    /**
     * KYC verification required exception
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class KYCVerificationRequiredException extends UserDomainException {
        public KYCVerificationRequiredException(String userId, String kycLevel) {
            super(String.format("KYC verification level '%s' required for user: %s",
                  kycLevel, userId), "KYC_VERIFICATION_REQUIRED");
        }
    }

    /**
     * Invalid verification token exception
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InvalidVerificationTokenException extends UserDomainException {
        public InvalidVerificationTokenException(String tokenType) {
            super("Invalid or expired " + tokenType + " verification token",
                  "INVALID_VERIFICATION_TOKEN");
        }
    }

    /**
     * Password validation failed exception
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class PasswordValidationException extends UserDomainException {
        public PasswordValidationException(String reason) {
            super("Password validation failed: " + reason, "PASSWORD_VALIDATION_FAILED");
        }
    }

    /**
     * Invalid credentials exception
     */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class InvalidCredentialsException extends UserDomainException {
        public InvalidCredentialsException() {
            super("Invalid username or password", "INVALID_CREDENTIALS");
        }
    }

    /**
     * Account creation failed exception
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public static class AccountCreationFailedException extends UserDomainException {
        public AccountCreationFailedException(String reason, Throwable cause) {
            super("Failed to create user account: " + reason,
                  "ACCOUNT_CREATION_FAILED", cause);
        }
    }

    /**
     * Profile update failed exception
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public static class ProfileUpdateFailedException extends UserDomainException {
        public ProfileUpdateFailedException(String userId, Throwable cause) {
            super("Failed to update profile for user: " + userId,
                  "PROFILE_UPDATE_FAILED", cause);
        }
    }

    /**
     * Duplicate email exception
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicateEmailException extends UserDomainException {
        public DuplicateEmailException(String email) {
            super("Email address already in use: " + email, "DUPLICATE_EMAIL");
        }
    }

    /**
     * Duplicate phone number exception
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicatePhoneException extends UserDomainException {
        public DuplicatePhoneException(String phone) {
            super("Phone number already in use: " + phone, "DUPLICATE_PHONE");
        }
    }

    /**
     * Invalid input exception
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InvalidUserInputException extends UserDomainException {
        public InvalidUserInputException(String fieldName, String reason) {
            super(String.format("Invalid %s: %s", fieldName, reason), "INVALID_INPUT");
        }
    }

    /**
     * Session expired exception
     */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class SessionExpiredException extends UserDomainException {
        public SessionExpiredException() {
            super("User session has expired. Please login again", "SESSION_EXPIRED");
        }
    }

    /**
     * Too many login attempts exception
     */
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public static class TooManyLoginAttemptsException extends UserDomainException {
        public TooManyLoginAttemptsException(int attempts, long lockoutMinutes) {
            super(String.format("Too many failed login attempts (%d). Account locked for %d minutes",
                  attempts, lockoutMinutes), "TOO_MANY_LOGIN_ATTEMPTS");
        }
    }

    /**
     * User role assignment failed exception
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class RoleAssignmentException extends UserDomainException {
        public RoleAssignmentException(String userId, String roleName, String reason) {
            super(String.format("Failed to assign role '%s' to user %s: %s",
                  roleName, userId, reason), "ROLE_ASSIGNMENT_FAILED");
        }
    }

    /**
     * Insufficient permissions exception
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class InsufficientPermissionsException extends UserDomainException {
        public InsufficientPermissionsException(String action) {
            super("Insufficient permissions to perform action: " + action,
                  "INSUFFICIENT_PERMISSIONS");
        }
    }

    /**
     * User service unavailable exception
     */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public static class UserServiceUnavailableException extends UserDomainException {
        public UserServiceUnavailableException(String reason, Throwable cause) {
            super("User service temporarily unavailable: " + reason,
                  "SERVICE_UNAVAILABLE", cause);
        }
    }
}
