package com.waqiti.account.service;

import com.waqiti.account.domain.Account;
import com.waqiti.account.domain.AccountNumberPattern;
import com.waqiti.account.domain.AccountNumberTemplate;
import com.waqiti.account.domain.Region;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.repository.AccountNumberPatternRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Account Number Generator Service
 * 
 * Generates unique account numbers based on account type and validation rules.
 * Account number format: [TYPE_PREFIX][YEAR][SEQUENCE][CHECK_DIGIT]
 * Example: WLT2024000012349 (Wallet account created in 2024)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountNumberGeneratorService {
    
    private final AccountRepository accountRepository;
    private final AccountNumberPatternRepository patternRepository;
    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("dd");
    
    // Cache for patterns to avoid database lookups
    private final Map<String, AccountNumberPattern> patternCache = new HashMap<>();
    private volatile long lastCacheUpdate = 0;
    private static final long CACHE_TTL = 300000; // 5 minutes
    
    // Account type prefixes
    private static final String WALLET_PREFIX = "WLT";
    private static final String SAVINGS_PREFIX = "SAV";
    private static final String CREDIT_PREFIX = "CRD";
    private static final String BUSINESS_PREFIX = "BIZ";
    private static final String MERCHANT_PREFIX = "MER";
    private static final String SYSTEM_PREFIX = "SYS";
    private static final String NOSTRO_PREFIX = "NOS";
    
    /**
     * Generates a unique account number for the given account type
     */
    public String generateAccountNumber(String accountType) {
        return generateAccountNumber(accountType, null, null);
    }
    
    /**
     * Generates a unique account number using custom patterns
     * @param accountType The type of account
     * @param region Optional region for region-specific patterns
     * @param customPattern Optional custom pattern to override defaults
     */
    public String generateAccountNumber(String accountType, Region region, String customPattern) {
        AccountNumberPattern pattern = getAccountPattern(accountType, region, customPattern);
        
        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            String accountNumber = generateNumberFromPattern(pattern);
            
            // Verify uniqueness
            if (!accountRepository.existsByAccountNumber(accountNumber)) {
                log.info("Generated account number: {} for type: {} using pattern: {}", 
                        accountNumber, accountType, pattern.getPattern());
                return accountNumber;
            }
        }
        
        throw new RuntimeException("Failed to generate unique account number after " + maxAttempts + " attempts");
    }
    
    /**
     * Generates account number from template pattern
     */
    public String generateNumberFromPattern(AccountNumberPattern pattern) {
        String template = pattern.getPattern();
        LocalDateTime now = LocalDateTime.now();
        
        // Replace placeholders with actual values
        String result = template
                .replace("{PREFIX}", pattern.getPrefix())
                .replace("{YYYY}", now.format(YEAR_FORMATTER))
                .replace("{YY}", now.format(DateTimeFormatter.ofPattern("yy")))
                .replace("{MM}", now.format(MONTH_FORMATTER))
                .replace("{DD}", now.format(DAY_FORMATTER))
                .replace("{BRANCH}", pattern.getBranchCode() != null ? pattern.getBranchCode() : "000")
                .replace("{REGION}", pattern.getRegionCode() != null ? pattern.getRegionCode() : "00")
                .replace("{CURRENCY}", pattern.getCurrencyCode() != null ? pattern.getCurrencyCode() : "USD");
        
        // Handle random sequences
        result = replaceRandomSequences(result);
        
        // Calculate and append check digit if required
        if (pattern.isIncludeCheckDigit()) {
            String checkDigit = calculateCheckDigit(result);
            result += checkDigit;
        }
        
        return result;
    }
    
    /**
     * Validates an account number using check digit
     */
    public boolean isValidAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return false;
        }
        
        try {
            String baseNumber = accountNumber.substring(0, accountNumber.length() - 1);
            String providedCheckDigit = accountNumber.substring(accountNumber.length() - 1);
            String calculatedCheckDigit = calculateCheckDigit(baseNumber);
            
            return providedCheckDigit.equals(calculatedCheckDigit);
        } catch (Exception e) {
            log.error("Error validating account number: {}", accountNumber, e);
            return false;
        }
    }
    
    /**
     * Gets the account type from account number
     */
    public Account.AccountType getAccountTypeFromNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 3) {
            throw new IllegalArgumentException("Invalid account number: " + accountNumber);
        }
        
        String prefix = accountNumber.substring(0, 3);
        return switch (prefix) {
            case WALLET_PREFIX -> Account.AccountType.USER_WALLET;
            case SAVINGS_PREFIX -> Account.AccountType.USER_SAVINGS;
            case CREDIT_PREFIX -> Account.AccountType.USER_CREDIT;
            case BUSINESS_PREFIX -> Account.AccountType.BUSINESS_OPERATING;
            case MERCHANT_PREFIX -> Account.AccountType.MERCHANT;
            case SYSTEM_PREFIX -> Account.AccountType.SYSTEM_ASSET;
            case NOSTRO_PREFIX -> Account.AccountType.NOSTRO;
            default -> throw new IllegalArgumentException("Unknown account number prefix: " + prefix + " in account number: " + accountNumber);
        };
    }
    
    /**
     * Generates the next account number in sequence for migrations
     */
    public String generateSequentialAccountNumber(String accountType, long sequenceNumber) {
        String prefix = getAccountTypePrefix(Account.AccountType.valueOf(accountType));
        String year = LocalDateTime.now().format(YEAR_FORMATTER);
        String sequence = String.format("%06d", sequenceNumber);
        String baseNumber = prefix + year + sequence;
        String checkDigit = calculateCheckDigit(baseNumber);
        
        return baseNumber + checkDigit;
    }
    
    // Private helper methods
    
    private String getAccountTypePrefix(Account.AccountType accountType) {
        return switch (accountType) {
            case USER_WALLET -> WALLET_PREFIX;
            case USER_SAVINGS -> SAVINGS_PREFIX;
            case USER_CREDIT -> CREDIT_PREFIX;
            case BUSINESS_OPERATING, BUSINESS_ESCROW -> BUSINESS_PREFIX;
            case MERCHANT -> MERCHANT_PREFIX;
            case SYSTEM_ASSET, SYSTEM_LIABILITY, FEE_COLLECTION, 
                 SUSPENSE, TRANSIT, RESERVE -> SYSTEM_PREFIX;
            case NOSTRO -> NOSTRO_PREFIX;
        };
    }
    
    private String generateSequence() {
        // Generate 6-digit random sequence
        int sequence = ThreadLocalRandom.current().nextInt(100000, 999999);
        return String.format("%06d", sequence);
    }
    
    /**
     * Calculates check digit using Luhn algorithm
     */
    private String calculateCheckDigit(String baseNumber) {
        int sum = 0;
        boolean alternate = false;
        
        // Process digits from right to left
        for (int i = baseNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(baseNumber.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        int checkDigit = (10 - (sum % 10)) % 10;
        return String.valueOf(checkDigit);
    }
    
    /**
     * Formats account number for display (adds dashes for readability)
     */
    public String formatAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 10) {
            return accountNumber;
        }
        
        // Format: XXX-XXXX-XXXXXX-X
        return accountNumber.substring(0, 3) + "-" +
               accountNumber.substring(3, 7) + "-" +
               accountNumber.substring(7, accountNumber.length() - 1) + "-" +
               accountNumber.substring(accountNumber.length() - 1);
    }
    
    /**
     * Removes formatting from account number
     */
    public String unformatAccountNumber(String formattedAccountNumber) {
        if (formattedAccountNumber == null) {
            return null;
        }
        return formattedAccountNumber.replaceAll("-", "");
    }
    
    // =======================
    // Custom Pattern Methods
    // =======================
    
    /**
     * Creates a new custom account number pattern
     */
    public AccountNumberPattern createCustomPattern(AccountNumberTemplate template) {
        AccountNumberPattern pattern = AccountNumberPattern.builder()
                .id(UUID.randomUUID().toString())
                .accountType(template.getAccountType())
                .region(template.getRegion())
                .pattern(template.getPattern())
                .prefix(template.getPrefix())
                .branchCode(template.getBranchCode())
                .regionCode(template.getRegionCode())
                .currencyCode(template.getCurrencyCode())
                .includeCheckDigit(template.isIncludeCheckDigit())
                .minLength(template.getMinLength())
                .maxLength(template.getMaxLength())
                .validationRegex(template.getValidationRegex())
                .description(template.getDescription())
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        
        AccountNumberPattern savedPattern = patternRepository.save(pattern);
        clearPatternCache(); // Clear cache to reload patterns
        
        log.info("Created custom account number pattern: {}", savedPattern.getId());
        return savedPattern;
    }
    
    /**
     * Updates an existing custom pattern
     */
    public AccountNumberPattern updateCustomPattern(String patternId, AccountNumberTemplate template) {
        AccountNumberPattern pattern = patternRepository.findById(patternId)
                .orElseThrow(() -> new IllegalArgumentException("Pattern not found: " + patternId));
        
        pattern.setPattern(template.getPattern());
        pattern.setPrefix(template.getPrefix());
        pattern.setBranchCode(template.getBranchCode());
        pattern.setRegionCode(template.getRegionCode());
        pattern.setCurrencyCode(template.getCurrencyCode());
        pattern.setIncludeCheckDigit(template.isIncludeCheckDigit());
        pattern.setMinLength(template.getMinLength());
        pattern.setMaxLength(template.getMaxLength());
        pattern.setValidationRegex(template.getValidationRegex());
        pattern.setDescription(template.getDescription());
        pattern.setUpdatedAt(LocalDateTime.now());
        
        AccountNumberPattern savedPattern = patternRepository.save(pattern);
        clearPatternCache(); // Clear cache to reload patterns
        
        log.info("Updated custom account number pattern: {}", savedPattern.getId());
        return savedPattern;
    }
    
    /**
     * Deactivates a custom pattern
     */
    public void deactivateCustomPattern(String patternId) {
        AccountNumberPattern pattern = patternRepository.findById(patternId)
                .orElseThrow(() -> new IllegalArgumentException("Pattern not found: " + patternId));
        
        pattern.setActive(false);
        pattern.setUpdatedAt(LocalDateTime.now());
        patternRepository.save(pattern);
        clearPatternCache();
        
        log.info("Deactivated custom account number pattern: {}", patternId);
    }
    
    /**
     * Gets all active patterns for an account type
     */
    public List<AccountNumberPattern> getActivePatterns(Account.AccountType accountType) {
        refreshPatternCache();
        return patternRepository.findByAccountTypeAndActiveTrue(accountType);
    }
    
    /**
     * Validates a custom pattern template
     */
    public boolean validatePatternTemplate(AccountNumberTemplate template) {
        try {
            // Create a temporary pattern to test generation
            AccountNumberPattern testPattern = AccountNumberPattern.builder()
                    .pattern(template.getPattern())
                    .prefix(template.getPrefix())
                    .branchCode(template.getBranchCode())
                    .regionCode(template.getRegionCode())
                    .currencyCode(template.getCurrencyCode())
                    .includeCheckDigit(template.isIncludeCheckDigit())
                    .minLength(template.getMinLength())
                    .maxLength(template.getMaxLength())
                    .validationRegex(template.getValidationRegex())
                    .build();
            
            // Try to generate a number from the pattern
            String testNumber = generateNumberFromPattern(testPattern);
            
            // Validate length constraints
            if (testNumber.length() < template.getMinLength() || 
                testNumber.length() > template.getMaxLength()) {
                return false;
            }
            
            // Validate regex if provided
            if (template.getValidationRegex() != null && !template.getValidationRegex().isEmpty()) {
                return Pattern.matches(template.getValidationRegex(), testNumber);
            }
            
            return true;
            
        } catch (Exception e) {
            log.warn("Invalid pattern template: {}", template.getPattern(), e);
            return false;
        }
    }
    
    // =======================
    // Private Helper Methods
    // =======================
    
    private AccountNumberPattern getAccountPattern(String accountType, Region region, String customPattern) {
        refreshPatternCache();
        
        // First try custom pattern if provided
        if (customPattern != null && !customPattern.isEmpty()) {
            return AccountNumberPattern.builder()
                    .pattern(customPattern)
                    .prefix(getAccountTypePrefix(Account.AccountType.valueOf(accountType)))
                    .includeCheckDigit(true)
                    .minLength(10)
                    .maxLength(20)
                    .build();
        }
        
        // Try to find region-specific pattern
        String cacheKey = accountType + "_" + (region != null ? region.getCode() : "DEFAULT");
        AccountNumberPattern pattern = patternCache.get(cacheKey);
        
        if (pattern != null) {
            return pattern;
        }
        
        // Try database lookup
        Account.AccountType type = Account.AccountType.valueOf(accountType);
        Optional<AccountNumberPattern> dbPattern = patternRepository
                .findByAccountTypeAndRegionAndActiveTrue(type, region);
        
        if (dbPattern.isPresent()) {
            pattern = dbPattern.get();
            patternCache.put(cacheKey, pattern);
            return pattern;
        }
        
        // Fall back to default pattern
        pattern = createDefaultPattern(type, region);
        patternCache.put(cacheKey, pattern);
        return pattern;
    }
    
    private AccountNumberPattern createDefaultPattern(Account.AccountType accountType, Region region) {
        String prefix = getAccountTypePrefix(accountType);
        String regionCode = region != null ? region.getCode() : "00";
        
        return AccountNumberPattern.builder()
                .pattern("{PREFIX}{YY}{REGION}{RANDOM6}")
                .prefix(prefix)
                .regionCode(regionCode)
                .includeCheckDigit(true)
                .minLength(10)
                .maxLength(15)
                .description("Default pattern for " + accountType)
                .build();
    }
    
    private String replaceRandomSequences(String template) {
        String result = template;
        
        // Replace {RANDOMN} patterns
        java.util.regex.Pattern randomPattern = java.util.regex.Pattern.compile("\\{RANDOM(\\d+)\\}");
        java.util.regex.Matcher matcher = randomPattern.matcher(result);
        
        while (matcher.find()) {
            int length = Integer.parseInt(matcher.group(1));
            String randomSequence = generateRandomSequence(length);
            result = result.replace(matcher.group(), randomSequence);
        }
        
        // Replace {SEQUENCE} with 6-digit default
        result = result.replace("{SEQUENCE}", generateSequence());
        
        return result;
    }
    
    private String generateRandomSequence(int length) {
        StringBuilder sequence = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sequence.append(ThreadLocalRandom.current().nextInt(0, 10));
        }
        return sequence.toString();
    }
    
    private void refreshPatternCache() {
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate > CACHE_TTL) {
            clearPatternCache();
            loadPatternCache();
            lastCacheUpdate = now;
        }
    }
    
    private void clearPatternCache() {
        patternCache.clear();
        lastCacheUpdate = 0;
    }
    
    private void loadPatternCache() {
        List<AccountNumberPattern> activePatterns = patternRepository.findByActiveTrue();
        for (AccountNumberPattern pattern : activePatterns) {
            String cacheKey = pattern.getAccountType() + "_" + 
                            (pattern.getRegion() != null ? pattern.getRegion().getCode() : "DEFAULT");
            patternCache.put(cacheKey, pattern);
        }
        log.debug("Loaded {} patterns into cache", activePatterns.size());
    }
}