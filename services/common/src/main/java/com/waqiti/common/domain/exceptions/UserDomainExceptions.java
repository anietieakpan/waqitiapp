package com.waqiti.common.domain.exceptions;

import com.waqiti.common.domain.valueobjects.Email;
import com.waqiti.common.domain.valueobjects.PhoneNumber;
import com.waqiti.common.domain.valueobjects.UserId;

/**
 * User Domain Exceptions
 * Domain-specific exceptions for user management
 */
public class UserDomainExceptions {
    
    private static final String USER_DOMAIN = "USER";
    
    /**
     * User Not Found Exception
     */
    public static class UserNotFoundException extends DomainException {
        
        private final UserId userId;
        
        public UserNotFoundException(UserId userId) {
            super(String.format("User not found: %s", userId),
                    "USER_NOT_FOUND",
                    USER_DOMAIN);
            this.userId = userId;
        }
        
        public UserId getUserId() {
            return userId;
        }
    }
    
    /**
     * Duplicate User Exception
     */
    public static class DuplicateUserException extends DomainException {
        
        private final String field;
        private final String value;
        
        public DuplicateUserException(String field, String value) {
            super(String.format("User with %s '%s' already exists", field, value),
                    "DUPLICATE_USER",
                    USER_DOMAIN);
            this.field = field;
            this.value = value;
        }
        
        public static DuplicateUserException byEmail(Email email) {
            return new DuplicateUserException("email", email.getValue());
        }
        
        public static DuplicateUserException byPhoneNumber(PhoneNumber phoneNumber) {
            return new DuplicateUserException("phone number", phoneNumber.getValue());
        }
        
        public String getField() {
            return field;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * User Account Locked Exception
     */
    public static class UserAccountLockedException extends DomainException {
        
        private final UserId userId;
        private final int failedAttempts;
        private final java.time.Instant lockedUntil;
        
        public UserAccountLockedException(UserId userId, int failedAttempts, java.time.Instant lockedUntil) {
            super(String.format("User account %s is locked after %d failed attempts. Locked until: %s", 
                    userId, failedAttempts, lockedUntil),
                    "USER_ACCOUNT_LOCKED",
                    USER_DOMAIN);
            this.userId = userId;
            this.failedAttempts = failedAttempts;
            this.lockedUntil = lockedUntil;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public int getFailedAttempts() {
            return failedAttempts;
        }
        
        public java.time.Instant getLockedUntil() {
            return lockedUntil;
        }
    }
    
    /**
     * Invalid Credentials Exception
     */
    public static class InvalidCredentialsException extends DomainException {
        
        private final String username;
        private final int remainingAttempts;
        
        public InvalidCredentialsException(String username, int remainingAttempts) {
            super(String.format("Invalid credentials for user: %s. Remaining attempts: %d", 
                    username, remainingAttempts),
                    "INVALID_CREDENTIALS",
                    USER_DOMAIN);
            this.username = username;
            this.remainingAttempts = remainingAttempts;
        }
        
        public String getUsername() {
            return username;
        }
        
        public int getRemainingAttempts() {
            return remainingAttempts;
        }
    }
    
    /**
     * Email Not Verified Exception
     */
    public static class EmailNotVerifiedException extends DomainException {
        
        private final UserId userId;
        private final Email email;
        
        public EmailNotVerifiedException(UserId userId, Email email) {
            super(String.format("Email %s not verified for user %s", email, userId),
                    "EMAIL_NOT_VERIFIED",
                    USER_DOMAIN);
            this.userId = userId;
            this.email = email;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public Email getEmail() {
            return email;
        }
    }
    
    /**
     * Phone Not Verified Exception
     */
    public static class PhoneNotVerifiedException extends DomainException {
        
        private final UserId userId;
        private final PhoneNumber phoneNumber;
        
        public PhoneNotVerifiedException(UserId userId, PhoneNumber phoneNumber) {
            super(String.format("Phone number %s not verified for user %s", phoneNumber, userId),
                    "PHONE_NOT_VERIFIED",
                    USER_DOMAIN);
            this.userId = userId;
            this.phoneNumber = phoneNumber;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public PhoneNumber getPhoneNumber() {
            return phoneNumber;
        }
    }
    
    /**
     * KYC Not Completed Exception
     */
    public static class KycNotCompletedException extends DomainException {
        
        private final UserId userId;
        private final String requiredLevel;
        private final String currentLevel;
        
        public KycNotCompletedException(UserId userId, String requiredLevel, String currentLevel) {
            super(String.format("KYC not completed for user %s: required level %s, current level %s", 
                    userId, requiredLevel, currentLevel),
                    "KYC_NOT_COMPLETED",
                    USER_DOMAIN);
            this.userId = userId;
            this.requiredLevel = requiredLevel;
            this.currentLevel = currentLevel;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public String getRequiredLevel() {
            return requiredLevel;
        }
        
        public String getCurrentLevel() {
            return currentLevel;
        }
    }
    
    /**
     * User Suspended Exception
     */
    public static class UserSuspendedException extends DomainException {
        
        private final UserId userId;
        private final String reason;
        private final java.time.Instant suspendedAt;
        
        public UserSuspendedException(UserId userId, String reason, java.time.Instant suspendedAt) {
            super(String.format("User %s is suspended: %s", userId, reason),
                    "USER_SUSPENDED",
                    USER_DOMAIN);
            this.userId = userId;
            this.reason = reason;
            this.suspendedAt = suspendedAt;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public String getReason() {
            return reason;
        }
        
        public java.time.Instant getSuspendedAt() {
            return suspendedAt;
        }
    }
    
    /**
     * User Deleted Exception
     */
    public static class UserDeletedException extends DomainException {
        
        private final UserId userId;
        private final java.time.Instant deletedAt;
        
        public UserDeletedException(UserId userId, java.time.Instant deletedAt) {
            super(String.format("User %s was deleted at %s", userId, deletedAt),
                    "USER_DELETED",
                    USER_DOMAIN);
            this.userId = userId;
            this.deletedAt = deletedAt;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public java.time.Instant getDeletedAt() {
            return deletedAt;
        }
    }
    
    /**
     * Invalid Password Exception
     */
    public static class InvalidPasswordException extends DomainException {
        
        private final String reason;
        
        public InvalidPasswordException(String reason) {
            super(String.format("Invalid password: %s", reason),
                    "INVALID_PASSWORD",
                    USER_DOMAIN);
            this.reason = reason;
        }
        
        public String getReason() {
            return reason;
        }
    }
    
    /**
     * Password Expired Exception
     */
    public static class PasswordExpiredException extends DomainException {
        
        private final UserId userId;
        private final java.time.Instant expiredAt;
        
        public PasswordExpiredException(UserId userId, java.time.Instant expiredAt) {
            super(String.format("Password for user %s expired at %s", userId, expiredAt),
                    "PASSWORD_EXPIRED",
                    USER_DOMAIN);
            this.userId = userId;
            this.expiredAt = expiredAt;
        }
        
        public UserId getUserId() {
            return userId;
        }
        
        public java.time.Instant getExpiredAt() {
            return expiredAt;
        }
    }
    
    /**
     * Age Restriction Exception
     */
    public static class AgeRestrictionException extends DomainException {
        
        private final int userAge;
        private final int minimumAge;
        
        public AgeRestrictionException(int userAge, int minimumAge) {
            super(String.format("User age %d is below minimum required age %d", userAge, minimumAge),
                    "AGE_RESTRICTION",
                    USER_DOMAIN);
            this.userAge = userAge;
            this.minimumAge = minimumAge;
        }
        
        public int getUserAge() {
            return userAge;
        }
        
        public int getMinimumAge() {
            return minimumAge;
        }
    }
}