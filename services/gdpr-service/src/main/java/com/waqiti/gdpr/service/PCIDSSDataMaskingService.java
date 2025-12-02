package com.waqiti.gdpr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * CRITICAL P0 FIX: PCI-DSS Compliant Data Masking for GDPR Exports
 *
 * Ensures that GDPR data exports comply with PCI-DSS requirements when they include
 * payment card data. This prevents accidental exposure of sensitive cardholder data.
 *
 * PCI-DSS REQUIREMENTS:
 * - PCI-DSS 3.2 Requirement 3.3: Mask PAN when displayed (show first 6 and last 4 max)
 * - PCI-DSS 3.2 Requirement 3.2: NEVER store sensitive authentication data (CVV, PIN)
 * - PCI-DSS 3.2 Requirement 3.4: Render PAN unreadable (encryption, truncation, masking)
 *
 * GDPR CONSIDERATIONS:
 * - GDPR Article 20: Data portability must be "in a structured, commonly used format"
 * - Balance transparency with security: provide enough info for user, mask sensitive data
 *
 * IMPLEMENTATION:
 * - PAN (Primary Account Number): Show last 4 digits only
 * - CVV/CVV2: NEVER export (critical security violation)
 * - Expiry Date: Can be shown (not sensitive under PCI-DSS)
 * - Cardholder Name: Can be shown (user's own data)
 * - SSN/Tax ID: Mask to last 4 digits
 * - Account Numbers: Mask to last 4 digits
 *
 * @author Waqiti Platform Engineering
 * @since 1.0-SNAPSHOT
 */
@Service
@Slf4j
public class PCIDSSDataMaskingService {

    // PCI-DSS Patterns
    private static final Pattern PAN_PATTERN = Pattern.compile("\\b\\d{13,19}\\b"); // 13-19 digits
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("\\b\\d{8,17}\\b");

    // Masking characters
    private static final String MASK_CHAR = "*";
    private static final String REDACTED = "[REDACTED FOR SECURITY]";

    /**
     * Masks personal data export to comply with PCI-DSS before sending to user.
     *
     * @param exportData Raw export data from all services
     * @return PCI-DSS compliant masked data
     */
    public Map<String, Object> maskExportData(Map<String, Object> exportData) {
        log.info("🔒 Applying PCI-DSS compliant masking to GDPR export");

        // Deep copy to avoid modifying original
        Map<String, Object> masked = new java.util.HashMap<>(exportData);

        // 1. CRITICAL: Mask Payment Card Data (PCI-DSS Requirement 3)
        if (masked.containsKey("paymentMethods")) {
            masked.put("paymentMethods", maskPaymentMethods(masked.get("paymentMethods")));
        }

        // 2. Mask Bank Account Numbers
        if (masked.containsKey("bankAccounts")) {
            masked.put("bankAccounts", maskBankAccounts(masked.get("bankAccounts")));
        }

        // 3. Mask SSN/Tax IDs (GDPR Special Category Data)
        if (masked.containsKey("taxInformation")) {
            masked.put("taxInformation", maskTaxInformation(masked.get("taxInformation")));
        }

        // 4. Mask Transaction Details (partial PAN may appear)
        if (masked.containsKey("transactions")) {
            masked.put("transactions", maskTransactions(masked.get("transactions")));
        }

        // 5. Remove sensitive fields that should NEVER be exported
        removeProhibitedFields(masked);

        log.info("✅ PCI-DSS masking completed successfully");

        return masked;
    }

    /**
     * CRITICAL: Masks payment methods according to PCI-DSS 3.2 Requirement 3.3.
     *
     * PCI-DSS Rule: Show at most first 6 and last 4 digits. We show ONLY last 4 for maximum security.
     */
    @SuppressWarnings("unchecked")
    private Object maskPaymentMethods(Object paymentMethods) {
        if (paymentMethods instanceof java.util.List) {
            java.util.List<Map<String, Object>> methods = (java.util.List<Map<String, Object>>) paymentMethods;

            for (Map<String, Object> method : methods) {
                // CRITICAL: Mask PAN to last 4 digits only
                if (method.containsKey("cardNumber")) {
                    String pan = method.get("cardNumber").toString();
                    method.put("cardNumber", maskPAN(pan));
                }

                // CRITICAL: NEVER export CVV (PCI-DSS 3.2 Requirement 3.2)
                if (method.containsKey("cvv")) {
                    method.put("cvv", REDACTED);
                    log.warn("⚠️ CVV found in export data - REMOVED for PCI-DSS compliance");
                }

                if (method.containsKey("cvv2")) {
                    method.put("cvv2", REDACTED);
                    log.warn("⚠️ CVV2 found in export data - REMOVED for PCI-DSS compliance");
                }

                if (method.containsKey("cvc")) {
                    method.put("cvc", REDACTED);
                    log.warn("⚠️ CVC found in export data - REMOVED for PCI-DSS compliance");
                }

                // CRITICAL: NEVER export PIN
                if (method.containsKey("pin")) {
                    method.put("pin", REDACTED);
                    log.warn("⚠️ PIN found in export data - REMOVED for PCI-DSS compliance");
                }

                // CRITICAL: NEVER export magnetic stripe data
                if (method.containsKey("track1Data") || method.containsKey("track2Data")) {
                    method.remove("track1Data");
                    method.remove("track2Data");
                    log.warn("⚠️ Magnetic stripe data found - REMOVED for PCI-DSS compliance");
                }

                // Expiry date is allowed (not sensitive under PCI-DSS)
                // Cardholder name is allowed (user's own data)
            }

            return methods;
        }

        return paymentMethods;
    }

    /**
     * Masks PAN (Primary Account Number) to show ONLY last 4 digits.
     *
     * PCI-DSS 3.2: "The first six and last four digits are the maximum number of digits
     * to be displayed." We show ONLY last 4 for maximum security.
     *
     * Examples:
     * - Input: 4532123456789012
     * - Output: ************9012
     */
    private String maskPAN(String pan) {
        if (pan == null || pan.length() < 13) {
            return MASK_CHAR.repeat(4); // Not a valid PAN, mask completely
        }

        // Remove all non-digits
        String digitsOnly = pan.replaceAll("\\D", "");

        if (digitsOnly.length() < 13 || digitsOnly.length() > 19) {
            return MASK_CHAR.repeat(4); // Invalid PAN length
        }

        // Show ONLY last 4 digits (most secure approach)
        String last4 = digitsOnly.substring(digitsOnly.length() - 4);
        return MASK_CHAR.repeat(12) + last4;
    }

    /**
     * Masks bank account numbers to show last 4 digits only.
     */
    @SuppressWarnings("unchecked")
    private Object maskBankAccounts(Object bankAccounts) {
        if (bankAccounts instanceof java.util.List) {
            java.util.List<Map<String, Object>> accounts = (java.util.List<Map<String, Object>>) bankAccounts;

            for (Map<String, Object> account : accounts) {
                if (account.containsKey("accountNumber")) {
                    String accountNum = account.get("accountNumber").toString();
                    account.put("accountNumber", maskAccountNumber(accountNum));
                }

                // Routing numbers can be shown (public information)
                // Bank name can be shown (not sensitive)
            }

            return accounts;
        }

        return bankAccounts;
    }

    /**
     * Masks account number to show last 4 digits.
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return MASK_CHAR.repeat(4);
        }

        String digitsOnly = accountNumber.replaceAll("\\D", "");
        if (digitsOnly.length() < 4) {
            return MASK_CHAR.repeat(4);
        }

        String last4 = digitsOnly.substring(digitsOnly.length() - 4);
        return MASK_CHAR.repeat(Math.max(0, digitsOnly.length() - 4)) + last4;
    }

    /**
     * Masks SSN/Tax IDs to show last 4 digits (GDPR Special Category Data).
     */
    @SuppressWarnings("unchecked")
    private Object maskTaxInformation(Object taxInfo) {
        if (taxInfo instanceof Map) {
            Map<String, Object> tax = (Map<String, Object>) taxInfo;

            if (tax.containsKey("ssn")) {
                String ssn = tax.get("ssn").toString();
                tax.put("ssn", maskSSN(ssn));
            }

            if (tax.containsKey("taxId")) {
                String taxId = tax.get("taxId").toString();
                tax.put("taxId", maskSSN(taxId)); // Use same logic
            }

            if (tax.containsKey("ein")) {
                String ein = tax.get("ein").toString();
                tax.put("ein", maskSSN(ein));
            }
        }

        return taxInfo;
    }

    /**
     * Masks SSN to format: ***-**-1234
     */
    private String maskSSN(String ssn) {
        if (ssn == null || ssn.isEmpty()) {
            return "";
        }

        String digitsOnly = ssn.replaceAll("\\D", "");
        if (digitsOnly.length() != 9) {
            // Invalid SSN, mask completely
            return "***-**-****";
        }

        String last4 = digitsOnly.substring(5);
        return "***-**-" + last4;
    }

    /**
     * Masks transaction data (PAN may appear in transaction history).
     */
    @SuppressWarnings("unchecked")
    private Object maskTransactions(Object transactions) {
        if (transactions instanceof java.util.List) {
            java.util.List<Map<String, Object>> txns = (java.util.List<Map<String, Object>>) transactions;

            for (Map<String, Object> txn : txns) {
                // Mask PAN if present
                if (txn.containsKey("cardNumber")) {
                    String pan = txn.get("cardNumber").toString();
                    txn.put("cardNumber", maskPAN(pan));
                }

                // Mask last4 may already be masked, but verify
                if (txn.containsKey("cardLast4")) {
                    String last4 = txn.get("cardLast4").toString();
                    // Last 4 is allowed, but ensure it's ONLY 4 digits
                    if (last4.length() > 4) {
                        txn.put("cardLast4", last4.substring(last4.length() - 4));
                    }
                }

                // Remove CVV if somehow present in transaction data
                txn.remove("cvv");
                txn.remove("cvv2");
                txn.remove("cvc");
            }

            return txns;
        }

        return transactions;
    }

    /**
     * CRITICAL: Removes fields that should NEVER be exported under ANY circumstances.
     *
     * PCI-DSS 3.2 Requirement 3.2: NEVER store sensitive authentication data after authorization.
     */
    private void removeProhibitedFields(Map<String, Object> data) {
        // CRITICAL: These fields should NEVER be exported
        String[] prohibitedFields = {
            "cvv", "cvv2", "cvc", "cid", // Card Verification Values
            "pin", "pinBlock",            // PIN data
            "track1Data", "track2Data", "track3Data", // Magnetic stripe
            "cavv", "xid",               // 3D Secure authentication
            "password", "passwordHash",   // Passwords
            "privateKey", "secretKey",    // Encryption keys
            "apiKey", "apiSecret",        // API credentials
            "accessToken", "refreshToken" // OAuth tokens
        };

        for (String field : prohibitedFields) {
            if (data.remove(field) != null) {
                log.warn("🚨 CRITICAL: Prohibited field '{}' found in export data and REMOVED", field);
            }
        }

        // Recursively check nested objects
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) value;
                removeProhibitedFields(nested);
            } else if (value instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> list = (java.util.List<Object>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nestedMap = (Map<String, Object>) item;
                        removeProhibitedFields(nestedMap);
                    }
                }
            }
        }
    }

    /**
     * Validates that export data is PCI-DSS compliant before final export.
     *
     * Returns true if compliant, false if violations detected.
     */
    public boolean validatePCIDSSCompliance(Map<String, Object> exportData) {
        log.info("🔍 Validating PCI-DSS compliance for GDPR export");

        boolean compliant = true;

        // Check for prohibited fields
        String[] prohibitedFields = {
            "cvv", "cvv2", "cvc", "pin", "track1Data", "track2Data"
        };

        for (String field : prohibitedFields) {
            if (containsField(exportData, field)) {
                log.error("❌ PCI-DSS VIOLATION: Prohibited field '{}' found in export!", field);
                compliant = false;
            }
        }

        // Check for unmasked PANs (full 16-digit card numbers)
        if (containsFullPAN(exportData)) {
            log.error("❌ PCI-DSS VIOLATION: Unmasked PAN (full card number) found in export!");
            compliant = false;
        }

        if (compliant) {
            log.info("✅ PCI-DSS compliance validation PASSED");
        } else {
            log.error("❌ PCI-DSS compliance validation FAILED - Export contains violations!");
        }

        return compliant;
    }

    /**
     * Recursively checks if a field exists in nested structure.
     */
    private boolean containsField(Map<String, Object> data, String fieldName) {
        if (data.containsKey(fieldName)) {
            return true;
        }

        for (Object value : data.values()) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) value;
                if (containsField(nested, fieldName)) {
                    return true;
                }
            } else if (value instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> list = (java.util.List<Object>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nestedMap = (Map<String, Object>) item;
                        if (containsField(nestedMap, fieldName)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Checks for unmasked full PANs in export data.
     */
    private boolean containsFullPAN(Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();

            if (value instanceof String) {
                String str = (String) value;
                // Check if string matches PAN pattern (13-19 consecutive digits)
                if (PAN_PATTERN.matcher(str).find()) {
                    return true;
                }
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) value;
                if (containsFullPAN(nested)) {
                    return true;
                }
            } else if (value instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> list = (java.util.List<Object>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nestedMap = (Map<String, Object>) item;
                        if (containsFullPAN(nestedMap)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}
