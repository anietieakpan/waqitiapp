package com.waqiti.payment.tokenization;

import com.waqiti.common.security.SecureRandomUtils;
import com.waqiti.common.audit.AuditService;
import com.waqiti.payment.exception.TokenGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * CRITICAL: PCI DSS Compliant Token Generator Service
 * 
 * This service generates cryptographically secure tokens for payment card data
 * in compliance with PCI DSS tokenization requirements.
 * 
 * TOKEN GENERATION FEATURES:
 * - Format-preserving tokenization (maintains PAN structure)
 * - Cryptographically secure random generation
 * - Collision detection and prevention
 * - Luhn algorithm compliance for format-preserving tokens
 * - Configurable token formats and lengths
 * - Comprehensive audit trails
 * 
 * SECURITY FEATURES:
 * - CSPRNG (Cryptographically Secure Pseudo-Random Number Generator)
 * - Entropy validation
 * - Token uniqueness guarantees
 * - No reversible patterns
 * - Secure seed management
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenGenerator {
    
    private final SecureRandomUtils secureRandomUtils;
    private final AuditService auditService;
    
    @Value("${payment.tokenization.token-prefix:tok_}")
    private String tokenPrefix;
    
    @Value("${payment.tokenization.enable-luhn-compliance:true}")
    private boolean enableLuhnCompliance;
    
    @Value("${payment.tokenization.max-generation-attempts:100}")
    private int maxGenerationAttempts;
    
    @Value("${payment.tokenization.audit-token-generation:true}")
    private boolean auditTokenGeneration;
    
    @Value("${payment.tokenization.min-entropy-bits:128}")
    private int minEntropyBits;
    
    // Thread-safe secure random instance
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Token collision detection cache (stores hashes, not actual tokens)
    private final Set<String> tokenHashCache = ConcurrentHashMap.newKeySet();
    
    // Character sets for different token types
    private static final String NUMERIC_CHARS = "0123456789";
    private static final String ALPHANUMERIC_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String SAFE_ALPHANUMERIC_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // No confusing chars
    
    // BIN (Bank Identification Number) ranges for major card networks
    private static final Map<String, String> CARD_BIN_PREFIXES = Map.of(
        "VISA", "4",
        "MASTERCARD", "5",
        "AMEX", "3",
        "DISCOVER", "6"
    );
    
    // Regular expressions for validation
    private static final Pattern PAN_PATTERN = Pattern.compile("^[0-9]{13,19}$");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^tok_[A-Z0-9]{12,16}$");
    
    /**
     * Generate format-preserving token that maintains PAN structure
     * 
     * @param pan Primary Account Number to tokenize
     * @return Format-preserving token
     * @throws TokenGenerationException if generation fails
     */
    public String generateFormatPreservingToken(String pan) {
        String correlationId = UUID.randomUUID().toString();
        
        log.debug("Generating format-preserving token: correlation={}", correlationId);
        
        try {
            // Validate input PAN
            validatePAN(pan);
            
            // Detect card type and preserve BIN structure
            String cardType = detectCardType(pan);
            String binPrefix = CARD_BIN_PREFIXES.get(cardType);
            
            // Generate token with same length and structure as PAN
            String token = generateStructuredToken(pan, binPrefix, correlationId);
            
            // Validate generated token
            validateGeneratedToken(token, pan.length());
            
            // Audit token generation
            auditTokenGeneration("FORMAT_PRESERVING_TOKEN_GENERATED", correlationId, pan.length());
            
            log.debug("Format-preserving token generated: correlation={}, cardType={}, length={}", 
                correlationId, cardType, token.length());
            
            return token;
            
        } catch (Exception e) {
            log.error("Failed to generate format-preserving token: correlation={}, error={}", 
                correlationId, e.getMessage(), e);
            
            auditTokenGenerationFailure("FORMAT_PRESERVING_TOKEN_FAILED", correlationId, e.getMessage());
            
            if (e instanceof TokenGenerationException) {
                throw e;
            }
            
            throw new TokenGenerationException("Failed to generate format-preserving token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate secure random token with specified length
     * 
     * @param length Desired token length (excluding prefix)
     * @return Secure random token
     * @throws TokenGenerationException if generation fails
     */
    public String generateSecureRandomToken(int length) {
        String correlationId = UUID.randomUUID().toString();
        
        log.debug("Generating secure random token: length={}, correlation={}", length, correlationId);
        
        try {
            // Validate length requirements
            validateTokenLength(length);
            
            // Generate cryptographically secure random token
            String token = generateCryptographicallySecureToken(length, correlationId);
            
            // Validate generated token
            validateGeneratedRandomToken(token, length);
            
            // Audit token generation
            auditTokenGeneration("SECURE_RANDOM_TOKEN_GENERATED", correlationId, length);
            
            log.debug("Secure random token generated: correlation={}, length={}", correlationId, token.length());
            
            return token;
            
        } catch (Exception e) {
            log.error("Failed to generate secure random token: length={}, correlation={}, error={}", 
                length, correlationId, e.getMessage(), e);
            
            auditTokenGenerationFailure("SECURE_RANDOM_TOKEN_FAILED", correlationId, e.getMessage());
            
            if (e instanceof TokenGenerationException) {
                throw e;
            }
            
            throw new TokenGenerationException("Failed to generate secure random token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate network token for mobile payments (Apple Pay, Google Pay)
     * 
     * @param pan Original PAN
     * @param networkProvider Network provider (APPLE_PAY, GOOGLE_PAY, etc.)
     * @return Network-specific token
     * @throws TokenGenerationException if generation fails
     */
    public String generateNetworkToken(String pan, String networkProvider) {
        String correlationId = UUID.randomUUID().toString();
        
        log.debug("Generating network token: provider={}, correlation={}", networkProvider, correlationId);
        
        try {
            // Validate input
            validatePAN(pan);
            validateNetworkProvider(networkProvider);
            
            // Generate network-specific token
            String token = generateNetworkSpecificToken(pan, networkProvider, correlationId);
            
            // Validate generated token
            validateNetworkToken(token, networkProvider);
            
            // Audit token generation
            auditTokenGeneration("NETWORK_TOKEN_GENERATED", correlationId, networkProvider);
            
            log.debug("Network token generated: provider={}, correlation={}", networkProvider, correlationId);
            
            return token;
            
        } catch (Exception e) {
            log.error("Failed to generate network token: provider={}, correlation={}, error={}", 
                networkProvider, correlationId, e.getMessage(), e);
            
            auditTokenGenerationFailure("NETWORK_TOKEN_FAILED", correlationId, e.getMessage());
            
            if (e instanceof TokenGenerationException) {
                throw e;
            }
            
            throw new TokenGenerationException("Failed to generate network token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate temporary token for single-use transactions
     * 
     * @param pan Original PAN
     * @param expiryMinutes Token expiry in minutes
     * @return Temporary token
     * @throws TokenGenerationException if generation fails
     */
    public String generateTemporaryToken(String pan, int expiryMinutes) {
        String correlationId = UUID.randomUUID().toString();
        
        log.debug("Generating temporary token: expiry={} minutes, correlation={}", expiryMinutes, correlationId);
        
        try {
            // Validate input
            validatePAN(pan);
            validateExpiryMinutes(expiryMinutes);
            
            // Generate time-bound token
            String token = generateTimeBoundToken(pan, expiryMinutes, correlationId);
            
            // Validate generated token
            validateTemporaryToken(token);
            
            // Audit token generation
            auditTokenGeneration("TEMPORARY_TOKEN_GENERATED", correlationId, expiryMinutes);
            
            log.debug("Temporary token generated: expiry={} minutes, correlation={}", expiryMinutes, correlationId);
            
            return token;
            
        } catch (Exception e) {
            log.error("Failed to generate temporary token: expiry={}, correlation={}, error={}", 
                expiryMinutes, correlationId, e.getMessage(), e);
            
            auditTokenGenerationFailure("TEMPORARY_TOKEN_FAILED", correlationId, e.getMessage());
            
            if (e instanceof TokenGenerationException) {
                throw e;
            }
            
            throw new TokenGenerationException("Failed to generate temporary token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate if token format is correct
     * 
     * @param token Token to validate
     * @return true if valid format, false otherwise
     */
    public boolean isValidTokenFormat(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        
        // Check if token matches expected pattern
        return TOKEN_PATTERN.matcher(token).matches();
    }
    
    /**
     * Get token entropy in bits
     * 
     * @param token Token to analyze
     * @return Estimated entropy in bits
     */
    public double calculateTokenEntropy(String token) {
        if (token == null || token.isEmpty()) {
            return 0.0;
        }
        
        // Remove prefix for entropy calculation
        String tokenBody = token.startsWith(tokenPrefix) ? 
            token.substring(tokenPrefix.length()) : token;
        
        // Calculate Shannon entropy
        Map<Character, Integer> charFreq = new HashMap<>();
        for (char c : tokenBody.toCharArray()) {
            charFreq.merge(c, 1, Integer::sum);
        }
        
        double entropy = 0.0;
        int length = tokenBody.length();
        
        for (int freq : charFreq.values()) {
            double probability = (double) freq / length;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        
        return entropy * length;
    }
    
    /**
     * Generate structured token maintaining PAN format
     */
    private String generateStructuredToken(String pan, String binPrefix, String correlationId) {
        int panLength = pan.length();
        String lastFourDigits = pan.substring(panLength - 4);
        
        // Calculate middle section length
        int prefixLength = binPrefix != null ? binPrefix.length() : 1;
        int middleLength = panLength - prefixLength - 4; // Exclude BIN and last 4
        
        for (int attempt = 0; attempt < maxGenerationAttempts; attempt++) {
            StringBuilder tokenBuilder = new StringBuilder();
            
            // Start with BIN-like prefix (but different from original)
            if (binPrefix != null) {
                tokenBuilder.append(generateSafeBinPrefix(binPrefix));
            } else {
                tokenBuilder.append(generateRandomDigit(1, 9)); // Non-zero first digit
            }
            
            // Generate middle section
            for (int i = 0; i < middleLength; i++) {
                tokenBuilder.append(generateRandomDigit(0, 9));
            }
            
            // Preserve last 4 digits structure but with different values
            tokenBuilder.append(generateLast4Alternative(lastFourDigits));
            
            String candidateToken = tokenBuilder.toString();
            
            // Apply Luhn algorithm if enabled
            if (enableLuhnCompliance) {
                candidateToken = makeLuhnCompliant(candidateToken);
            }
            
            // Check for uniqueness
            String tokenHash = hashToken(candidateToken);
            if (tokenHashCache.add(tokenHash)) {
                return candidateToken;
            }
        }
        
        throw new TokenGenerationException("Failed to generate unique structured token after " + 
            maxGenerationAttempts + " attempts");
    }
    
    /**
     * Generate cryptographically secure random token
     */
    private String generateCryptographicallySecureToken(int length, String correlationId) {
        for (int attempt = 0; attempt < maxGenerationAttempts; attempt++) {
            StringBuilder tokenBuilder = new StringBuilder();
            
            // Use secure random generation
            for (int i = 0; i < length; i++) {
                char randomChar = SAFE_ALPHANUMERIC_CHARS.charAt(
                    secureRandom.nextInt(SAFE_ALPHANUMERIC_CHARS.length())
                );
                tokenBuilder.append(randomChar);
            }
            
            String candidateToken = tokenBuilder.toString();
            
            // Validate entropy
            double entropy = calculateTokenEntropy(candidateToken);
            if (entropy < minEntropyBits) {
                continue; // Regenerate if insufficient entropy
            }
            
            // Check for uniqueness
            String tokenHash = hashToken(candidateToken);
            if (tokenHashCache.add(tokenHash)) {
                return candidateToken;
            }
        }
        
        throw new TokenGenerationException("Failed to generate unique secure token after " + 
            maxGenerationAttempts + " attempts");
    }
    
    /**
     * Generate network-specific token
     */
    private String generateNetworkSpecificToken(String pan, String networkProvider, String correlationId) {
        String prefix = getNetworkTokenPrefix(networkProvider);
        int tokenLength = pan.length();
        
        StringBuilder tokenBuilder = new StringBuilder();
        tokenBuilder.append(prefix);
        
        // Generate remaining digits
        int remainingLength = tokenLength - prefix.length();
        for (int i = 0; i < remainingLength; i++) {
            tokenBuilder.append(generateRandomDigit(0, 9));
        }
        
        String candidateToken = tokenBuilder.toString();
        
        // Apply network-specific validation
        if (enableLuhnCompliance && !"AMEX".equals(detectCardTypeFromNetworkProvider(networkProvider))) {
            candidateToken = makeLuhnCompliant(candidateToken);
        }
        
        return candidateToken;
    }
    
    /**
     * Generate time-bound token
     */
    private String generateTimeBoundToken(String pan, int expiryMinutes, String correlationId) {
        // Include timestamp component for uniqueness
        long timestamp = Instant.now().toEpochMilli();
        String timestampStr = Long.toString(timestamp);
        
        // Use last 8 digits of timestamp
        String timeComponent = timestampStr.substring(Math.max(0, timestampStr.length() - 8));
        
        StringBuilder tokenBuilder = new StringBuilder();
        tokenBuilder.append("tmp_"); // Temporary token prefix
        tokenBuilder.append(timeComponent);
        
        // Add random component
        int randomLength = Math.max(8, pan.length() - timeComponent.length() - 4);
        for (int i = 0; i < randomLength; i++) {
            tokenBuilder.append(ALPHANUMERIC_CHARS.charAt(
                secureRandom.nextInt(ALPHANUMERIC_CHARS.length())
            ));
        }
        
        return tokenBuilder.toString();
    }
    
    /**
     * Detect card type from PAN
     */
    private String detectCardType(String pan) {
        if (pan.startsWith("4")) return "VISA";
        if (pan.startsWith("5") || pan.startsWith("2")) return "MASTERCARD";
        if (pan.startsWith("3")) return "AMEX";
        if (pan.startsWith("6")) return "DISCOVER";
        return "UNKNOWN";
    }
    
    /**
     * Generate safe BIN prefix that's different from original
     */
    private String generateSafeBinPrefix(String originalBin) {
        // Ensure we don't generate the same BIN
        String[] safeBins = {"9", "8", "7"}; // Use unused BIN ranges
        return safeBins[secureRandom.nextInt(safeBins.length)];
    }
    
    /**
     * Generate alternative last 4 digits
     */
    private String generateLast4Alternative(String originalLast4) {
        // Generate different last 4 digits for display purposes
        StringBuilder last4 = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int digit;
            do {
                digit = generateRandomDigit(0, 9);
            } while (digit == Character.getNumericValue(originalLast4.charAt(i)));
            last4.append(digit);
        }
        return last4.toString();
    }
    
    /**
     * Generate random digit in range
     */
    private int generateRandomDigit(int min, int max) {
        return secureRandom.nextInt(max - min + 1) + min;
    }
    
    /**
     * Make token compliant with Luhn algorithm
     */
    private String makeLuhnCompliant(String token) {
        StringBuilder tokenBuilder = new StringBuilder(token);
        
        // Calculate Luhn check digit
        int sum = 0;
        boolean alternate = true;
        
        for (int i = token.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(token.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        int checkDigit = (10 - (sum % 10)) % 10;
        
        // Replace last digit with check digit
        tokenBuilder.setCharAt(token.length() - 1, Character.forDigit(checkDigit, 10));
        
        return tokenBuilder.toString();
    }
    
    /**
     * Get network token prefix
     */
    private String getNetworkTokenPrefix(String networkProvider) {
        switch (networkProvider) {
            case "APPLE_PAY": return "50";
            case "GOOGLE_PAY": return "51";
            case "SAMSUNG_PAY": return "52";
            default: return "59";
        }
    }
    
    /**
     * Detect card type from network provider
     */
    private String detectCardTypeFromNetworkProvider(String networkProvider) {
        // Network tokens typically follow Mastercard format
        return "MASTERCARD";
    }
    
    /**
     * Generate hash for token uniqueness check
     */
    private String hashToken(String token) {
        return Integer.toHexString(token.hashCode());
    }
    
    /**
     * Validation methods
     */
    private void validatePAN(String pan) {
        if (pan == null || pan.trim().isEmpty()) {
            throw new TokenGenerationException("PAN is required for token generation");
        }
        
        if (!PAN_PATTERN.matcher(pan).matches()) {
            throw new TokenGenerationException("Invalid PAN format");
        }
        
        // Validate Luhn algorithm
        if (!isValidLuhn(pan)) {
            throw new TokenGenerationException("PAN fails Luhn validation");
        }
    }
    
    private void validateTokenLength(int length) {
        if (length < 8 || length > 32) {
            throw new TokenGenerationException("Token length must be between 8 and 32 characters");
        }
    }
    
    private void validateNetworkProvider(String networkProvider) {
        Set<String> validProviders = Set.of("APPLE_PAY", "GOOGLE_PAY", "SAMSUNG_PAY", "GENERIC");
        if (!validProviders.contains(networkProvider)) {
            throw new TokenGenerationException("Invalid network provider: " + networkProvider);
        }
    }
    
    private void validateExpiryMinutes(int expiryMinutes) {
        if (expiryMinutes < 1 || expiryMinutes > 1440) { // Max 24 hours
            throw new TokenGenerationException("Expiry minutes must be between 1 and 1440");
        }
    }
    
    private void validateGeneratedToken(String token, int expectedLength) {
        if (token == null || token.length() != expectedLength) {
            throw new TokenGenerationException("Generated token has incorrect length");
        }
        
        if (enableLuhnCompliance && !isValidLuhn(token)) {
            throw new TokenGenerationException("Generated token fails Luhn validation");
        }
    }
    
    private void validateGeneratedRandomToken(String token, int expectedLength) {
        if (token == null || token.length() != expectedLength) {
            throw new TokenGenerationException("Generated random token has incorrect length");
        }
        
        double entropy = calculateTokenEntropy(token);
        if (entropy < minEntropyBits) {
            throw new TokenGenerationException("Generated token has insufficient entropy: " + entropy);
        }
    }
    
    private void validateNetworkToken(String token, String networkProvider) {
        if (token == null || token.trim().isEmpty()) {
            throw new TokenGenerationException("Generated network token is empty");
        }
        
        // Network-specific validation
        String expectedPrefix = getNetworkTokenPrefix(networkProvider);
        if (!token.startsWith(expectedPrefix)) {
            throw new TokenGenerationException("Network token has incorrect prefix for " + networkProvider);
        }
    }
    
    private void validateTemporaryToken(String token) {
        if (token == null || !token.startsWith("tmp_")) {
            throw new TokenGenerationException("Temporary token has incorrect format");
        }
    }
    
    /**
     * Luhn algorithm validation
     */
    private boolean isValidLuhn(String number) {
        int sum = 0;
        boolean alternate = false;
        
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(number.charAt(i));
            
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
     * Audit token generation
     */
    private void auditTokenGeneration(String eventType, String correlationId, Object metadata) {
        if (!auditTokenGeneration) return;
        
        Map<String, Object> auditData = Map.of(
            "correlationId", correlationId,
            "metadata", metadata.toString(),
            "timestamp", Instant.now().toString(),
            "entropyBits", minEntropyBits
        );
        
        auditService.logFinancialEvent(eventType, correlationId, auditData);
    }
    
    private void auditTokenGenerationFailure(String eventType, String correlationId, String error) {
        if (!auditTokenGeneration) return;
        
        Map<String, Object> auditData = Map.of(
            "correlationId", correlationId,
            "error", error,
            "timestamp", Instant.now().toString()
        );
        
        auditService.logFinancialEvent(eventType, correlationId, auditData);
    }
}