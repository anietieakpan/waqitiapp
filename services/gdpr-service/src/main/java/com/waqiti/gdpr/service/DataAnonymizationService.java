package com.waqiti.gdpr.service;

import com.waqiti.gdpr.dto.AnonymizationResult;
import com.waqiti.gdpr.dto.ErasureResult;
import com.waqiti.gdpr.integration.ServiceDataCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DataAnonymizationService {

    private final ServiceDataCollector dataCollector;
    private final EncryptionService encryptionService;
    
    private static final String[] FIRST_NAMES = {
        "Anonymous", "User", "Person", "Individual", "Member", "Customer", "Client"
    };
    
    private static final String[] LAST_NAMES = {
        "One", "Two", "Three", "Four", "Five", "A", "B", "C", "D", "E"
    };
    
    private static final String ANONYMIZED_EMAIL_DOMAIN = "@anonymized.waqiti.com";

    public ErasureResult eraseUserData(String userId, List<String> dataCategories) {
        log.info("Starting data erasure for user: {} categories: {}", userId, dataCategories);
        
        ErasureResult result = ErasureResult.builder()
            .userId(userId)
            .startTime(LocalDateTime.now())
            .dataCategories(dataCategories)
            .servicesProcessed(0)
            .recordsErased(0)
            .errors(new ArrayList<>())
            .build();

        try {
            // Process each data category
            for (String category : dataCategories) {
                processDataCategory(userId, category, result);
            }

            // Anonymize remaining data that cannot be deleted
            AnonymizationResult anonymizationResult = anonymizeUserData(userId);
            result.setRecordsAnonymized(anonymizationResult.getRecordsAnonymized());

            result.setEndTime(LocalDateTime.now());
            result.setSuccess(result.getErrors().isEmpty());

        } catch (Exception e) {
            log.error("Error during data erasure for user: {}", userId, e);
            result.getErrors().add("Erasure failed: " + e.getMessage());
            result.setSuccess(false);
        }

        return result;
    }

    private void processDataCategory(String userId, String category, ErasureResult result) {
        switch (category.toUpperCase()) {
            case "PERSONAL_INFO":
                erasePersonalInfo(userId, result);
                break;
            case "FINANCIAL_DATA":
                eraseFinancialData(userId, result);
                break;
            case "TRANSACTION_HISTORY":
                eraseTransactionHistory(userId, result);
                break;
            case "COMMUNICATION_DATA":
                eraseCommunicationData(userId, result);
                break;
            case "LOCATION_DATA":
                eraseLocationData(userId, result);
                break;
            case "DEVICE_DATA":
                eraseDeviceData(userId, result);
                break;
            case "BEHAVIORAL_DATA":
                eraseBehavioralData(userId, result);
                break;
            case "ALL":
                eraseAllData(userId, result);
                break;
            default:
                log.warn("Unknown data category: {}", category);
        }
    }

    private void erasePersonalInfo(String userId, ErasureResult result) {
        log.info("Erasing personal info for user: {}", userId);
        
        try {
            // Erase from user service
            int recordsErased = dataCollector.eraseUserPersonalInfo(userId);
            result.incrementRecordsErased(recordsErased);
            result.incrementServicesProcessed();
            
            // Erase from KYC service
            recordsErased = dataCollector.eraseKYCData(userId);
            result.incrementRecordsErased(recordsErased);
            
        } catch (Exception e) {
            log.error("Error erasing personal info", e);
            result.getErrors().add("Failed to erase personal info: " + e.getMessage());
        }
    }

    private void eraseFinancialData(String userId, ErasureResult result) {
        log.info("Erasing financial data for user: {}", userId);
        
        try {
            // Note: Financial data often cannot be fully erased due to legal requirements
            // We anonymize instead
            int recordsAnonymized = dataCollector.anonymizeFinancialData(userId);
            result.incrementRecordsAnonymized(recordsAnonymized);
            result.incrementServicesProcessed();
            
        } catch (Exception e) {
            log.error("Error processing financial data", e);
            result.getErrors().add("Failed to process financial data: " + e.getMessage());
        }
    }

    private void eraseTransactionHistory(String userId, ErasureResult result) {
        log.info("Processing transaction history for user: {}", userId);
        
        try {
            // Transactions are typically anonymized rather than deleted
            int recordsAnonymized = dataCollector.anonymizeTransactions(userId);
            result.incrementRecordsAnonymized(recordsAnonymized);
            result.incrementServicesProcessed();
            
        } catch (Exception e) {
            log.error("Error processing transaction history", e);
            result.getErrors().add("Failed to process transactions: " + e.getMessage());
        }
    }

    private void eraseCommunicationData(String userId, ErasureResult result) {
        log.info("Erasing communication data for user: {}", userId);
        
        try {
            // Erase emails, messages, notifications
            int recordsErased = dataCollector.eraseCommunicationData(userId);
            result.incrementRecordsErased(recordsErased);
            result.incrementServicesProcessed();
            
        } catch (Exception e) {
            log.error("Error erasing communication data", e);
            result.getErrors().add("Failed to erase communications: " + e.getMessage());
        }
    }

    private void eraseLocationData(String userId, ErasureResult result) {
        log.info("Erasing location data for user: {}", userId);
        
        try {
            int recordsErased = dataCollector.eraseLocationData(userId);
            result.incrementRecordsErased(recordsErased);
            
        } catch (Exception e) {
            log.error("Error erasing location data", e);
            result.getErrors().add("Failed to erase location data: " + e.getMessage());
        }
    }

    private void eraseDeviceData(String userId, ErasureResult result) {
        log.info("Erasing device data for user: {}", userId);
        
        try {
            int recordsErased = dataCollector.eraseDeviceData(userId);
            result.incrementRecordsErased(recordsErased);
            
        } catch (Exception e) {
            log.error("Error erasing device data", e);
            result.getErrors().add("Failed to erase device data: " + e.getMessage());
        }
    }

    private void eraseBehavioralData(String userId, ErasureResult result) {
        log.info("Erasing behavioral data for user: {}", userId);
        
        try {
            int recordsErased = dataCollector.eraseBehavioralData(userId);
            result.incrementRecordsErased(recordsErased);
            
        } catch (Exception e) {
            log.error("Error erasing behavioral data", e);
            result.getErrors().add("Failed to erase behavioral data: " + e.getMessage());
        }
    }

    private void eraseAllData(String userId, ErasureResult result) {
        log.info("Erasing all data for user: {}", userId);
        
        // Process all categories
        erasePersonalInfo(userId, result);
        eraseFinancialData(userId, result);
        eraseTransactionHistory(userId, result);
        eraseCommunicationData(userId, result);
        eraseLocationData(userId, result);
        eraseDeviceData(userId, result);
        eraseBehavioralData(userId, result);
    }

    public AnonymizationResult anonymizeUserData(String userId) {
        log.info("Anonymizing data for user: {}", userId);
        
        AnonymizationResult result = AnonymizationResult.builder()
            .originalUserId(userId)
            .anonymizedUserId(generateAnonymousId(userId))
            .recordsAnonymized(0)
            .timestamp(LocalDateTime.now())
            .build();

        try {
            // Generate anonymized data
            Map<String, Object> anonymizedData = generateAnonymizedData();
            
            // Update user profile
            int records = dataCollector.updateUserProfile(userId, anonymizedData);
            result.incrementRecordsAnonymized(records);
            
            // Anonymize related records
            records = dataCollector.anonymizeRelatedRecords(userId, result.getAnonymizedUserId());
            result.incrementRecordsAnonymized(records);
            
            result.setSuccess(true);
            
        } catch (Exception e) {
            log.error("Error anonymizing user data", e);
            result.setSuccess(false);
            result.setError(e.getMessage());
        }

        return result;
    }

    public String generateAnonymousId(String originalId) {
        // Generate a deterministic but irreversible anonymous ID
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(originalId.getBytes());
            return "ANON_" + bytesToHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating anonymous ID", e);
            return "ANON_" + UUID.randomUUID().toString().substring(0, 16);
        }
    }

    private Map<String, Object> generateAnonymizedData() {
        Map<String, Object> data = new HashMap<>();
        
        // Generate anonymous personal info
        String firstName = FIRST_NAMES[ThreadLocalRandom.current().nextInt(FIRST_NAMES.length)];
        String lastName = LAST_NAMES[ThreadLocalRandom.current().nextInt(LAST_NAMES.length)];
        
        data.put("firstName", firstName);
        data.put("lastName", lastName);
        data.put("email", generateAnonymousEmail());
        data.put("phoneNumber", generateAnonymousPhone());
        data.put("dateOfBirth", generateAnonymousDateOfBirth());
        data.put("address", generateAnonymousAddress());
        
        return data;
    }

    private String generateAnonymousEmail() {
        return "user" + System.currentTimeMillis() + ANONYMIZED_EMAIL_DOMAIN;
    }

    private String generateAnonymousPhone() {
        return "+00000" + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }

    private LocalDate generateAnonymousDateOfBirth() {
        // Generate a date that makes the person between 25-65 years old
        int yearsAgo = 25 + ThreadLocalRandom.current().nextInt(40);
        return LocalDate.now().minusYears(yearsAgo).withDayOfYear(1);
    }

    private Map<String, String> generateAnonymousAddress() {
        Map<String, String> address = new HashMap<>();
        address.put("street", "Anonymous Street");
        address.put("city", "Anonymous City");
        address.put("state", "XX");
        address.put("country", "XX");
        address.put("zipCode", "00000");
        return address;
    }

    public String pseudonymizeData(String data, String context) {
        // Create a reversible pseudonym using encryption
        String key = context + "_pseudonym";
        return encryptionService.encryptReversible(data, key);
    }

    public String depseudonymizeData(String pseudonym, String context) {
        // Reverse the pseudonymization
        String key = context + "_pseudonym";
        return encryptionService.decryptReversible(pseudonym, key);
    }

    public String generalizeData(String data, DataGeneralizationLevel level) {
        // Generalize data to reduce precision
        switch (level) {
            case LOW:
                return generalizeToCity(data);
            case MEDIUM:
                return generalizeToRegion(data);
            case HIGH:
                return generalizeToCountry(data);
            default:
                return "GENERALIZED";
        }
    }

    public String suppressData(String data, double threshold) {
        // Suppress data if it doesn't meet the threshold
        if (shouldSuppress(data, threshold)) {
            log.debug("Suppressing data due to low frequency threshold: {}", threshold);
            
            // Return appropriate suppression marker based on data type
            if (data == null || data.trim().isEmpty()) {
                return "[EMPTY]";
            }
            
            // Determine appropriate suppression marker based on data characteristics
            if (isNumericData(data)) {
                return "[SUPPRESSED_NUMERIC]";
            } else if (isEmailData(data)) {
                return "[SUPPRESSED_EMAIL]";
            } else if (isPhoneData(data)) {
                return "[SUPPRESSED_PHONE]";
            } else if (isAddressData(data)) {
                return "[SUPPRESSED_ADDRESS]";
            } else if (isPersonalNameData(data)) {
                return "[SUPPRESSED_NAME]";
            } else {
                return "[SUPPRESSED]";
            }
        }
        
        // Data meets threshold, return as-is or apply light generalization
        return applyLightGeneralization(data);
    }
    
    /**
     * Apply light generalization for data that doesn't need full suppression
     */
    private String applyLightGeneralization(String data) {
        if (data == null) {
            return "[NULL_VALUE]";
        }
        
        // Apply minimal generalization to preserve utility while protecting privacy
        if (isEmailData(data)) {
            return generalizeEmail(data);
        } else if (isPhoneData(data)) {
            return generalizePhone(data);
        } else if (isNumericData(data) && data.length() > 4) {
            // Preserve first and last 2 digits for numeric data
            return data.substring(0, 2) + "***" + data.substring(data.length() - 2);
        }
        
        return data; // Return unchanged if no specific generalization needed
    }
    
    /**
     * Check if data represents numeric information
     */
    private boolean isNumericData(String data) {
        return data != null && data.matches("\\d+");
    }
    
    /**
     * Check if data represents email address
     */
    private boolean isEmailData(String data) {
        return data != null && data.contains("@") && data.contains(".");
    }
    
    /**
     * Check if data represents phone number
     */
    private boolean isPhoneData(String data) {
        return data != null && (data.matches(".*\\d{3,}.*") && 
               (data.contains("-") || data.contains("(") || data.contains("+") || data.length() >= 10));
    }
    
    /**
     * Check if data represents address
     */
    private boolean isAddressData(String data) {
        return data != null && (data.toLowerCase().contains("street") || 
               data.toLowerCase().contains("avenue") || 
               data.toLowerCase().contains("road") ||
               data.toLowerCase().contains("drive") ||
               data.matches(".*\\d+.*[A-Za-z].*"));
    }
    
    /**
     * Check if data represents personal name
     */
    private boolean isPersonalNameData(String data) {
        return data != null && data.matches("^[A-Za-z\\s]+$") && 
               data.trim().split("\\s+").length >= 2;
    }
    
    /**
     * Generalize email while preserving domain structure
     */
    private String generalizeEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "[INVALID_EMAIL]";
        }
        
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return "[MALFORMED_EMAIL]";
        }
        
        String localPart = parts[0];
        String domain = parts[1];
        
        // Generalize local part but preserve domain for utility
        String generalizedLocal = localPart.length() > 2 ? 
            localPart.substring(0, 1) + "***" : "***";
            
        return generalizedLocal + "@" + domain;
    }
    
    /**
     * Generalize phone number while preserving country/area codes
     */
    private String generalizePhone(String phone) {
        if (phone == null) {
            return "[INVALID_PHONE]";
        }
        
        // Remove all non-digit characters to normalize
        String digits = phone.replaceAll("\\D", "");
        
        if (digits.length() < 7) {
            return "[SHORT_PHONE]";
        } else if (digits.length() <= 10) {
            // Preserve first 3 digits (area code), suppress middle
            return digits.substring(0, 3) + "-***-" + 
                   digits.substring(digits.length() - 4);
        } else {
            // International number - preserve country code and last 4 digits
            return "+" + digits.substring(0, 2) + "-***-***-" + 
                   digits.substring(digits.length() - 4);
        }
    }

    private boolean shouldSuppress(String data, double threshold) {
        // Implementation of suppression logic
        // For example, suppress if data appears in less than threshold% of records
        return true; // Simplified for example
    }

    private String generalizeToCity(String address) {
        // Extract and return only city from address
        return "City";
    }

    private String generalizeToRegion(String address) {
        // Extract and return only region/state from address
        return "Region";
    }

    private String generalizeToCountry(String address) {
        // Extract and return only country from address
        return "Country";
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    public enum DataGeneralizationLevel {
        LOW,
        MEDIUM,
        HIGH
    }
}