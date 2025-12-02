package com.waqiti.account.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

/**
 * Template for creating custom account number patterns
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountNumberTemplate {
    
    @NotNull(message = "Account type is required")
    private Account.AccountType accountType;
    
    private Region region;
    
    @NotBlank(message = "Pattern is required")
    @Size(max = 100, message = "Pattern cannot exceed 100 characters")
    private String pattern;
    
    @Size(max = 10, message = "Prefix cannot exceed 10 characters")
    private String prefix;
    
    @Size(max = 10, message = "Branch code cannot exceed 10 characters")
    private String branchCode;
    
    @Size(max = 10, message = "Region code cannot exceed 10 characters")
    private String regionCode;
    
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    private String currencyCode;
    
    private boolean includeCheckDigit = true;
    
    @Min(value = 5, message = "Minimum length must be at least 5")
    @Max(value = 30, message = "Maximum length cannot exceed 30")
    private int minLength = 10;
    
    @Min(value = 5, message = "Minimum length must be at least 5")
    @Max(value = 30, message = "Maximum length cannot exceed 30")
    private int maxLength = 20;
    
    @Size(max = 200, message = "Validation regex cannot exceed 200 characters")
    private String validationRegex;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @Min(value = 0, message = "Priority cannot be negative")
    @Max(value = 100, message = "Priority cannot exceed 100")
    private int priority = 0;
    
    // Predefined pattern templates for common use cases
    public static class CommonPatterns {
        public static final String STANDARD = "{PREFIX}{YYYY}{REGION}{RANDOM6}";
        public static final String WITH_BRANCH = "{PREFIX}{YY}{BRANCH}{RANDOM4}";
        public static final String CURRENCY_PREFIX = "{CURRENCY}{PREFIX}{RANDOM8}";
        public static final String DATE_BASED = "{PREFIX}{YYYYMMDD}{RANDOM4}";
        public static final String SEQUENTIAL = "{PREFIX}{YY}{SEQUENCE}";
        public static final String HIERARCHICAL = "{REGION}{BRANCH}{PREFIX}{RANDOM4}";
        public static final String ISO_FORMAT = "{PREFIX}-{YYYY}-{REGION}-{RANDOM6}";
        public static final String COMPACT = "{PREFIX}{YY}{RANDOM8}";
    }
    
    // Validation helper methods
    
    public boolean isLengthValid() {
        return minLength <= maxLength && minLength >= 5 && maxLength <= 30;
    }
    
    public boolean hasRequiredFields() {
        return accountType != null && 
               pattern != null && 
               !pattern.trim().isEmpty();
    }
    
    public boolean isPatternValid() {
        // Basic pattern validation - check for required placeholders
        return pattern.contains("{") && pattern.contains("}");
    }
}