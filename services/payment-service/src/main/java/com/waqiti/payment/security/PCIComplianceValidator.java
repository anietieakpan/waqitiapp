package com.waqiti.payment.security;

import com.waqiti.payment.dto.TokenizationRequest;
import com.waqiti.payment.dto.DetokenizationRequest;
import com.waqiti.payment.dto.CardDetails;
import com.waqiti.payment.exception.PCIComplianceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * CRITICAL: PCI DSS Compliance Validator
 * 
 * This service validates all operations for PCI DSS compliance:
 * - Ensures no prohibited data is stored
 * - Validates tokenization requests
 * - Enforces access controls
 * - Monitors compliance violations
 * 
 * PCI DSS REQUIREMENTS ENFORCED:
 * - No PAN storage in clear text
 * - No CVV storage
 * - No track data storage
 * - No PIN data handling
 * - Proper access controls
 * - Audit trail requirements
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PCIComplianceValidator {
    
    @Value("${payment.pci.strict-validation:true}")
    private boolean strictValidation;
    
    @Value("${payment.pci.allow-test-cards:false}")
    private boolean allowTestCards;
    
    @Value("${payment.pci.max-pan-length:19}")
    private int maxPanLength;
    
    @Value("${payment.pci.min-pan-length:13}")
    private int minPanLength;
    
    // PCI DSS prohibited data patterns
    private static final Pattern CVV_PATTERN = Pattern.compile(".*[0-9]{3,4}.*");
    private static final Pattern TRACK1_PATTERN = Pattern.compile(".*%[A-Z0-9]+\\^[A-Z ]+\\^[0-9]{4}.*");
    private static final Pattern TRACK2_PATTERN = Pattern.compile(".*;[0-9]{13,19}=[0-9]{4}.*");
    private static final Pattern PAN_PATTERN = Pattern.compile(".*[0-9]{13,19}.*");
    
    // Test card numbers (should not be used in production)
    private static final Set<String> TEST_CARD_NUMBERS = Set.of(
        "4111111111111111", // Visa test card
        "4012888888881881", // Visa test card
        "5555555555554444", // Mastercard test card
        "5105105105105100", // Mastercard test card
        "378282246310005",  // Amex test card
        "371449635398431",  // Amex test card
        "6011111111111117", // Discover test card
        "6011000990139424"  // Discover test card
    );
    
    // Authorized detokenization purposes
    private static final Set<String> AUTHORIZED_PURPOSES = Set.of(
        "PAYMENT_PROCESSING",
        "FRAUD_INVESTIGATION",
        "COMPLIANCE_AUDIT",
        "DISPUTE_RESOLUTION",
        "REGULATORY_REPORTING"
    );
    
    /**
     * Validate tokenization request for PCI DSS compliance
     * 
     * @param request Tokenization request to validate
     * @return true if compliant, throws exception if not
     * @throws PCIComplianceException if validation fails
     */
    public boolean isTokenizationCompliant(TokenizationRequest request) {
        log.debug("Validating tokenization request for PCI DSS compliance: userId={}", 
            request.getUserId());
        
        try {
            // Step 1: Validate card details
            validateCardDetails(request.getCardDetails());
            
            // Step 2: Validate request structure
            validateTokenizationRequestStructure(request);
            
            // Step 3: Validate environment constraints
            validateEnvironmentConstraints(request);
            
            // Step 4: Validate security requirements
            validateSecurityRequirements(request);
            
            log.debug("Tokenization request passed PCI DSS compliance validation: userId={}", 
                request.getUserId());
            
            return true;
            
        } catch (Exception e) {
            log.error("Tokenization request failed PCI DSS compliance validation: userId={}, error={}", 
                request.getUserId(), e.getMessage());
            
            if (e instanceof PCIComplianceException) {
                throw e;
            }
            
            throw new PCIComplianceException("PCI DSS compliance validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate detokenization request for PCI DSS compliance
     * 
     * @param request Detokenization request to validate
     * @return true if compliant, throws exception if not
     * @throws PCIComplianceException if validation fails
     */
    public boolean isDetokenizationCompliant(DetokenizationRequest request) {
        log.debug("Validating detokenization request for PCI DSS compliance: userId={}, purpose={}", 
            request.getUserId(), request.getPurpose());
        
        try {
            // Step 1: Validate purpose authorization
            validateDetokenizationPurpose(request.getPurpose());
            
            // Step 2: Validate access controls
            validateDetokenizationAccess(request);
            
            // Step 3: Validate audit requirements
            validateAuditRequirements(request);
            
            log.debug("Detokenization request passed PCI DSS compliance validation: userId={}, purpose={}", 
                request.getUserId(), request.getPurpose());
            
            return true;
            
        } catch (Exception e) {
            log.error("Detokenization request failed PCI DSS compliance validation: userId={}, purpose={}, error={}", 
                request.getUserId(), request.getPurpose(), e.getMessage());
            
            if (e instanceof PCIComplianceException) {
                throw e;
            }
            
            throw new PCIComplianceException("PCI DSS compliance validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate that text doesn't contain prohibited card data
     * 
     * @param text Text to validate
     * @param context Context for error reporting
     * @throws PCIComplianceException if prohibited data found
     */
    public void validateTextForProhibitedData(String text, String context) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        // Check for PAN patterns
        if (PAN_PATTERN.matcher(text).matches()) {
            throw new PCIComplianceException("PCI VIOLATION: PAN detected in " + context);
        }
        
        // Check for track data patterns
        if (TRACK1_PATTERN.matcher(text).matches() || TRACK2_PATTERN.matcher(text).matches()) {
            throw new PCIComplianceException("PCI VIOLATION: Track data detected in " + context);
        }
        
        // Check for CVV patterns (basic check)
        if (strictValidation && CVV_PATTERN.matcher(text).matches()) {
            log.warn("POTENTIAL PCI VIOLATION: CVV-like pattern detected in {}", context);
        }
    }
    
    /**
     * Validate card details for PCI DSS compliance
     */
    private void validateCardDetails(CardDetails cardDetails) {
        if (cardDetails == null) {
            throw new PCIComplianceException("Card details are required for tokenization");
        }
        
        // Validate PAN
        validatePAN(cardDetails.getCardNumber());
        
        // Ensure CVV is not being stored (it's OK to receive it for processing)
        if (cardDetails.getCvv() != null) {
            log.debug("CVV provided for tokenization - will be used for validation only, not stored");
        }
        
        // Ensure track data is not present
        if (cardDetails.getTrackData() != null) {
            throw new PCIComplianceException("PCI VIOLATION: Track data cannot be processed");
        }
        
        // Validate expiry
        if (cardDetails.getExpiryMonth() == null || cardDetails.getExpiryYear() == null) {
            throw new PCIComplianceException("Card expiry is required for tokenization");
        }
        
        // Check for test cards in production
        if (!allowTestCards && isTestCard(cardDetails.getCardNumber())) {
            throw new PCIComplianceException("Test card numbers not allowed in production environment");
        }
    }
    
    /**
     * Validate Primary Account Number
     */
    private void validatePAN(String pan) {
        if (pan == null || pan.trim().isEmpty()) {
            throw new PCIComplianceException("PAN is required for tokenization");
        }
        
        // Remove any spaces or dashes
        String cleanPan = pan.replaceAll("[\\s-]", "");
        
        // Validate length
        if (cleanPan.length() < minPanLength || cleanPan.length() > maxPanLength) {
            throw new PCIComplianceException("Invalid PAN length: must be between " + 
                minPanLength + " and " + maxPanLength + " digits");
        }
        
        // Validate numeric
        if (!cleanPan.matches("^[0-9]+$")) {
            throw new PCIComplianceException("PAN must contain only numeric digits");
        }
        
        // Validate Luhn algorithm
        if (strictValidation && !isValidLuhn(cleanPan)) {
            throw new PCIComplianceException("PAN fails Luhn algorithm validation");
        }
    }
    
    /**
     * Validate tokenization request structure
     */
    private void validateTokenizationRequestStructure(TokenizationRequest request) {
        if (request.getUserId() == null) {
            throw new PCIComplianceException("User ID is required for tokenization");
        }
        
        if (request.getPurpose() == null || request.getPurpose().trim().isEmpty()) {
            throw new PCIComplianceException("Purpose is required for tokenization audit trail");
        }
        
        if (!request.isPciCompliant()) {
            throw new PCIComplianceException("PCI compliance flag must be enabled");
        }
        
        if (!request.isAuditAllOperations()) {
            throw new PCIComplianceException("Audit trail must be enabled for PCI compliance");
        }
    }
    
    /**
     * Validate environment constraints
     */
    private void validateEnvironmentConstraints(TokenizationRequest request) {
        String environment = request.getEnvironment();
        
        if ("PRODUCTION".equals(environment)) {
            // Production environment has stricter requirements
            if (allowTestCards) {
                log.warn("SECURITY WARNING: Test cards are allowed in production environment");
            }
            
            if (!"CRITICAL".equals(request.getSecurityLevel()) && 
                !"HIGH".equals(request.getSecurityLevel())) {
                log.info("Standard security level tokenization in production: userId={}", 
                    request.getUserId());
            }
        }
    }
    
    /**
     * Validate security requirements
     */
    private void validateSecurityRequirements(TokenizationRequest request) {
        // Ensure format-preserving tokenization doesn't compromise security
        if (request.isFormatPreserving() && !request.isLuhnCompliant()) {
            log.warn("Format-preserving tokenization without Luhn compliance may reduce security");
        }
        
        // Validate metadata doesn't contain sensitive data
        if (request.getMetadata() != null) {
            validateTextForProhibitedData(request.getMetadata(), "request metadata");
        }
    }
    
    /**
     * Validate detokenization purpose
     */
    private void validateDetokenizationPurpose(String purpose) {
        if (purpose == null || purpose.trim().isEmpty()) {
            throw new PCIComplianceException("Purpose is required for detokenization");
        }
        
        if (!AUTHORIZED_PURPOSES.contains(purpose)) {
            throw new PCIComplianceException("Unauthorized detokenization purpose: " + purpose);
        }
    }
    
    /**
     * Validate detokenization access controls
     */
    private void validateDetokenizationAccess(DetokenizationRequest request) {
        if (request.getRequesterId() == null) {
            throw new PCIComplianceException("Requester ID is required for access control");
        }
        
        if (request.getSourceSystem() == null || request.getSourceSystem().trim().isEmpty()) {
            throw new PCIComplianceException("Source system is required for audit trail");
        }
        
        // High-security purposes require additional authorization
        if (request.isHighSecurityPurpose() && request.getAuthorizationToken() == null) {
            throw new PCIComplianceException("Authorization token required for " + request.getPurpose());
        }
    }
    
    /**
     * Validate audit requirements
     */
    private void validateAuditRequirements(DetokenizationRequest request) {
        if (!request.isAuditRequired()) {
            throw new PCIComplianceException("Audit trail must be enabled for detokenization");
        }
        
        if (!request.isPciCompliant()) {
            throw new PCIComplianceException("PCI compliance flag must be enabled");
        }
    }
    
    /**
     * Check if card number is a test card
     */
    private boolean isTestCard(String pan) {
        if (pan == null) {
            return false;
        }
        
        String cleanPan = pan.replaceAll("[\\s-]", "");
        return TEST_CARD_NUMBERS.contains(cleanPan);
    }
    
    /**
     * Validate Luhn algorithm
     */
    private boolean isValidLuhn(String pan) {
        int sum = 0;
        boolean alternate = false;
        
        for (int i = pan.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(pan.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return sum % 10 == 0;
    }
    
    /**
     * Get compliance status summary
     */
    public String getComplianceStatus() {
        return String.format("PCIComplianceValidator{strictValidation=%s, allowTestCards=%s, " +
                           "authorizedPurposes=%d}", 
            strictValidation, allowTestCards, AUTHORIZED_PURPOSES.size());
    }
}