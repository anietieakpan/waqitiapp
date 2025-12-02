package com.waqiti.virtualcard.service;

import com.waqiti.virtualcard.domain.ShippingAddress;
import com.waqiti.virtualcard.dto.AddressValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service for validating shipping addresses
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressValidationService {
    
    @Value("${address-validation.provider:internal}")
    private String provider;
    
    @Value("${address-validation.strict-mode:false}")
    private boolean strictMode;
    
    // List of supported countries
    private static final Set<String> SUPPORTED_COUNTRIES = Set.of(
        "US", "CA", "GB", "DE", "FR", "IT", "ES", "AU", "JP", "SG"
    );
    
    // List of US states for validation
    private static final Set<String> US_STATES = Set.of(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
        "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
        "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
        "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
        "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
        "DC"
    );
    
    /**
     * Validates a shipping address
     */
    public ShippingAddress validateAddress(ShippingAddress address) {
        log.debug("Validating address for recipient: {}", address.getRecipientName());
        
        AddressValidationResult result = performValidation(address);
        
        if (result.isValid()) {
            // Return validated/normalized address
            return ShippingAddress.builder()
                .recipientName(normalizeRecipientName(address.getRecipientName()))
                .companyName(address.getCompanyName())
                .addressLine1(normalizeAddressLine(address.getAddressLine1()))
                .addressLine2(normalizeAddressLine(address.getAddressLine2()))
                .city(normalizeCity(address.getCity()))
                .stateProvince(normalizeStateProvince(address.getStateProvince(), address.getCountry()))
                .postalCode(normalizePostalCode(address.getPostalCode(), address.getCountry()))
                .country(address.getCountry().toUpperCase())
                .phoneNumber(normalizePhoneNumber(address.getPhoneNumber()))
                .deliveryInstructions(address.getDeliveryInstructions())
                .addressType(determineAddressType(address))
                .validated(true)
                .validationScore(result.getValidationScore())
                .latitude(result.getLatitude())
                .longitude(result.getLongitude())
                .build();
        } else {
            throw new com.waqiti.common.exception.InvalidAddressException(
                "Address validation failed: " + result.getErrorMessage()
            );
        }
    }
    
    /**
     * Performs the actual address validation
     */
    private AddressValidationResult performValidation(ShippingAddress address) {
        try {
            // Basic field validation
            if (!hasRequiredFields(address)) {
                return AddressValidationResult.builder()
                    .valid(false)
                    .errorMessage("Required address fields are missing")
                    .errorCode("MISSING_FIELDS")
                    .build();
            }
            
            // Country validation
            if (!isCountrySupported(address.getCountry())) {
                return AddressValidationResult.builder()
                    .valid(false)
                    .errorMessage("Shipping to " + address.getCountry() + " is not supported")
                    .errorCode("UNSUPPORTED_COUNTRY")
                    .build();
            }
            
            // Format validation
            if (!isAddressFormatValid(address)) {
                return AddressValidationResult.builder()
                    .valid(false)
                    .errorMessage("Address format is invalid")
                    .errorCode("INVALID_FORMAT")
                    .build();
            }
            
            // Postal code validation
            if (!isPostalCodeValid(address.getPostalCode(), address.getCountry())) {
                return AddressValidationResult.builder()
                    .valid(false)
                    .errorMessage("Invalid postal code format")
                    .errorCode("INVALID_POSTAL_CODE")
                    .build();
            }
            
            // State/Province validation (for US and Canada)
            if (!isStateProvinceValid(address.getStateProvince(), address.getCountry())) {
                return AddressValidationResult.builder()
                    .valid(false)
                    .errorMessage("Invalid state/province")
                    .errorCode("INVALID_STATE_PROVINCE")
                    .build();
            }
            
            // Check for PO Box restrictions (if configured)
            if (strictMode && isPOBox(address.getAddressLine1())) {
                return AddressValidationResult.builder()
                    .valid(false)
                    .errorMessage("PO Box addresses are not allowed for card delivery")
                    .errorCode("PO_BOX_NOT_ALLOWED")
                    .build();
            }
            
            // All validations passed
            return AddressValidationResult.builder()
                .valid(true)
                .validationScore(calculateValidationScore(address))
                .latitude(simulateLatitude())
                .longitude(simulateLongitude())
                .normalizedAddress(address) // Would be normalized by external service
                .build();
                
        } catch (Exception e) {
            log.error("Address validation failed", e);
            return AddressValidationResult.builder()
                .valid(false)
                .errorMessage("Address validation service error")
                .errorCode("VALIDATION_ERROR")
                .build();
        }
    }
    
    private boolean hasRequiredFields(ShippingAddress address) {
        return address.getRecipientName() != null && !address.getRecipientName().trim().isEmpty() &&
               address.getAddressLine1() != null && !address.getAddressLine1().trim().isEmpty() &&
               address.getCity() != null && !address.getCity().trim().isEmpty() &&
               address.getPostalCode() != null && !address.getPostalCode().trim().isEmpty() &&
               address.getCountry() != null && !address.getCountry().trim().isEmpty();
    }
    
    private boolean isCountrySupported(String country) {
        return SUPPORTED_COUNTRIES.contains(country.toUpperCase());
    }
    
    private boolean isAddressFormatValid(ShippingAddress address) {
        // Basic format validation
        if (address.getRecipientName().length() > 50) return false;
        if (address.getAddressLine1().length() > 100) return false;
        if (address.getAddressLine2() != null && address.getAddressLine2().length() > 100) return false;
        if (address.getCity().length() > 50) return false;
        
        return true;
    }
    
    private boolean isPostalCodeValid(String postalCode, String country) {
        if (postalCode == null) return false;
        
        switch (country.toUpperCase()) {
            case "US":
                return postalCode.matches("^\\d{5}(-\\d{4})?$");
            case "CA":
                return postalCode.matches("^[A-Za-z]\\d[A-Za-z] ?\\d[A-Za-z]\\d$");
            case "GB":
                return postalCode.matches("^[A-Za-z]{1,2}\\d[A-Za-z\\d]? ?\\d[A-Za-z]{2}$");
            case "DE":
                return postalCode.matches("^\\d{5}$");
            case "FR":
                return postalCode.matches("^\\d{5}$");
            case "AU":
                return postalCode.matches("^\\d{4}$");
            case "JP":
                return postalCode.matches("^\\d{3}-\\d{4}$");
            default:
                return postalCode.length() >= 3 && postalCode.length() <= 10;
        }
    }
    
    private boolean isStateProvinceValid(String stateProvince, String country) {
        if ("US".equals(country.toUpperCase())) {
            return stateProvince != null && US_STATES.contains(stateProvince.toUpperCase());
        }
        // For other countries, we're less strict
        return true;
    }
    
    private boolean isPOBox(String addressLine) {
        String normalized = addressLine.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return normalized.contains("POBOX") || 
               normalized.contains("PO BOX") ||
               normalized.matches(".*P\\.?O\\.?\\s*BOX.*");
    }
    
    private String normalizeRecipientName(String name) {
        return name.trim().toUpperCase();
    }
    
    private String normalizeAddressLine(String addressLine) {
        if (addressLine == null) return null;
        return addressLine.trim().toUpperCase();
    }
    
    private String normalizeCity(String city) {
        return city.trim().toUpperCase();
    }
    
    private String normalizeStateProvince(String stateProvince, String country) {
        if (stateProvince == null) return null;
        return stateProvince.trim().toUpperCase();
    }
    
    private String normalizePostalCode(String postalCode, String country) {
        String normalized = postalCode.trim().toUpperCase();
        
        // Country-specific normalization
        if ("CA".equals(country)) {
            // Canadian postal codes: remove spaces and format as A1A 1A1
            normalized = normalized.replaceAll("\\s+", "");
            if (normalized.length() == 6) {
                normalized = normalized.substring(0, 3) + " " + normalized.substring(3);
            }
        }
        
        return normalized;
    }
    
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        
        // Remove all non-digit characters except +
        String normalized = phoneNumber.replaceAll("[^+\\d]", "");
        
        // Basic US phone number formatting
        if (normalized.length() == 10 && !normalized.startsWith("+")) {
            normalized = "+1" + normalized;
        }
        
        return normalized;
    }
    
    private String determineAddressType(ShippingAddress address) {
        if (address.getCompanyName() != null && !address.getCompanyName().trim().isEmpty()) {
            return "BUSINESS";
        }
        if (isPOBox(address.getAddressLine1())) {
            return "PO_BOX";
        }
        return "HOME";
    }
    
    private double calculateValidationScore(ShippingAddress address) {
        double score = 0.5; // Base score
        
        // Add points for complete information
        if (address.getPhoneNumber() != null) score += 0.1;
        if (address.getStateProvince() != null) score += 0.1;
        if (address.getAddressLine2() != null) score += 0.1;
        
        // Add points for format compliance
        if (isPostalCodeValid(address.getPostalCode(), address.getCountry())) score += 0.2;
        
        return Math.min(1.0, score);
    }
    
    // Simulate coordinates (in production, would come from geocoding service)
    private Double simulateLatitude() {
        return 40.7128 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 10; // Around NYC +/- 5 degrees
    }
    
    private Double simulateLongitude() {
        return -74.0060 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 10; // Around NYC +/- 5 degrees
    }
}