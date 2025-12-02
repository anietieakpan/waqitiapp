package com.waqiti.account.controller;

import com.waqiti.account.domain.Account;
import com.waqiti.account.domain.AccountNumberPattern;
import com.waqiti.account.domain.AccountNumberTemplate;
import com.waqiti.account.domain.Region;
import com.waqiti.account.service.AccountNumberGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for managing custom account number patterns
 */
@RestController
@RequestMapping("/api/v1/account-patterns")
@RequiredArgsConstructor
@Slf4j
public class AccountNumberPatternController {
    
    private final AccountNumberGeneratorService generatorService;
    
    /**
     * Create a new custom account number pattern
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACCOUNT_MANAGER')")
    public ResponseEntity<AccountNumberPattern> createPattern(@Valid @RequestBody AccountNumberTemplate template) {
        log.info("Creating new account number pattern for type: {}", template.getAccountType());
        
        AccountNumberPattern pattern = generatorService.createCustomPattern(template);
        return ResponseEntity.ok(pattern);
    }
    
    /**
     * Update an existing account number pattern
     */
    @PutMapping("/{patternId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACCOUNT_MANAGER')")
    public ResponseEntity<AccountNumberPattern> updatePattern(
            @PathVariable String patternId,
            @Valid @RequestBody AccountNumberTemplate template) {
        
        log.info("Updating account number pattern: {}", patternId);
        
        AccountNumberPattern pattern = generatorService.updateCustomPattern(patternId, template);
        return ResponseEntity.ok(pattern);
    }
    
    /**
     * Deactivate an account number pattern
     */
    @DeleteMapping("/{patternId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivatePattern(@PathVariable String patternId) {
        log.info("Deactivating account number pattern: {}", patternId);
        
        generatorService.deactivateCustomPattern(patternId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Get all active patterns for an account type
     */
    @GetMapping("/account-type/{accountType}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACCOUNT_MANAGER') or hasRole('USER')")
    public ResponseEntity<List<AccountNumberPattern>> getPatternsByAccountType(
            @PathVariable Account.AccountType accountType) {
        
        List<AccountNumberPattern> patterns = generatorService.getActivePatterns(accountType);
        return ResponseEntity.ok(patterns);
    }
    
    /**
     * Validate a pattern template before creating
     */
    @PostMapping("/validate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACCOUNT_MANAGER')")
    public ResponseEntity<Map<String, Object>> validatePattern(@Valid @RequestBody AccountNumberTemplate template) {
        log.debug("Validating pattern template: {}", template.getPattern());
        
        boolean isValid = generatorService.validatePatternTemplate(template);
        
        Map<String, Object> response = new HashMap<>();
        response.put("valid", isValid);
        
        if (isValid) {
            // Generate a sample account number to show the result
            try {
                AccountNumberPattern testPattern = AccountNumberPattern.builder()
                        .pattern(template.getPattern())
                        .prefix(template.getPrefix())
                        .branchCode(template.getBranchCode())
                        .regionCode(template.getRegionCode())
                        .currencyCode(template.getCurrencyCode())
                        .includeCheckDigit(template.isIncludeCheckDigit())
                        .build();
                
                String sampleNumber = generatorService.generateNumberFromPattern(testPattern);
                response.put("sampleNumber", sampleNumber);
            } catch (Exception e) {
                response.put("valid", false);
                response.put("error", "Failed to generate sample: " + e.getMessage());
            }
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Generate a test account number using custom pattern
     */
    @PostMapping("/test-generate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACCOUNT_MANAGER')")
    public ResponseEntity<Map<String, Object>> testGenerate(
            @RequestParam Account.AccountType accountType,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String customPattern) {
        
        log.debug("Testing account number generation for type: {}", accountType);
        
        try {
            Region region = null; // In a real implementation, lookup region by code
            String accountNumber = generatorService.generateAccountNumber(
                    accountType.toString(), region, customPattern);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("accountNumber", accountNumber);
            response.put("accountType", accountType);
            response.put("formatted", generatorService.formatAccountNumber(accountNumber));
            response.put("valid", generatorService.isValidAccountNumber(accountNumber));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to generate test account number", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get predefined pattern templates
     */
    @GetMapping("/templates")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACCOUNT_MANAGER')")
    public ResponseEntity<Map<String, String>> getPatternTemplates() {
        Map<String, String> templates = new HashMap<>();
        templates.put("standard", AccountNumberTemplate.CommonPatterns.STANDARD);
        templates.put("withBranch", AccountNumberTemplate.CommonPatterns.WITH_BRANCH);
        templates.put("currencyPrefix", AccountNumberTemplate.CommonPatterns.CURRENCY_PREFIX);
        templates.put("dateBased", AccountNumberTemplate.CommonPatterns.DATE_BASED);
        templates.put("sequential", AccountNumberTemplate.CommonPatterns.SEQUENTIAL);
        templates.put("hierarchical", AccountNumberTemplate.CommonPatterns.HIERARCHICAL);
        templates.put("isoFormat", AccountNumberTemplate.CommonPatterns.ISO_FORMAT);
        templates.put("compact", AccountNumberTemplate.CommonPatterns.COMPACT);
        
        return ResponseEntity.ok(templates);
    }
    
    /**
     * Get pattern placeholder documentation
     */
    @GetMapping("/placeholders")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACCOUNT_MANAGER')")
    public ResponseEntity<Map<String, Object>> getPatternPlaceholders() {
        Map<String, Object> placeholders = new HashMap<>();
        
        Map<String, String> dateTime = new HashMap<>();
        dateTime.put("{YYYY}", "4-digit year (e.g., 2024)");
        dateTime.put("{YY}", "2-digit year (e.g., 24)");
        dateTime.put("{MM}", "2-digit month (e.g., 07)");
        dateTime.put("{DD}", "2-digit day (e.g., 15)");
        
        Map<String, String> location = new HashMap<>();
        location.put("{PREFIX}", "Account type prefix");
        location.put("{BRANCH}", "Branch code");
        location.put("{REGION}", "Region code");
        location.put("{CURRENCY}", "Currency code");
        
        Map<String, String> random = new HashMap<>();
        random.put("{RANDOM4}", "4-digit random number");
        random.put("{RANDOM6}", "6-digit random number");
        random.put("{RANDOM8}", "8-digit random number");
        random.put("{SEQUENCE}", "6-digit sequential number");
        
        placeholders.put("dateTime", dateTime);
        placeholders.put("location", location);
        placeholders.put("random", random);
        
        Map<String, String> examples = new HashMap<>();
        examples.put("{PREFIX}{YYYY}{REGION}{RANDOM6}", "WLT202400123456");
        examples.put("{PREFIX}-{YY}{MM}-{BRANCH}-{RANDOM4}", "SAV-2407-001-5678");
        examples.put("{CURRENCY}{PREFIX}{RANDOM8}", "USDWLT12345678");
        
        placeholders.put("examples", examples);
        
        return ResponseEntity.ok(placeholders);
    }
    
    /**
     * Bulk validate multiple account numbers
     */
    @PostMapping("/validate-bulk")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ACCOUNT_MANAGER')")
    public ResponseEntity<Map<String, Object>> validateBulkAccountNumbers(
            @RequestBody List<String> accountNumbers) {
        
        log.info("Bulk validating {} account numbers", accountNumbers.size());
        
        Map<String, Boolean> results = new HashMap<>();
        int validCount = 0;
        
        for (String accountNumber : accountNumbers) {
            boolean isValid = generatorService.isValidAccountNumber(accountNumber);
            results.put(accountNumber, isValid);
            if (isValid) validCount++;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("total", accountNumbers.size());
        response.put("valid", validCount);
        response.put("invalid", accountNumbers.size() - validCount);
        response.put("results", results);
        
        return ResponseEntity.ok(response);
    }
}