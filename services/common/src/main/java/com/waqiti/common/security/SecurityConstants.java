package com.waqiti.common.security;

/**
 * Security constants used across the application
 */
public final class SecurityConstants {
    
    // Token headers
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    
    // Token claims
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_AUTHORITIES = "authorities";
    public static final String CLAIM_MFA_VERIFIED = "mfa_verified";
    public static final String CLAIM_SESSION_ID = "session_id";
    public static final String CLAIM_DEVICE_ID = "device_id";
    
    // Token types
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    public static final String TOKEN_TYPE_MFA = "mfa";
    
    // Security headers
    public static final String HEADER_CSRF_TOKEN = "X-CSRF-TOKEN";
    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_CLIENT_IP = "X-Forwarded-For";
    public static final String HEADER_USER_AGENT = "User-Agent";
    
    // Security events
    public static final String EVENT_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String EVENT_LOGIN_FAILED = "LOGIN_FAILED";
    public static final String EVENT_LOGOUT = "LOGOUT";
    public static final String EVENT_TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String EVENT_TOKEN_REVOKED = "TOKEN_REVOKED";
    public static final String EVENT_MFA_REQUIRED = "MFA_REQUIRED";
    public static final String EVENT_MFA_SUCCESS = "MFA_SUCCESS";
    public static final String EVENT_MFA_FAILED = "MFA_FAILED";
    public static final String EVENT_SUSPICIOUS_ACTIVITY = "SUSPICIOUS_ACTIVITY";
    
    // Rate limiting
    public static final String RATE_LIMIT_LOGIN = "rate_limit:login:";
    public static final String RATE_LIMIT_MFA = "rate_limit:mfa:";
    public static final String RATE_LIMIT_TOKEN_REFRESH = "rate_limit:token_refresh:";
    
    // Session management
    public static final String SESSION_ACTIVE = "session:active:";
    public static final String SESSION_BLACKLIST = "session:blacklist:";
    
    // Default values
    public static final int DEFAULT_ACCESS_TOKEN_VALIDITY = 3600; // 1 hour
    public static final int DEFAULT_REFRESH_TOKEN_VALIDITY = 86400; // 24 hours
    public static final int DEFAULT_MFA_TOKEN_VALIDITY = 300; // 5 minutes
    public static final int DEFAULT_MAX_LOGIN_ATTEMPTS = 5;
    public static final int DEFAULT_LOCKOUT_DURATION = 900; // 15 minutes
    
    // Roles
    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    public static final String ROLE_SERVICE = "ROLE_SERVICE";
    
    private SecurityConstants() {
        // Prevent instantiation
    }
}