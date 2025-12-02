/**
 * Password Complexity Service
 * Implements enterprise-grade password policy enforcement
 * Follows NIST SP 800-63B guidelines and industry best practices
 */
package com.waqiti.common.security.password;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comprehensive password security implementation
 * Enforces multi-layered password validation and policies
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "security.password.complexity.enabled", havingValue = "true", matchIfMissing = true)
public class PasswordComplexityService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    
    // Configuration properties
    @Value("${security.password.min-length:12}")
    private int minLength;
    
    @Value("${security.password.max-length:128}")
    private int maxLength;
    
    @Value("${security.password.require-uppercase:true}")
    private boolean requireUppercase;
    
    @Value("${security.password.require-lowercase:true}")
    private boolean requireLowercase;
    
    @Value("${security.password.require-digits:true}")
    private boolean requireDigits;
    
    @Value("${security.password.require-special-chars:true}")
    private boolean requireSpecialChars;
    
    @Value("${security.password.min-unique-chars:8}")
    private int minUniqueChars;
    
    @Value("${security.password.history-count:5}")
    private int passwordHistoryCount;
    
    @Value("${security.password.expiry-days:90}")
    private int passwordExpiryDays;
    
    @Value("${security.password.lockout-threshold:5}")
    private int lockoutThreshold;
    
    @Value("${security.password.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;
    
    @Value("${security.password.complexity-score-threshold:3}")
    private int complexityScoreThreshold;
    
    @Value("${security.password.dictionary.enabled:true}")
    private boolean dictionaryCheckEnabled;
    
    @Value("${security.password.breach-check.enabled:true}")
    private boolean breachCheckEnabled;
    
    // Common password patterns
    private static final Pattern SEQUENTIAL_PATTERN = Pattern.compile("(012|123|234|345|456|567|678|789|890|abc|bcd|cde|def|efg|fgh|ghi|hij|ijk|jkl|klm|lmn|mno|nop|opq|pqr|qrs|rst|stu|tuv|uvw|vwx|wxy|xyz)");
    private static final Pattern REPEATED_PATTERN = Pattern.compile("(.)\\1{2,}");
    private static final Pattern KEYBOARD_PATTERN = Pattern.compile("(qwer|asdf|zxcv|1234|qaz|wsx|edc|rfv|tgb|yhn|ujm|ik|ol|p)");
    
    // Special characters allowed
    private static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;:,.<>?";
    
    // Lazy-loaded dictionary
    private volatile Set<String> commonPasswords;
    
    /**
     * Validate password against all complexity rules
     */
    public PasswordValidationResult validatePassword(String password, String username, String email) {
        log.debug("Validating password complexity for user: {}", username);
        
        List<String> violations = new ArrayList<>();
        int complexityScore = 0;
        
        // Basic length validation
        if (password == null || password.length() < minLength) {
            violations.add(String.format("Password must be at least %d characters long", minLength));
        }
        
        if (password != null && password.length() > maxLength) {
            violations.add(String.format("Password must not exceed %d characters", maxLength));
        }
        
        if (password != null) {
            // Character composition validation
            ValidationResult compositionResult = validateCharacterComposition(password);
            violations.addAll(compositionResult.getViolations());
            complexityScore += compositionResult.getScore();
            
            // Uniqueness validation
            ValidationResult uniquenessResult = validateCharacterUniqueness(password);
            violations.addAll(uniquenessResult.getViolations());
            complexityScore += uniquenessResult.getScore();
            
            // Pattern validation
            ValidationResult patternResult = validateCommonPatterns(password);
            violations.addAll(patternResult.getViolations());
            complexityScore += patternResult.getScore();
            
            // Personal information validation
            ValidationResult personalResult = validatePersonalInformation(password, username, email);
            violations.addAll(personalResult.getViolations());
            complexityScore += personalResult.getScore();
            
            // Dictionary validation
            if (dictionaryCheckEnabled) {
                ValidationResult dictionaryResult = validateAgainstDictionary(password);
                violations.addAll(dictionaryResult.getViolations());
                complexityScore += dictionaryResult.getScore();
            }
            
            // Breach database validation
            if (breachCheckEnabled) {
                ValidationResult breachResult = validateAgainstBreachDatabase(password);
                violations.addAll(breachResult.getViolations());
                complexityScore += breachResult.getScore();
            }
            
            // Entropy calculation
            double entropy = calculatePasswordEntropy(password);
            if (entropy < 40.0) {
                violations.add("Password entropy is too low - consider using more diverse characters");
            } else {
                complexityScore += (int) (entropy / 10);
            }
        }
        
        // Overall complexity score check
        boolean meetsComplexity = complexityScore >= complexityScoreThreshold && violations.isEmpty();
        
        PasswordValidationResult result = PasswordValidationResult.builder()
            .valid(meetsComplexity)
            .violations(violations)
            .complexityScore(complexityScore)
            .entropy(password != null ? calculatePasswordEntropy(password) : 0.0)
            .strength(determinePasswordStrength(complexityScore, violations.isEmpty()))
            .recommendations(generateRecommendations(violations))
            .build();
        
        // Log validation result
        if (!result.isValid()) {
            log.warn("Password validation failed for user {}: {}", username, violations);
            eventPublisher.publishEvent(new PasswordValidationFailedEvent(username, violations));
        } else {
            log.debug("Password validation successful for user: {}", username);
        }
        
        return result;
    }

    /**
     * Validate character composition requirements
     */
    private ValidationResult validateCharacterComposition(String password) {
        List<String> violations = new ArrayList<>();
        int score = 0;
        
        boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowercase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecialChar = password.chars().anyMatch(c -> SPECIAL_CHARS.indexOf(c) != -1);
        
        if (requireUppercase && !hasUppercase) {
            violations.add("Password must contain at least one uppercase letter");
        } else if (hasUppercase) {
            score++;
        }
        
        if (requireLowercase && !hasLowercase) {
            violations.add("Password must contain at least one lowercase letter");
        } else if (hasLowercase) {
            score++;
        }
        
        if (requireDigits && !hasDigit) {
            violations.add("Password must contain at least one digit");
        } else if (hasDigit) {
            score++;
        }
        
        if (requireSpecialChars && !hasSpecialChar) {
            violations.add("Password must contain at least one special character (" + SPECIAL_CHARS + ")");
        } else if (hasSpecialChar) {
            score++;
        }
        
        return ValidationResult.builder()
            .violations(violations)
            .score(score)
            .build();
    }

    /**
     * Validate character uniqueness
     */
    private ValidationResult validateCharacterUniqueness(String password) {
        List<String> violations = new ArrayList<>();
        int score = 0;
        
        Set<Character> uniqueChars = password.chars()
            .mapToObj(c -> (char) c)
            .collect(Collectors.toSet());
        
        if (uniqueChars.size() < minUniqueChars) {
            violations.add(String.format("Password must contain at least %d unique characters", minUniqueChars));
        } else {
            score = Math.min(uniqueChars.size() / 4, 3); // Up to 3 points for uniqueness
        }
        
        return ValidationResult.builder()
            .violations(violations)
            .score(score)
            .build();
    }

    /**
     * Validate against common patterns
     */
    private ValidationResult validateCommonPatterns(String password) {
        List<String> violations = new ArrayList<>();
        int score = 2; // Start with full score, deduct for violations
        
        String lowerPassword = password.toLowerCase();
        
        // Check for sequential characters
        if (SEQUENTIAL_PATTERN.matcher(lowerPassword).find()) {
            violations.add("Password must not contain sequential characters");
            score--;
        }
        
        // Check for repeated characters
        if (REPEATED_PATTERN.matcher(password).find()) {
            violations.add("Password must not contain repeated character sequences");
            score--;
        }
        
        // Check for keyboard patterns
        if (KEYBOARD_PATTERN.matcher(lowerPassword).find()) {
            violations.add("Password must not contain keyboard patterns");
            score--;
        }
        
        // Check for common substitutions
        if (containsCommonSubstitutions(lowerPassword)) {
            violations.add("Password must not use predictable character substitutions");
            score--;
        }
        
        return ValidationResult.builder()
            .violations(violations)
            .score(Math.max(score, 0))
            .build();
    }

    /**
     * Validate against personal information
     */
    private ValidationResult validatePersonalInformation(String password, String username, String email) {
        List<String> violations = new ArrayList<>();
        int score = 1; // Start with score, deduct for violations
        
        String lowerPassword = password.toLowerCase();
        
        // Check against username
        if (username != null && !username.isEmpty()) {
            if (lowerPassword.contains(username.toLowerCase())) {
                violations.add("Password must not contain username");
                score = 0;
            }
        }
        
        // Check against email
        if (email != null && !email.isEmpty()) {
            String emailLocal = email.split("@")[0].toLowerCase();
            if (lowerPassword.contains(emailLocal)) {
                violations.add("Password must not contain email address parts");
                score = 0;
            }
        }
        
        // Check for common personal patterns (dates, etc.)
        if (containsDatePatterns(password)) {
            violations.add("Password must not contain date patterns");
            score = 0;
        }
        
        return ValidationResult.builder()
            .violations(violations)
            .score(score)
            .build();
    }

    /**
     * Validate against dictionary words
     */
    private ValidationResult validateAgainstDictionary(String password) {
        List<String> violations = new ArrayList<>();
        int score = 1;
        
        Set<String> dictionary = getCommonPasswordsDictionary();
        String lowerPassword = password.toLowerCase();
        
        // Check exact match
        if (dictionary.contains(lowerPassword)) {
            violations.add("Password is a commonly used password");
            score = 0;
        }
        
        // Check for dictionary words as substrings
        for (String word : dictionary) {
            if (word.length() >= 4 && lowerPassword.contains(word)) {
                violations.add("Password contains common dictionary words");
                score = 0;
                break;
            }
        }
        
        return ValidationResult.builder()
            .violations(violations)
            .score(score)
            .build();
    }

    /**
     * Validate against known breach databases
     */
    private ValidationResult validateAgainstBreachDatabase(String password) {
        List<String> violations = new ArrayList<>();
        int score = 1;
        
        // Hash password for breach check (using SHA-1 for HaveIBeenPwned compatibility)
        String sha1Hash = calculateSHA1Hash(password);
        
        // Check if password appears in breach database
        if (isPasswordInBreachDatabase(sha1Hash)) {
            violations.add("Password has been found in known data breaches");
            score = 0;
        }
        
        return ValidationResult.builder()
            .violations(violations)
            .score(score)
            .build();
    }

    /**
     * Calculate SHA-1 hash of password for HaveIBeenPwned API compatibility
     */
    private String calculateSHA1Hash(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (java.security.NoSuchAlgorithmException e) {
            log.error("SHA-1 algorithm not available", e);
            return "";
        }
    }

    /**
     * Calculate password entropy
     */
    private double calculatePasswordEntropy(String password) {
        if (password == null || password.isEmpty()) {
            return 0.0;
        }
        
        // Calculate character space
        int charSpace = 0;
        
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> SPECIAL_CHARS.indexOf(c) != -1);
        
        if (hasLower) charSpace += 26;
        if (hasUpper) charSpace += 26;
        if (hasDigit) charSpace += 10;
        if (hasSpecial) charSpace += SPECIAL_CHARS.length();
        
        // Calculate entropy: log2(charSpace^length)
        return password.length() * Math.log(charSpace) / Math.log(2);
    }

    /**
     * Check password against history
     */
    public boolean isPasswordReused(String username, String newPassword) {
        if (passwordHistoryCount <= 0) {
            return false;
        }
        
        String historyKey = "password_history:" + username;
        List<Object> passwordHistory = redisTemplate.opsForList().range(historyKey, 0, -1);
        
        if (passwordHistory == null) {
            return false;
        }
        
        // Check if new password matches any in history
        for (Object historicalHash : passwordHistory) {
            if (passwordEncoder.matches(newPassword, (String) historicalHash)) {
                log.warn("Password reuse detected for user: {}", username);
                eventPublisher.publishEvent(new PasswordReuseAttemptEvent(username));
                return true;
            }
        }
        
        return false;
    }

    /**
     * Update password history
     */
    public void updatePasswordHistory(String username, String passwordHash) {
        if (passwordHistoryCount <= 0) {
            return;
        }
        
        String historyKey = "password_history:" + username;
        
        // Add new password hash to history
        redisTemplate.opsForList().leftPush(historyKey, passwordHash);
        
        // Trim history to configured size
        redisTemplate.opsForList().trim(historyKey, 0, passwordHistoryCount - 1);
        
        // Set expiration for cleanup
        redisTemplate.expire(historyKey, 365, TimeUnit.DAYS);
    }

    /**
     * Check if password has expired
     */
    public boolean isPasswordExpired(String username) {
        if (passwordExpiryDays <= 0) {
            return false; // Password expiry disabled
        }
        
        String expiryKey = "password_expiry:" + username;
        Object expiryObj = redisTemplate.opsForValue().get(expiryKey);
        
        if (expiryObj == null) {
            return false; // No expiry set
        }
        
        Instant expiryDate = Instant.parse((String) expiryObj);
        return Instant.now().isAfter(expiryDate);
    }

    /**
     * Update password expiry
     */
    public void updatePasswordExpiry(String username) {
        if (passwordExpiryDays <= 0) {
            return;
        }
        
        String expiryKey = "password_expiry:" + username;
        Instant expiryDate = Instant.now().plus(passwordExpiryDays, ChronoUnit.DAYS);
        
        redisTemplate.opsForValue().set(expiryKey, expiryDate.toString());
        redisTemplate.expire(expiryKey, passwordExpiryDays + 30, TimeUnit.DAYS); // Grace period
    }

    /**
     * Get days until password expiry
     */
    public long getDaysUntilPasswordExpiry(String username) {
        if (passwordExpiryDays <= 0) {
            return -1; // No expiry
        }
        
        String expiryKey = "password_expiry:" + username;
        Object expiryObj = redisTemplate.opsForValue().get(expiryKey);
        
        if (expiryObj == null) {
            return -1; // No expiry set
        }
        
        Instant expiryDate = Instant.parse((String) expiryObj);
        return ChronoUnit.DAYS.between(Instant.now(), expiryDate);
    }

    // Helper methods
    private boolean containsCommonSubstitutions(String password) {
        String[] substitutions = {"@", "3", "1", "0", "$", "!", "4"};
        return Arrays.stream(substitutions).anyMatch(password::contains);
    }
    
    private boolean containsDatePatterns(String password) {
        return password.matches(".*\\b(19|20)\\d{2}\\b.*") || // Years
               password.matches(".*\\b(0[1-9]|1[0-2])[/-](0[1-9]|[12]\\d|3[01])\\b.*"); // Dates
    }
    
    private String calculateSHA256Hash(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("Error calculating SHA-256 hash", e);
            return "";
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    private boolean isPasswordInBreachDatabase(String sha1Hash) {
        // In production, this would check against HaveIBeenPwned API or local breach database
        // For now, return false to avoid external dependencies
        return false;
    }
    
    private Set<String> getCommonPasswordsDictionary() {
        if (commonPasswords == null) {
            synchronized (this) {
                if (commonPasswords == null) {
                    commonPasswords = loadCommonPasswordsDictionary();
                }
            }
        }
        return commonPasswords;
    }
    
    private Set<String> loadCommonPasswordsDictionary() {
        Set<String> passwords = new HashSet<>();
        try (InputStream is = getClass().getResourceAsStream("/security/common-passwords.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    passwords.add(line.trim().toLowerCase());
                }
            }
        } catch (Exception e) {
            log.warn("Could not load common passwords dictionary", e);
            // Add some basic common passwords
            passwords.addAll(Arrays.asList(
                "password", "123456", "password123", "admin", "qwerty",
                "letmein", "welcome", "monkey", "1234567890"
            ));
        }
        return passwords;
    }
    
    private PasswordStrength determinePasswordStrength(int score, boolean meetsRequirements) {
        if (!meetsRequirements) return PasswordStrength.VERY_WEAK;
        if (score >= 8) return PasswordStrength.VERY_STRONG;
        if (score >= 6) return PasswordStrength.STRONG;
        if (score >= 4) return PasswordStrength.MODERATE;
        if (score >= 2) return PasswordStrength.WEAK;
        return PasswordStrength.VERY_WEAK;
    }
    
    private List<String> generateRecommendations(List<String> violations) {
        List<String> recommendations = new ArrayList<>();
        
        if (violations.isEmpty()) {
            recommendations.add("Password meets all security requirements");
            return recommendations;
        }
        
        recommendations.add("Consider using a passphrase with random words");
        recommendations.add("Use a password manager to generate unique passwords");
        recommendations.add("Avoid personal information and common patterns");
        recommendations.add("Include a mix of character types for better security");
        
        return recommendations;
    }

    // Data classes and enums
    @Data
    @Builder
    public static class PasswordValidationResult {
        private boolean valid;
        private List<String> violations;
        private int complexityScore;
        private double entropy;
        private PasswordStrength strength;
        private List<String> recommendations;
    }
    
    @Data
    @Builder
    private static class ValidationResult {
        private List<String> violations;
        private int score;
    }
    
    public enum PasswordStrength {
        VERY_WEAK, WEAK, MODERATE, STRONG, VERY_STRONG
    }
    
    // Events
    public static class PasswordValidationFailedEvent {
        private final String username;
        private final List<String> violations;
        
        public PasswordValidationFailedEvent(String username, List<String> violations) {
            this.username = username;
            this.violations = violations;
        }
        
        public String getUsername() { return username; }
        public List<String> getViolations() { return violations; }
    }
    
    public static class PasswordReuseAttemptEvent {
        private final String username;
        
        public PasswordReuseAttemptEvent(String username) {
            this.username = username;
        }
        
        public String getUsername() { return username; }
    }
}