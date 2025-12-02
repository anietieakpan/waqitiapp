package com.waqiti.familyaccount.service.validation;

import com.waqiti.familyaccount.domain.FamilyAccount;
import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.exception.FamilyAccountException;
import com.waqiti.familyaccount.exception.UnauthorizedAccessException;
import com.waqiti.familyaccount.client.UserServiceClient;
import com.waqiti.familyaccount.client.KYCServiceClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

/**
 * Family Validation Service
 *
 * Handles all business validation logic for family accounts
 * Responsibilities:
 * - Parent eligibility validation
 * - User existence validation
 * - Permission validation
 * - Age-based validation
 * - Business rule validation
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyValidationService {

    private final UserServiceClient userServiceClient;
    private final KYCServiceClient kycServiceClient;

    // Age thresholds
    private static final int MINIMUM_PARENT_AGE = 18;
    private static final int MINIMUM_MEMBER_AGE = 0;
    private static final int CHILD_AGE_THRESHOLD = 13;
    private static final int TEEN_AGE_THRESHOLD = 18;

    /**
     * Validates if a user is eligible to be a primary parent
     *
     * @param userId User ID to validate
     * @throws FamilyAccountException if user is not eligible
     */
    public void validateParentEligibility(String userId) {
        log.debug("Validating parent eligibility for user: {}", userId);

        // Validate user exists
        if (!userServiceClient.userExists(userId)) {
            throw new FamilyAccountException("User does not exist: " + userId);
        }

        // Validate user is of legal age
        Integer userAge = userServiceClient.getUserAge(userId);
        if (userAge == null || userAge < MINIMUM_PARENT_AGE) {
            throw new FamilyAccountException("User must be at least " + MINIMUM_PARENT_AGE + " years old to create a family account");
        }

        // Validate user is eligible for family account (account in good standing)
        if (!userServiceClient.isUserEligibleForFamilyAccount(userId)) {
            throw new FamilyAccountException("User is not eligible for family account. Account may be suspended or restricted.");
        }

        // Additional checks can be added here (credit score, KYC level, etc.)
        log.debug("Parent eligibility validated successfully for user: {}", userId);
    }

    /**
     * Validates if a user exists
     *
     * @param userId User ID to validate
     * @throws FamilyAccountException if user does not exist
     */
    public void validateUserExists(String userId) {
        log.debug("Validating user exists: {}", userId);

        if (!userServiceClient.userExists(userId)) {
            throw new FamilyAccountException("User does not exist: " + userId);
        }

        log.debug("User exists: {}", userId);
    }

    /**
     * Validates if a user has parent permission for a family account
     *
     * @param familyAccount Family account to check
     * @param userId User ID to validate
     * @throws UnauthorizedAccessException if user is not a parent
     */
    public void validateParentPermission(FamilyAccount familyAccount, String userId) {
        log.debug("Validating parent permission for user: {} on family: {}", userId, familyAccount.getFamilyId());

        if (!familyAccount.isParent(userId)) {
            throw new UnauthorizedAccessException("User is not a parent of this family account");
        }

        log.debug("Parent permission validated for user: {}", userId);
    }

    /**
     * Validates if a user is a member of a family account
     *
     * @param familyAccount Family account to check
     * @param userId User ID to validate
     * @throws UnauthorizedAccessException if user is not a family member
     */
    public void validateFamilyMemberAccess(FamilyAccount familyAccount, String userId) {
        log.debug("Validating family member access for user: {} on family: {}", userId, familyAccount.getFamilyId());

        if (!familyAccount.isFamilyMember(userId)) {
            throw new UnauthorizedAccessException("User is not a member of this family account");
        }

        log.debug("Family member access validated for user: {}", userId);
    }

    /**
     * Validates member age eligibility
     *
     * @param dateOfBirth Date of birth of the member
     * @throws FamilyAccountException if age is invalid
     */
    public void validateMemberAge(LocalDate dateOfBirth) {
        log.debug("Validating member age with date of birth: {}", dateOfBirth);

        if (dateOfBirth == null) {
            throw new FamilyAccountException("Date of birth is required");
        }

        if (dateOfBirth.isAfter(LocalDate.now())) {
            throw new FamilyAccountException("Date of birth cannot be in the future");
        }

        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();

        if (age < MINIMUM_MEMBER_AGE) {
            throw new FamilyAccountException("Member must be at least " + MINIMUM_MEMBER_AGE + " years old");
        }

        log.debug("Member age validated: {} years old", age);
    }

    /**
     * Validates spending amount
     *
     * @param amount Amount to validate
     * @throws FamilyAccountException if amount is invalid
     */
    public void validateSpendingAmount(BigDecimal amount) {
        log.debug("Validating spending amount: {}", amount);

        if (amount == null) {
            throw new FamilyAccountException("Amount cannot be null");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FamilyAccountException("Amount must be greater than zero");
        }

        // Check maximum transaction amount (e.g., $10,000)
        BigDecimal maxTransactionAmount = new BigDecimal("10000.00");
        if (amount.compareTo(maxTransactionAmount) > 0) {
            throw new FamilyAccountException("Amount exceeds maximum transaction limit of " + maxTransactionAmount);
        }

        log.debug("Spending amount validated: {}", amount);
    }

    /**
     * Validates spending limit configuration
     *
     * @param dailyLimit Daily spending limit
     * @param weeklyLimit Weekly spending limit
     * @param monthlyLimit Monthly spending limit
     * @throws FamilyAccountException if limits are invalid
     */
    public void validateSpendingLimits(BigDecimal dailyLimit, BigDecimal weeklyLimit, BigDecimal monthlyLimit) {
        log.debug("Validating spending limits - daily: {}, weekly: {}, monthly: {}", dailyLimit, weeklyLimit, monthlyLimit);

        // All limits must be non-negative
        if (dailyLimit != null && dailyLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new FamilyAccountException("Daily limit cannot be negative");
        }

        if (weeklyLimit != null && weeklyLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new FamilyAccountException("Weekly limit cannot be negative");
        }

        if (monthlyLimit != null && monthlyLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new FamilyAccountException("Monthly limit cannot be negative");
        }

        // Logical validation: daily <= weekly <= monthly
        if (dailyLimit != null && weeklyLimit != null && dailyLimit.compareTo(weeklyLimit) > 0) {
            throw new FamilyAccountException("Daily limit cannot exceed weekly limit");
        }

        if (weeklyLimit != null && monthlyLimit != null && weeklyLimit.compareTo(monthlyLimit) > 0) {
            throw new FamilyAccountException("Weekly limit cannot exceed monthly limit");
        }

        if (dailyLimit != null && monthlyLimit != null && dailyLimit.multiply(new BigDecimal("31")).compareTo(monthlyLimit) > 0) {
            log.warn("Daily limit is high relative to monthly limit. Daily: {}, Monthly: {}", dailyLimit, monthlyLimit);
        }

        log.debug("Spending limits validated successfully");
    }

    /**
     * Validates member status allows operation
     *
     * @param member Family member to validate
     * @throws FamilyAccountException if member status doesn't allow operation
     */
    public void validateMemberStatus(FamilyMember member) {
        log.debug("Validating member status for member: {}", member.getUserId());

        if (member.getMemberStatus() == null) {
            throw new FamilyAccountException("Member status is not set");
        }

        if (member.getMemberStatus() == FamilyMember.MemberStatus.SUSPENDED) {
            throw new FamilyAccountException("Member account is suspended");
        }

        if (member.getMemberStatus() == FamilyMember.MemberStatus.REMOVED) {
            throw new FamilyAccountException("Member has been removed from the family account");
        }

        if (member.getMemberStatus() != FamilyMember.MemberStatus.ACTIVE) {
            throw new FamilyAccountException("Member account is not active");
        }

        log.debug("Member status validated: ACTIVE");
    }

    /**
     * Determines age group based on date of birth
     *
     * @param dateOfBirth Date of birth
     * @return Age group classification
     */
    public FamilyMember.AgeGroup determineAgeGroup(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return FamilyMember.AgeGroup.UNKNOWN;
        }

        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();

        if (age < CHILD_AGE_THRESHOLD) {
            return FamilyMember.AgeGroup.CHILD;
        } else if (age < TEEN_AGE_THRESHOLD) {
            return FamilyMember.AgeGroup.TEEN;
        } else {
            return FamilyMember.AgeGroup.ADULT;
        }
    }

    /**
     * Validates if operation is allowed based on member age
     *
     * @param member Family member
     * @param operation Operation to validate (e.g., "CRYPTO_TRADING", "INVESTMENT")
     * @return true if allowed, false otherwise
     */
    public boolean isOperationAllowedForAge(FamilyMember member, String operation) {
        FamilyMember.AgeGroup ageGroup = determineAgeGroup(member.getDateOfBirth());

        // Define age-based restrictions
        switch (operation) {
            case "CRYPTO_TRADING":
            case "INVESTMENT":
                return ageGroup == FamilyMember.AgeGroup.ADULT;

            case "INTERNATIONAL_TRANSACTIONS":
                return ageGroup == FamilyMember.AgeGroup.TEEN || ageGroup == FamilyMember.AgeGroup.ADULT;

            case "PEER_PAYMENTS":
                return ageGroup == FamilyMember.AgeGroup.TEEN || ageGroup == FamilyMember.AgeGroup.ADULT;

            case "ATM_WITHDRAWALS":
                return ageGroup == FamilyMember.AgeGroup.TEEN || ageGroup == FamilyMember.AgeGroup.ADULT;

            case "ONLINE_PURCHASES":
                return true; // Allowed for all age groups with appropriate limits

            default:
                return true; // Default allow for basic operations
        }
    }

    /**
     * Validates family name
     *
     * @param familyName Family name to validate
     * @throws FamilyAccountException if name is invalid
     */
    public void validateFamilyName(String familyName) {
        log.debug("Validating family name: {}", familyName);

        if (familyName == null || familyName.trim().isEmpty()) {
            throw new FamilyAccountException("Family name is required");
        }

        if (familyName.length() < 2) {
            throw new FamilyAccountException("Family name must be at least 2 characters");
        }

        if (familyName.length() > 100) {
            throw new FamilyAccountException("Family name must not exceed 100 characters");
        }

        // Check for inappropriate content (basic check)
        if (containsInappropriateContent(familyName)) {
            throw new FamilyAccountException("Family name contains inappropriate content");
        }

        log.debug("Family name validated: {}", familyName);
    }

    /**
     * Comprehensive inappropriate content check
     *
     * Checks for:
     * - Profanity and offensive language
     * - Hate speech indicators
     * - Inappropriate terms for a family banking context
     *
     * Note: This is a basic implementation using a blocked word list.
     * For enterprise production, consider integrating:
     * - AWS Comprehend Content Moderation
     * - Azure Content Moderator
     * - Google Cloud Natural Language API
     * - OpenAI Moderation API
     *
     * @param text The text to check
     * @return true if inappropriate content is detected, false otherwise
     */
    private boolean containsInappropriateContent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String lowerText = text.toLowerCase().trim();

        // Remove common separators that might be used to bypass filters
        String normalizedText = lowerText
            .replaceAll("[_\\-\\.@#\\$\\*\\s]+", "")
            .replaceAll("[0o]", "0")  // l33t speak normalization
            .replaceAll("[1i!]", "1")
            .replaceAll("[3e]", "3")
            .replaceAll("[4a@]", "4")
            .replaceAll("[5s\\$]", "5")
            .replaceAll("[7t]", "7");

        // Blocked word list (basic implementation)
        // In production, load this from a database or external configuration service
        Set<String> blockedWords = Set.of(
            // Profanity (partial list for demonstration)
            "profanity1", "profanity2", "profanity3", // Replace with actual terms

            // Hate speech indicators (generic patterns)
            "hate", "racist", "discrimination",

            // Financial scam indicators
            "scam", "fraud", "ponzi", "pyramid",

            // Inappropriate for family context
            "adult", "casino", "gambling", "betting",

            // Generic inappropriate terms
            "offensive", "inappropriate", "explicit",

            // Test words for validation (remove in production)
            "test-blocked", "inappropriate-test"
        );

        // Check for exact matches in blocked words
        for (String blockedWord : blockedWords) {
            if (normalizedText.contains(blockedWord)) {
                log.warn("Inappropriate content detected: blocked word found in text: {}",
                        text.substring(0, Math.min(20, text.length())) + "...");
                return true;
            }
        }

        // Check for suspicious patterns
        if (containsSuspiciousPatterns(lowerText)) {
            log.warn("Suspicious pattern detected in text: {}",
                    text.substring(0, Math.min(20, text.length())) + "...");
            return true;
        }

        // Check for excessive special characters (potential obfuscation)
        long specialCharCount = lowerText.chars()
            .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
            .count();

        if (specialCharCount > lowerText.length() * 0.3) {
            log.warn("Excessive special characters detected (possible obfuscation): {}",
                    text.substring(0, Math.min(20, text.length())) + "...");
            return true;
        }

        return false;
    }

    /**
     * Check for suspicious patterns that might indicate inappropriate content
     *
     * @param text Normalized lowercase text
     * @return true if suspicious patterns detected
     */
    private boolean containsSuspiciousPatterns(String text) {
        // Check for repeated characters (e.g., "aaaaaaa")
        if (text.matches(".*([a-z])\\1{5,}.*")) {
            return true;
        }

        // Check for common obfuscation patterns
        String[] suspiciousPatterns = {
            "f+u+c+k+",      // Elongated profanity
            "s+h+i+t+",      // Elongated profanity
            "d+a+m+n+",      // Elongated profanity
            "h+e+l+l+",      // Elongated words
            "xxx+",          // Adult content indicators
            "666",           // Potentially offensive numbers
        };

        for (String pattern : suspiciousPatterns) {
            if (text.matches(".*" + pattern + ".*")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Enhanced validation for production environments
     *
     * This method is a hook for integrating external content moderation services.
     * Uncomment and implement when ready to integrate with external APIs.
     *
     * @param text The text to validate
     * @return ContentModerationResult with detailed analysis
     */
    /*
    private ContentModerationResult validateWithExternalService(String text) {
        // Integration example with AWS Comprehend:
        // ComprehendClient comprehend = ComprehendClient.create();
        // DetectToxicContentRequest request = DetectToxicContentRequest.builder()
        //     .textSegments(TextSegment.builder().text(text).build())
        //     .languageCode(LanguageCode.EN)
        //     .build();
        // DetectToxicContentResponse response = comprehend.detectToxicContent(request);
        // return processComprehendResponse(response);

        return ContentModerationResult.builder()
            .clean(true)
            .confidenceScore(0.95)
            .build();
    }
    */
}
