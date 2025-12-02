package com.waqiti.common.validation.service;

import com.waqiti.common.validation.model.EmailValidationResult;

/**
 * Enterprise-grade Email Verification Service interface.
 * Provides comprehensive email verification capabilities including syntax validation,
 * domain verification, MX record checking, and disposable email detection.
 */
public interface EmailVerificationService {
    
    /**
     * Perform comprehensive email verification
     * 
     * @param email The email address to verify
     * @return EmailValidationResult with detailed verification results
     */
    EmailValidationResult verifyEmail(String email);
    
    /**
     * Check if an email address has valid syntax
     * 
     * @param email The email address to validate
     * @return true if the email syntax is valid
     */
    boolean isValidSyntax(String email);
    
    /**
     * Check if the email domain has valid MX records
     * 
     * @param email The email address to check
     * @return true if the domain has valid MX records
     */
    boolean hasMXRecords(String email);
    
    /**
     * Check if the email is from a disposable email provider
     * 
     * @param email The email address to check
     * @return true if the email is from a disposable provider
     */
    boolean isDisposable(String email);
    
    /**
     * Check if the email domain exists
     * 
     * @param email The email address to check
     * @return true if the domain exists
     */
    boolean domainExists(String email);
    
    /**
     * Get the reputation score for an email domain
     * 
     * @param email The email address to check
     * @return reputation score between 0.0 (bad) and 1.0 (good)
     */
    double getDomainReputation(String email);
    
    /**
     * Check if the email is on a blacklist
     * 
     * @param email The email address to check
     * @return true if the email is blacklisted
     */
    boolean isBlacklisted(String email);
    
    /**
     * Check if the email belongs to a role account (admin@, info@, etc.)
     * 
     * @param email The email address to check
     * @return true if it's a role account
     */
    boolean isRoleAccount(String email);
    
    /**
     * Check if the email is from a free email provider
     * 
     * @param email The email address to check
     * @return true if it's from a free provider
     */
    boolean isFreeProvider(String email);
    
    /**
     * Check if the email is from a corporate domain
     * 
     * @param email The email address to check
     * @return true if it's from a corporate domain
     */
    boolean isCorporate(String email);
    
    /**
     * Perform SMTP verification (if enabled)
     * 
     * @param email The email address to verify
     * @return true if SMTP verification passes
     */
    boolean verifyViaSMTP(String email);
    
    /**
     * Get risk score for an email address
     * 
     * @param email The email address to assess
     * @return risk score between 0.0 (low risk) and 1.0 (high risk)
     */
    double getRiskScore(String email);
}