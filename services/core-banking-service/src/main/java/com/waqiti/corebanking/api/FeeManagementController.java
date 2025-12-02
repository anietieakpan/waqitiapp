package com.waqiti.corebanking.api;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.domain.FeeSchedule;
import com.waqiti.corebanking.domain.Transaction;
import com.waqiti.corebanking.service.FeeCalculationService;
import com.waqiti.corebanking.repository.FeeScheduleRepository;
import com.waqiti.corebanking.repository.AccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Fee Management API Controller
 * 
 * Provides endpoints for managing fee schedules, calculating fees,
 * and applying fees to accounts and transactions.
 */
@RestController
@RequestMapping("/api/v1/fees")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fee Management", description = "Fee calculation and management operations")
public class FeeManagementController {
    
    private final FeeCalculationService feeCalculationService;
    private final FeeScheduleRepository feeScheduleRepository;
    private final AccountRepository accountRepository;
    
    /**
     * Calculate fee for a transaction (preview)
     */
    @PostMapping("/calculate/transaction")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Calculate transaction fee", 
               description = "Calculates fee for a transaction without applying it")
    public ResponseEntity<FeeCalculationResponse> calculateTransactionFee(
            @RequestBody FeeCalculationRequest request) {
        
        log.info("Fee calculation requested for account: {}, amount: {}", 
            request.getAccountId(), request.getAmount());
        
        try {
            Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found"));
            
            // Create a mock transaction for fee calculation
            Transaction mockTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .accountId(account.getId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .type(request.getTransactionType())
                .build();
            
            BigDecimal calculatedFee = feeCalculationService.calculateTransactionFee(mockTransaction, account);
            
            return ResponseEntity.ok(FeeCalculationResponse.builder()
                .accountId(request.getAccountId())
                .transactionAmount(request.getAmount())
                .calculatedFee(calculatedFee)
                .currency(request.getCurrency())
                .feeType("TRANSACTION_FEE")
                .calculationTimestamp(LocalDateTime.now())
                .build());
                
        } catch (Exception e) {
            log.error("Error calculating transaction fee", e);
            return ResponseEntity.badRequest()
                .body(FeeCalculationResponse.builder()
                    .error(e.getMessage())
                    .build());
        }
    }
    
    /**
     * Calculate maintenance fee for an account
     */
    @GetMapping("/calculate/maintenance/{accountId}")
    @PreAuthorize("hasRole('USER') and @accountService.isAccountOwner(#accountId, authentication.principal.id)")
    @Operation(summary = "Calculate maintenance fee", 
               description = "Calculates monthly maintenance fee for account")
    public ResponseEntity<FeeCalculationResponse> calculateMaintenanceFee(
            @Parameter(description = "Account ID") @PathVariable UUID accountId) {
        
        log.info("Maintenance fee calculation requested for account: {}", accountId);
        
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
            
            BigDecimal maintenanceFee = feeCalculationService.calculateMaintenanceFee(
                account, FeeSchedule.PeriodType.MONTHLY);
            
            return ResponseEntity.ok(FeeCalculationResponse.builder()
                .accountId(accountId)
                .calculatedFee(maintenanceFee)
                .currency(account.getCurrency())
                .feeType("MAINTENANCE_FEE")
                .calculationTimestamp(LocalDateTime.now())
                .build());
                
        } catch (Exception e) {
            log.error("Error calculating maintenance fee for account: {}", accountId, e);
            return ResponseEntity.badRequest()
                .body(FeeCalculationResponse.builder()
                    .accountId(accountId)
                    .error(e.getMessage())
                    .build());
        }
    }
    
    /**
     * Apply fee to an account (Admin only)
     */
    @PostMapping("/apply")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Apply fee to account", 
               description = "Manually applies a fee to an account")
    public ResponseEntity<FeeApplicationResponse> applyFee(
            @RequestBody FeeApplicationRequest request) {
        
        log.info("Manual fee application requested: {} to account {}", 
            request.getFeeAmount(), request.getAccountId());
        
        try {
            Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found"));
            
            Transaction feeTransaction = feeCalculationService.applyFee(
                account, 
                request.getFeeAmount(),
                request.getFeeType(),
                request.getDescription(),
                request.getRelatedTransactionId()
            );
            
            return ResponseEntity.ok(FeeApplicationResponse.builder()
                .accountId(request.getAccountId())
                .appliedFee(request.getFeeAmount())
                .feeTransactionId(feeTransaction != null ? feeTransaction.getId() : null)
                .applicationTimestamp(LocalDateTime.now())
                .status("SUCCESS")
                .build());
                
        } catch (Exception e) {
            log.error("Error applying fee to account: {}", request.getAccountId(), e);
            return ResponseEntity.badRequest()
                .body(FeeApplicationResponse.builder()
                    .accountId(request.getAccountId())
                    .error(e.getMessage())
                    .status("FAILED")
                    .build());
        }
    }
    
    /**
     * Get all fee schedules (Admin only)
     */
    @GetMapping("/schedules")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get fee schedules", 
               description = "Retrieves all fee schedules with pagination")
    public ResponseEntity<Page<FeeSchedule>> getFeeSchedules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) FeeSchedule.FeeScheduleStatus status) {
        
        try {
            Page<FeeSchedule> schedules;
            
            if (status != null) {
                schedules = feeScheduleRepository.findAll(PageRequest.of(page, size));
                // Filter would be better done in repository with proper query
            } else {
                schedules = feeScheduleRepository.findAll(PageRequest.of(page, size));
            }
            
            return ResponseEntity.ok(schedules);
            
        } catch (Exception e) {
            log.error("Error retrieving fee schedules", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Create new fee schedule (Admin only)
     */
    @PostMapping("/schedules")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create fee schedule", 
               description = "Creates a new fee schedule")
    public ResponseEntity<FeeSchedule> createFeeSchedule(
            @RequestBody FeeScheduleCreateRequest request) {
        
        log.info("Creating new fee schedule: {}", request.getName());
        
        try {
            // Check if name already exists
            if (feeScheduleRepository.existsByName(request.getName())) {
                return ResponseEntity.badRequest().build();
            }
            
            FeeSchedule feeSchedule = FeeSchedule.builder()
                .name(request.getName())
                .description(request.getDescription())
                .feeType(request.getFeeType())
                .calculationMethod(request.getCalculationMethod())
                .baseAmount(request.getBaseAmount())
                .percentageRate(request.getPercentageRate())
                .minimumFee(request.getMinimumFee())
                .maximumFee(request.getMaximumFee())
                .currency(request.getCurrency())
                .effectiveDate(request.getEffectiveDate())
                .expiryDate(request.getExpiryDate())
                .status(FeeSchedule.FeeScheduleStatus.DRAFT)
                .appliesToAccountTypes(request.getAppliesToAccountTypes())
                .appliesToTransactionTypes(request.getAppliesToTransactionTypes())
                .freeTransactionsPerPeriod(request.getFreeTransactionsPerPeriod())
                .periodType(request.getPeriodType())
                .build();
            
            FeeSchedule saved = feeScheduleRepository.save(feeSchedule);
            
            log.info("Created fee schedule: {} with ID: {}", saved.getName(), saved.getId());
            return ResponseEntity.ok(saved);
            
        } catch (Exception e) {
            log.error("Error creating fee schedule", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Update fee schedule (Admin only)
     */
    @PutMapping("/schedules/{scheduleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update fee schedule", 
               description = "Updates an existing fee schedule")
    public ResponseEntity<FeeSchedule> updateFeeSchedule(
            @PathVariable UUID scheduleId,
            @RequestBody FeeScheduleUpdateRequest request) {
        
        log.info("Updating fee schedule: {}", scheduleId);
        
        try {
            FeeSchedule existing = feeScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Fee schedule not found"));
            
            // Update fields
            if (request.getDescription() != null) {
                existing.setDescription(request.getDescription());
            }
            if (request.getBaseAmount() != null) {
                existing.setBaseAmount(request.getBaseAmount());
            }
            if (request.getPercentageRate() != null) {
                existing.setPercentageRate(request.getPercentageRate());
            }
            if (request.getMinimumFee() != null) {
                existing.setMinimumFee(request.getMinimumFee());
            }
            if (request.getMaximumFee() != null) {
                existing.setMaximumFee(request.getMaximumFee());
            }
            if (request.getStatus() != null) {
                existing.setStatus(request.getStatus());
            }
            if (request.getExpiryDate() != null) {
                existing.setExpiryDate(request.getExpiryDate());
            }
            
            FeeSchedule updated = feeScheduleRepository.save(existing);
            
            log.info("Updated fee schedule: {}", scheduleId);
            return ResponseEntity.ok(updated);
            
        } catch (Exception e) {
            log.error("Error updating fee schedule: {}", scheduleId, e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get account's free transaction count
     */
    @GetMapping("/free-transactions/{accountId}")
    @PreAuthorize("hasRole('USER') and @accountService.isAccountOwner(#accountId, authentication.principal.id)")
    @Operation(summary = "Get free transaction count", 
               description = "Gets remaining free transactions for account")
    public ResponseEntity<FreeTransactionResponse> getFreeTransactionCount(
            @PathVariable UUID accountId) {
        
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
            
            // This would need to be implemented based on the account's fee schedule
            int usedTransactions = feeCalculationService.getTransactionCountForPeriod(
                account, FeeSchedule.PeriodType.MONTHLY);
                
            // Get account's fee schedule to determine free transaction limit
            // This is simplified - would need proper fee schedule lookup
            int freeLimit = 5; // Default from standard schedule
            int remaining = Math.max(0, freeLimit - usedTransactions);
            
            return ResponseEntity.ok(FreeTransactionResponse.builder()
                .accountId(accountId)
                .freeTransactionLimit(freeLimit)
                .usedTransactions(usedTransactions)
                .remainingFreeTransactions(remaining)
                .periodType("MONTHLY")
                .build());
                
        } catch (Exception e) {
            log.error("Error getting free transaction count for account: {}", accountId, e);
            return ResponseEntity.badRequest()
                .body(FreeTransactionResponse.builder()
                    .accountId(accountId)
                    .error(e.getMessage())
                    .build());
        }
    }
    
    // DTOs
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FeeCalculationRequest {
        private UUID accountId;
        private BigDecimal amount;
        private String currency;
        private Transaction.TransactionType transactionType;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FeeCalculationResponse {
        private UUID accountId;
        private BigDecimal transactionAmount;
        private BigDecimal calculatedFee;
        private String currency;
        private String feeType;
        private LocalDateTime calculationTimestamp;
        private String error;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FeeApplicationRequest {
        private UUID accountId;
        private BigDecimal feeAmount;
        private FeeSchedule.FeeType feeType;
        private String description;
        private UUID relatedTransactionId;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FeeApplicationResponse {
        private UUID accountId;
        private BigDecimal appliedFee;
        private UUID feeTransactionId;
        private LocalDateTime applicationTimestamp;
        private String status;
        private String error;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FeeScheduleCreateRequest {
        private String name;
        private String description;
        private FeeSchedule.FeeType feeType;
        private FeeSchedule.CalculationMethod calculationMethod;
        private BigDecimal baseAmount;
        private BigDecimal percentageRate;
        private BigDecimal minimumFee;
        private BigDecimal maximumFee;
        private String currency;
        private LocalDateTime effectiveDate;
        private LocalDateTime expiryDate;
        private String appliesToAccountTypes;
        private String appliesToTransactionTypes;
        private Integer freeTransactionsPerPeriod;
        private FeeSchedule.PeriodType periodType;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FeeScheduleUpdateRequest {
        private String description;
        private BigDecimal baseAmount;
        private BigDecimal percentageRate;
        private BigDecimal minimumFee;
        private BigDecimal maximumFee;
        private LocalDateTime expiryDate;
        private FeeSchedule.FeeScheduleStatus status;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FreeTransactionResponse {
        private UUID accountId;
        private Integer freeTransactionLimit;
        private Integer usedTransactions;
        private Integer remainingFreeTransactions;
        private String periodType;
        private String error;
    }
}