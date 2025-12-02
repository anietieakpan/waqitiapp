package com.waqiti.common.security.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Field-level encryption service for PCI/PII data
 *
 * Provides convenient methods for encrypting specific field types:
 * - Credit card numbers (PAN)
 * - Social Security Numbers (SSN)
 * - Bank account numbers
 * - Personal identification numbers
 *
 * Each field type uses appropriate encryption context for additional security
 *
 * @author Waqiti Security Team
 */
@Slf4j
@Service
public class FieldEncryptionService {

    private final KmsEncryptionService kmsEncryptionService;

    public FieldEncryptionService(KmsEncryptionService kmsEncryptionService) {
        this.kmsEncryptionService = kmsEncryptionService;
    }

    /**
     * Encrypt Primary Account Number (PAN) - PCI DSS Requirement 3.5
     */
    public String encryptPAN(String pan, String userId, String merchantId) {
        if (pan == null || pan.isEmpty()) {
            return null;
        }

        Map<String, String> context = new HashMap<>();
        context.put("dataType", "PAN");
        context.put("userId", userId);
        if (merchantId != null) {
            context.put("merchantId", merchantId);
        }
        context.put("timestamp", String.valueOf(System.currentTimeMillis()));

        return kmsEncryptionService.encrypt(pan, context);
    }

    /**
     * Decrypt PAN
     */
    public String decryptPAN(String encryptedPAN, String userId, String merchantId) {
        if (encryptedPAN == null || encryptedPAN.isEmpty()) {
            return null;
        }

        Map<String, String> context = new HashMap<>();
        context.put("dataType", "PAN");
        context.put("userId", userId);
        if (merchantId != null) {
            context.put("merchantId", merchantId);
        }
        // Note: timestamp not needed for decryption

        return kmsEncryptionService.decrypt(encryptedPAN, context);
    }

    /**
     * Encrypt Social Security Number (SSN)
     */
    public String encryptSSN(String ssn, String userId) {
        if (ssn == null || ssn.isEmpty()) {
            return null;
        }

        Map<String, String> context = new HashMap<>();
        context.put("dataType", "SSN");
        context.put("userId", userId);
        context.put("timestamp", String.valueOf(System.currentTimeMillis()));

        return kmsEncryptionService.encrypt(ssn, context);
    }

    /**
     * Decrypt SSN
     */
    public String decryptSSN(String encryptedSSN, String userId) {
        if (encryptedSSN == null || encryptedSSN.isEmpty()) {
            return null;
        }

        Map<String, String> context = new HashMap<>();
        context.put("dataType", "SSN");
        context.put("userId", userId);

        return kmsEncryptionService.decrypt(encryptedSSN, context);
    }

    /**
     * Encrypt bank account number
     */
    public String encryptAccountNumber(String accountNumber, String userId, String bankId) {
        if (accountNumber == null || accountNumber.isEmpty()) {
            return null;
        }

        Map<String, String> context = new HashMap<>();
        context.put("dataType", "ACCOUNT");
        context.put("userId", userId);
        if (bankId != null) {
            context.put("bankId", bankId);
        }
        context.put("timestamp", String.valueOf(System.currentTimeMillis()));

        return kmsEncryptionService.encrypt(accountNumber, context);
    }

    /**
     * Decrypt bank account number
     */
    public String decryptAccountNumber(String encryptedAccount, String userId, String bankId) {
        if (encryptedAccount == null || encryptedAccount.isEmpty()) {
            return null;
        }

        Map<String, String> context = new HashMap<>();
        context.put("dataType", "ACCOUNT");
        context.put("userId", userId);
        if (bankId != null) {
            context.put("bankId", bankId);
        }

        return kmsEncryptionService.decrypt(encryptedAccount, context);
    }

    /**
     * Encrypt routing number
     */
    public String encryptRoutingNumber(String routingNumber, String userId) {
        if (routingNumber == null || routingNumber.isEmpty()) {
            return null;
        }

        Map<String, String> context = new HashMap<>();
        context.put("dataType", "ROUTING");
        context.put("userId", userId);

        return kmsEncryptionService.encrypt(routingNumber, context);
    }

    /**
     * Decrypt routing number
     */
    public String decryptRoutingNumber(String encryptedRouting, String userId) {
        if (encryptedRouting == null || encryptedRouting.isEmpty()) {
            return null;
        }

        Map<String, String> context = new HashMap<>();
        context.put("dataType", "ROUTING");
        context.put("userId", userId);

        return kmsEncryptionService.decrypt(encryptedRouting, context);
    }

    /**
     * Encrypt CVV (Card Verification Value)
     * Note: PCI DSS 3.2 prohibits storage of CVV after authorization
     * This is only for temporary processing
     */
    public String encryptCVV(String cvv, String userId, String sessionId) {
        if (cvv == null || cvv.isEmpty()) {
            return null;
        }

        Map<String, String> context = new HashMap<>();
        context.put("dataType", "CVV");
        context.put("userId", userId);
        context.put("sessionId", sessionId);
        context.put("timestamp", String.valueOf(System.currentTimeMillis()));
        context.put("ttl", "300"); // 5 minutes TTL

        return kmsEncryptionService.encrypt(cvv, context);
    }

    /**
     * Decrypt CVV
     */
    public String decryptCVV(String encryptedCVV, String userId, String sessionId) {
        if (encryptedCVV == null || encryptedCVV.isEmpty()) {
            return null;
        }

        Map<String, String> context = new HashMap<>();
        context.put("dataType", "CVV");
        context.put("userId", userId);
        context.put("sessionId", sessionId);

        return kmsEncryptionService.decrypt(encryptedCVV, context);
    }

    /**
     * Mask PAN for display (show only last 4 digits)
     */
    public String maskPAN(String pan) {
        if (pan == null || pan.length() < 4) {
            return "****";
        }
        return "************" + pan.substring(pan.length() - 4);
    }

    /**
     * Mask SSN for display (show only last 4 digits)
     */
    public String maskSSN(String ssn) {
        if (ssn == null || ssn.length() < 4) {
            return "***-**-****";
        }
        return "***-**-" + ssn.substring(ssn.length() - 4);
    }

    /**
     * Mask account number for display (show only last 4 digits)
     */
    public String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
