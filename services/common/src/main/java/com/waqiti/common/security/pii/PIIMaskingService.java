package com.waqiti.common.security.pii;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * PII Masking Service - Facade for Logging
 *
 * Thin wrapper around ComprehensivePIIProtectionService specifically for logging use cases.
 * Provides simple masking API without requiring full PII context/classification.
 *
 * For full PII protection features (encryption, tokenization, GDPR compliance),
 * use ComprehensivePIIProtectionService directly.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PIIMaskingService {

    private final ComprehensivePIIProtectionService comprehensivePIIService;

    /**
     * Mask PII data in a string for logging purposes.
     * Uses masking mode from ComprehensivePIIProtectionService.
     *
     * @param input String potentially containing PII
     * @return Masked string with PII redacted
     */
    public String maskPII(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        try {
            // Create a simple logging context
            ComprehensivePIIProtectionService.PIIContext context =
                ComprehensivePIIProtectionService.PIIContext.builder()
                    .userId("SYSTEM")
                    .purpose("LOGGING")
                    .build();

            // Use the comprehensive service's protection (will use masking mode for logging)
            ComprehensivePIIProtectionService.PIIProtectionResult result =
                comprehensivePIIService.protectPII(input, context);

            return result.getProtectedData();

        } catch (Exception e) {
            // Fallback to basic masking if comprehensive service fails
            log.warn("ComprehensivePIIProtectionService failed, using fallback masking", e);
            return fallbackMask(input);
        }
    }

    /**
     * Mask user ID for audit logging purposes.
     *
     * Preserves first 4 and last 4 characters for debugging while masking middle.
     * For UUIDs and long IDs: shows prefix and suffix
     * For short IDs: shows only length indicator
     *
     * @param userId User identifier to mask
     * @return Masked user ID safe for logging
     */
    public String maskUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "[NULL_USER_ID]";
        }

        try {
            // For very short IDs, mask completely
            if (userId.length() <= 4) {
                return "****";
            }

            // For short IDs (5-8 chars), show first char only
            if (userId.length() <= 8) {
                return userId.charAt(0) + "****";
            }

            // For medium IDs (9-16 chars), show first 2 and last 2
            if (userId.length() <= 16) {
                return userId.substring(0, 2) + "****" + userId.substring(userId.length() - 2);
            }

            // For long IDs (UUID, etc), show first 4 and last 4
            return userId.substring(0, 4) + "****" + userId.substring(userId.length() - 4);

        } catch (Exception e) {
            log.error("Failed to mask user ID", e);
            return "****";
        }
    }

    /**
     * Check if a string contains PII patterns.
     * Basic heuristic check for common PII patterns.
     *
     * @param input String to check
     * @return true if PII patterns are detected
     */
    public boolean containsPII(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        // Check for common PII patterns
        return input.matches(".*\\b\\d{3}-\\d{2}-\\d{4}\\b.*") ||  // SSN
               input.matches(".*\\b\\d{16}\\b.*") ||                // Credit card
               input.matches(".*\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b.*") ||  // Email
               input.matches(".*\\b\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b.*") ||  // Phone
               input.toLowerCase().matches(".*(password|secret|token|apikey).*");  // Credentials
    }

    /**
     * Fallback masking if comprehensive service is unavailable.
     * Simple regex-based masking for critical PII patterns.
     */
    private String fallbackMask(String input) {
        String masked = input;

        // Mask SSN
        masked = masked.replaceAll("\\b\\d{3}-\\d{2}-\\d{4}\\b", "***-**-****");

        // Mask credit card numbers
        masked = masked.replaceAll("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b", "****-****-****-****");

        // Mask email addresses (keep domain)
        masked = masked.replaceAll("\\b([A-Za-z0-9._%+-]+)@", "****@");

        // Mask phone numbers
        masked = masked.replaceAll("\\b\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b", "***-***-****");

        // Mask passwords and tokens
        masked = java.util.regex.Pattern.compile(
            "(password|pwd|passwd|secret|token|apikey|api_key)\\s*[=:]\\s*[^\\s,;]+",
            java.util.regex.Pattern.CASE_INSENSITIVE
        ).matcher(masked).replaceAll("$1=****"
        );

        // Mask authorization headers
        masked = java.util.regex.Pattern.compile(
            "(Authorization|X-API-Key|X-Auth-Token|Bearer)\\s*[=:]?\\s*[^\\s,;]+",
            java.util.regex.Pattern.CASE_INSENSITIVE
        ).matcher(masked).replaceAll("$1: ****");

        return masked;
    }
}
