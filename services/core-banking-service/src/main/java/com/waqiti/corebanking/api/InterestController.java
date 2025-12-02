package com.waqiti.corebanking.api;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.service.InterestCalculationService;
import com.waqiti.corebanking.repository.AccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Interest Management API
 * 
 * Provides endpoints for interest calculation management
 */
@RestController
@RequestMapping("/api/v1/interest")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Interest Management", description = "Interest calculation and management operations")
public class InterestController {
    
    private final InterestCalculationService interestCalculationService;
    private final AccountRepository accountRepository;
    
    /**
     * Manually trigger interest calculation for all eligible accounts
     * Admin only operation
     */
    @PostMapping("/calculate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Manually trigger interest calculation", 
               description = "Triggers interest calculation for all eligible accounts")
    public ResponseEntity<InterestCalculationResponse> triggerInterestCalculation() {
        log.info("Manual interest calculation triggered by admin");
        
        try {
            interestCalculationService.calculateDailyInterest();
            
            return ResponseEntity.ok(InterestCalculationResponse.builder()
                .status("SUCCESS")
                .message("Interest calculation completed successfully")
                .timestamp(java.time.LocalDateTime.now())
                .build());
        } catch (Exception e) {
            log.error("Error during manual interest calculation", e);
            return ResponseEntity.internalServerError()
                .body(InterestCalculationResponse.builder()
                    .status("ERROR")
                    .message("Interest calculation failed: " + e.getMessage())
                    .timestamp(java.time.LocalDateTime.now())
                    .build());
        }
    }
    
    /**
     * Calculate interest for a specific account
     * Returns the calculated amount without applying it
     */
    @GetMapping("/calculate/{accountId}")
    @PreAuthorize("hasRole('USER') and @accountService.isAccountOwner(#accountId, authentication.principal.id)")
    @Operation(summary = "Calculate interest for account", 
               description = "Calculates interest for a specific account without applying it")
    public ResponseEntity<InterestPreviewResponse> calculateInterestForAccount(
            @Parameter(description = "Account ID") @PathVariable UUID accountId) {
        
        log.info("Interest calculation preview requested for account: {}", accountId);
        
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
            
            BigDecimal interestAmount = interestCalculationService.calculateInterest(account);
            
            return ResponseEntity.ok(InterestPreviewResponse.builder()
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .currentBalance(account.getCurrentBalance())
                .interestRate(account.getInterestRate())
                .calculationMethod(account.getInterestCalculationMethod() != null ? 
                    account.getInterestCalculationMethod().toString() : "DAILY_BALANCE")
                .calculatedInterest(interestAmount)
                .lastCalculationDate(account.getLastInterestCalculationDate())
                .build());
                
        } catch (Exception e) {
            log.error("Error calculating interest for account: {}", accountId, e);
            return ResponseEntity.badRequest()
                .body(InterestPreviewResponse.builder()
                    .accountId(accountId)
                    .error(e.getMessage())
                    .build());
        }
    }
    
    /**
     * Update interest rate for an account
     * Admin only operation
     */
    @PutMapping("/rate/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update account interest rate", 
               description = "Updates the interest rate for a specific account")
    public ResponseEntity<InterestRateUpdateResponse> updateInterestRate(
            @Parameter(description = "Account ID") @PathVariable UUID accountId,
            @Parameter(description = "New interest rate") @RequestBody InterestRateUpdateRequest request) {
        
        log.info("Interest rate update requested for account: {} to rate: {}", accountId, request.getInterestRate());
        
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
            
            BigDecimal oldRate = account.getInterestRate();
            account.setInterestRate(request.getInterestRate());
            
            if (request.getCalculationMethod() != null) {
                account.setInterestCalculationMethod(request.getCalculationMethod());
            }
            
            accountRepository.save(account);
            
            return ResponseEntity.ok(InterestRateUpdateResponse.builder()
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .oldInterestRate(oldRate)
                .newInterestRate(request.getInterestRate())
                .calculationMethod(account.getInterestCalculationMethod() != null ? 
                    account.getInterestCalculationMethod().toString() : "DAILY_BALANCE")
                .updateTimestamp(java.time.LocalDateTime.now())
                .build());
                
        } catch (Exception e) {
            log.error("Error updating interest rate for account: {}", accountId, e);
            return ResponseEntity.badRequest()
                .body(InterestRateUpdateResponse.builder()
                    .accountId(accountId)
                    .error(e.getMessage())
                    .build());
        }
    }
    
    // DTOs
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class InterestCalculationResponse {
        private String status;
        private String message;
        private java.time.LocalDateTime timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class InterestPreviewResponse {
        private UUID accountId;
        private String accountNumber;
        private BigDecimal currentBalance;
        private BigDecimal interestRate;
        private String calculationMethod;
        private BigDecimal calculatedInterest;
        private java.time.LocalDate lastCalculationDate;
        private String error;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class InterestRateUpdateRequest {
        private BigDecimal interestRate;
        private Account.InterestCalculationMethod calculationMethod;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class InterestRateUpdateResponse {
        private UUID accountId;
        private String accountNumber;
        private BigDecimal oldInterestRate;
        private BigDecimal newInterestRate;
        private String calculationMethod;
        private java.time.LocalDateTime updateTimestamp;
        private String error;
    }
}