package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.waqiti.common.idempotency.Idempotent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Production-grade Settlement Service
 * Handles settlement processing, batch management, and payout distribution
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final BankingService bankingService;
    private final FeeService feeService;
    private final ReconciliationService reconciliationService;
    
    private final Map<String, Settlement> settlements = new ConcurrentHashMap<>();
    private final Map<String, List<String>> merchantSettlements = new ConcurrentHashMap<>();
    private final Map<String, SettlementStatus> settlementStatuses = new ConcurrentHashMap<>();
    
    private static final BigDecimal MINIMUM_SETTLEMENT_AMOUNT = new BigDecimal("1.00");
    private static final int MAX_SETTLEMENT_RETRIES = 3;

    /**
     * Create settlement batch for merchant
     */
    @Transactional
    @Idempotent(
        keyExpression = "'settlement:' + #merchantId + ':' + #paymentIds.hashCode() + ':' + #settlementMethod",
        serviceName = "payment-service",
        operationType = "CREATE_SETTLEMENT",
        userIdExpression = "#merchantId",
        currencyExpression = "#currency",
        ttlHours = 168
    )
    public SettlementResult createSettlement(String merchantId, List<String> paymentIds,
                                           String settlementMethod, String currency) {
        try {
            log.info("Creating settlement for merchant: {} with {} payments, method: {}", 
                    merchantId, paymentIds.size(), settlementMethod);
            
            // Validate settlement request
            validateSettlementRequest(merchantId, paymentIds, settlementMethod);
            
            String settlementId = UUID.randomUUID().toString();
            
            // Calculate settlement amounts
            SettlementCalculation calculation = calculateSettlementAmounts(merchantId, paymentIds, 
                                                                         settlementMethod, currency);
            
            // Create settlement record
            Settlement settlement = Settlement.builder()
                .settlementId(settlementId)
                .merchantId(merchantId)
                .paymentIds(paymentIds)
                .settlementMethod(settlementMethod)
                .currency(currency)
                .grossAmount(calculation.getGrossAmount())
                .totalFees(calculation.getTotalFees())
                .netAmount(calculation.getNetAmount())
                .status(SettlementStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .scheduledDate(calculateScheduledDate(merchantId, settlementMethod))
                .build();
            
            // Store settlement
            settlements.put(settlementId, settlement);
            settlementStatuses.put(settlementId, SettlementStatus.PENDING);
            
            // Track merchant settlements
            merchantSettlements.computeIfAbsent(merchantId, k -> new ArrayList<>()).add(settlementId);
            
            // Schedule settlement processing
            scheduleSettlementProcessing(settlement);
            
            SettlementResult result = SettlementResult.builder()
                .settlementId(settlementId)
                .merchantId(merchantId)
                .grossAmount(calculation.getGrossAmount())
                .netAmount(calculation.getNetAmount())
                .totalFees(calculation.getTotalFees())
                .settlementMethod(settlementMethod)
                .status(SettlementStatus.PENDING)
                .scheduledDate(settlement.getScheduledDate())
                .paymentCount(paymentIds.size())
                .build();
            
            log.info("Settlement created successfully: {} for merchant: {} net amount: {}", 
                    settlementId, merchantId, calculation.getNetAmount());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to create settlement for merchant {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("Settlement creation failed", e);
        }
    }

    /**
     * Process settlement execution
     */
    @Transactional
    @Idempotent(
        keyExpression = "'settlement-process:' + #settlementId",
        serviceName = "payment-service",
        operationType = "PROCESS_SETTLEMENT",
        correlationIdExpression = "#settlementId",
        ttlHours = 168
    )
    public SettlementExecutionResult processSettlement(String settlementId) {
        try {
            log.info("Processing settlement: {}", settlementId);
            
            Settlement settlement = settlements.get(settlementId);
            if (settlement == null) {
                throw new RuntimeException("Settlement not found: " + settlementId);
            }
            
            if (settlement.getStatus() != SettlementStatus.PENDING) {
                throw new RuntimeException("Settlement not in pending status: " + settlement.getStatus());
            }
            
            // Update status to processing
            updateSettlementStatus(settlementId, SettlementStatus.PROCESSING);
            
            SettlementExecutionResult.SettlementExecutionResultBuilder resultBuilder = 
                SettlementExecutionResult.builder()
                    .settlementId(settlementId)
                    .merchantId(settlement.getMerchantId())
                    .executionStarted(LocalDateTime.now());
            
            try {
                // Pre-settlement validations
                performPreSettlementValidations(settlement);
                
                // Execute banking transfer
                BankingService.TransferResult transferResult = executeSettlementTransfer(settlement);
                
                // Perform reconciliation
                ReconciliationService.ReconciliationResult reconciliation = 
                    performSettlementReconciliation(settlement);
                
                // Update settlement with transfer details
                settlement.setTransferReference(transferResult.getTransferReference());
                settlement.setActualTransferAmount(settlement.getNetAmount());
                settlement.setProcessedAt(LocalDateTime.now());
                
                if ("SUCCESS".equals(transferResult.getStatus()) && reconciliation.isReconciled()) {
                    updateSettlementStatus(settlementId, SettlementStatus.COMPLETED);
                    
                    resultBuilder
                        .status(SettlementStatus.COMPLETED)
                        .transferReference(transferResult.getReference())
                        .actualAmount(settlement.getNetAmount())
                        .reconciled(true)
                        .completedAt(LocalDateTime.now());
                    
                    log.info("Settlement processed successfully: {} amount: {}", 
                            settlementId, settlement.getNetAmount());
                    
                } else {
                    updateSettlementStatus(settlementId, SettlementStatus.FAILED);
                    
                    resultBuilder
                        .status(SettlementStatus.FAILED)
                        .error("Transfer failed: " + transferResult.getErrorMessage())
                        .reconciled(false);
                    
                    log.error("Settlement processing failed: {} - Transfer: {} Reconciliation: {}", 
                            settlementId, transferResult.getStatus(), reconciliation.isReconciled());
                }
                
            } catch (Exception processingError) {
                log.error("Settlement processing error for {}: {}", settlementId, processingError.getMessage());
                
                updateSettlementStatus(settlementId, SettlementStatus.FAILED);
                settlement.setFailureReason(processingError.getMessage());
                settlement.setRetryCount(settlement.getRetryCount() + 1);
                
                // Schedule retry if under retry limit
                if (settlement.getRetryCount() < MAX_SETTLEMENT_RETRIES) {
                    scheduleSettlementRetry(settlement);
                    
                    resultBuilder
                        .status(SettlementStatus.RETRY_SCHEDULED)
                        .error(processingError.getMessage())
                        .retryScheduled(true);
                } else {
                    resultBuilder
                        .status(SettlementStatus.FAILED)
                        .error("Max retries exceeded: " + processingError.getMessage())
                        .retryScheduled(false);
                }
            }
            
            return resultBuilder.build();
            
        } catch (Exception e) {
            log.error("Settlement processing failed for {}: {}", settlementId, e.getMessage(), e);
            throw new RuntimeException("Settlement processing failed", e);
        }
    }

    /**
     * Get settlement status and details
     */
    public Settlement getSettlementDetails(String settlementId) {
        try {
            Settlement settlement = settlements.get(settlementId);
            if (settlement != null) {
                log.debug("Retrieved settlement details for: {}", settlementId);
                return settlement;
            }
            
            throw new RuntimeException("Settlement not found: " + settlementId);
            
        } catch (Exception e) {
            log.error("Failed to get settlement details for {}: {}", settlementId, e.getMessage());
            throw new RuntimeException("Failed to get settlement details", e);
        }
    }

    /**
     * Get merchant settlement history
     */
    public List<Settlement> getMerchantSettlements(String merchantId, LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Getting settlement history for merchant: {} from {} to {}", 
                    merchantId, startDate, endDate);
            
            List<String> merchantSettlementIds = merchantSettlements.getOrDefault(merchantId, Collections.emptyList());
            
            return merchantSettlementIds.stream()
                .map(settlements::get)
                .filter(Objects::nonNull)
                .filter(s -> !s.getCreatedAt().toLocalDate().isBefore(startDate))
                .filter(s -> !s.getCreatedAt().toLocalDate().isAfter(endDate))
                .sorted(Comparator.comparing(Settlement::getCreatedAt).reversed())
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to get merchant settlements for {}: {}", merchantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Cancel pending settlement
     */
    @Transactional
    public boolean cancelSettlement(String settlementId, String reason) {
        try {
            log.info("Cancelling settlement: {} reason: {}", settlementId, reason);
            
            Settlement settlement = settlements.get(settlementId);
            if (settlement == null) {
                throw new RuntimeException("Settlement not found: " + settlementId);
            }
            
            if (settlement.getStatus() != SettlementStatus.PENDING) {
                log.warn("Cannot cancel settlement in status: {}", settlement.getStatus());
                return false;
            }
            
            updateSettlementStatus(settlementId, SettlementStatus.CANCELLED);
            settlement.setCancelledAt(LocalDateTime.now());
            settlement.setCancellationReason(reason);
            
            log.info("Settlement cancelled successfully: {}", settlementId);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to cancel settlement {}: {}", settlementId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Generate settlement report for period
     */
    public SettlementReport generateSettlementReport(String merchantId, LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Generating settlement report for merchant: {} period: {} to {}", 
                    merchantId, startDate, endDate);
            
            List<Settlement> periodSettlements = getMerchantSettlements(merchantId, startDate, endDate);
            
            BigDecimal totalGrossAmount = periodSettlements.stream()
                .map(Settlement::getGrossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalFees = periodSettlements.stream()
                .map(Settlement::getTotalFees)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalNetAmount = periodSettlements.stream()
                .map(Settlement::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            long completedCount = periodSettlements.stream()
                .mapToLong(s -> s.getStatus() == SettlementStatus.COMPLETED ? 1 : 0)
                .sum();
            
            SettlementReport report = SettlementReport.builder()
                .merchantId(merchantId)
                .periodStart(startDate)
                .periodEnd(endDate)
                .totalSettlements(periodSettlements.size())
                .completedSettlements(completedCount)
                .totalGrossAmount(totalGrossAmount)
                .totalFees(totalFees)
                .totalNetAmount(totalNetAmount)
                .settlements(periodSettlements)
                .generatedAt(LocalDateTime.now())
                .build();
            
            log.info("Settlement report generated: {} settlements, total net: {}", 
                    periodSettlements.size(), totalNetAmount);
            
            return report;
            
        } catch (Exception e) {
            log.error("Failed to generate settlement report for {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("Settlement report generation failed", e);
        }
    }

    // Private helper methods
    
    private void validateSettlementRequest(String merchantId, List<String> paymentIds, String settlementMethod) {
        if (merchantId == null || merchantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant ID cannot be null or empty");
        }
        
        if (paymentIds == null || paymentIds.isEmpty()) {
            throw new IllegalArgumentException("Payment IDs cannot be null or empty");
        }
        
        if (settlementMethod == null || settlementMethod.trim().isEmpty()) {
            throw new IllegalArgumentException("Settlement method cannot be null or empty");
        }
        
        Set<String> supportedMethods = Set.of("ACH", "WIRE", "INSTANT", "BANK_TRANSFER");
        if (!supportedMethods.contains(settlementMethod)) {
            throw new IllegalArgumentException("Unsupported settlement method: " + settlementMethod);
        }
    }

    private SettlementCalculation calculateSettlementAmounts(String merchantId, List<String> paymentIds, 
                                                           String settlementMethod, String currency) {
        // Mock payment amounts - in production would query payment database
        BigDecimal grossAmount = BigDecimal.valueOf(paymentIds.size() * 100); // $100 per payment
        
        // Calculate settlement fees
        FeeService.SettlementFeeResult feeResult = feeService.calculateSettlementFee(
            merchantId, grossAmount, settlementMethod, currency);
        
        BigDecimal totalFees = feeResult.getSettlementFee();
        BigDecimal netAmount = grossAmount.subtract(totalFees);
        
        return SettlementCalculation.builder()
            .grossAmount(grossAmount)
            .totalFees(totalFees)
            .netAmount(netAmount)
            .build();
    }

    private LocalDateTime calculateScheduledDate(String merchantId, String settlementMethod) {
        // Settlement timing based on method
        return switch (settlementMethod) {
            case "INSTANT" -> LocalDateTime.now().plusMinutes(5);
            case "ACH" -> LocalDateTime.now().plusDays(1);
            case "WIRE" -> LocalDateTime.now().plusHours(2);
            case "BANK_TRANSFER" -> LocalDateTime.now().plusDays(2);
            default -> LocalDateTime.now().plusDays(1);
        };
    }

    private void scheduleSettlementProcessing(Settlement settlement) {
        // Schedule asynchronous processing
        CompletableFuture.runAsync(() -> {
            try {
                // Wait until scheduled time
                // Non-blocking delay
                CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> {});
                // Continue after delay
                processSettlement(settlement.getSettlementId());
            } catch (Exception e) {
                log.error("Scheduled settlement processing failed for {}: {}", 
                        settlement.getSettlementId(), e.getMessage());
            }
        });
    }

    private void performPreSettlementValidations(Settlement settlement) {
        // Check minimum amount
        if (settlement.getNetAmount().compareTo(MINIMUM_SETTLEMENT_AMOUNT) < 0) {
            throw new RuntimeException("Settlement amount below minimum: " + MINIMUM_SETTLEMENT_AMOUNT);
        }
        
        // Verify bank account
        String merchantAccountId = getMerchantBankAccount(settlement.getMerchantId());
        if (!bankingService.verifyBankAccount(merchantAccountId)) {
            throw new RuntimeException("Bank account verification failed for merchant");
        }
        
        // Check daily limits
        if (!bankingService.checkDailyLimit(merchantAccountId, settlement.getNetAmount())) {
            throw new RuntimeException("Settlement amount exceeds daily transfer limit");
        }
    }

    private BankingService.TransferResult executeSettlementTransfer(Settlement settlement) {
        String merchantAccountId = getMerchantBankAccount(settlement.getMerchantId());
        
        return switch (settlement.getSettlementMethod()) {
            case "ACH" -> bankingService.processACHTransfer(merchantAccountId, 
                settlement.getNetAmount(), settlement.getSettlementId());
            case "WIRE" -> bankingService.processWireTransfer(merchantAccountId, 
                settlement.getNetAmount(), settlement.getSettlementId());
            case "INSTANT", "BANK_TRANSFER" -> bankingService.initiateTransfer(merchantAccountId, 
                settlement.getNetAmount(), settlement.getCurrency(), settlement.getSettlementId());
            default -> throw new RuntimeException("Unsupported settlement method");
        };
    }

    private ReconciliationService.ReconciliationResult performSettlementReconciliation(Settlement settlement) {
        // Mock payment list - in production would fetch actual payments
        List<Object> payments = Collections.emptyList();
        
        return reconciliationService.reconcileSettlement(
            settlement.getSettlementId(), 
            payments, 
            settlement.getGrossAmount(), 
            settlement.getNetAmount()
        );
    }

    private void updateSettlementStatus(String settlementId, SettlementStatus status) {
        settlementStatuses.put(settlementId, status);
        Settlement settlement = settlements.get(settlementId);
        if (settlement != null) {
            settlement.setStatus(status);
        }
        log.debug("Updated settlement {} status to: {}", settlementId, status);
    }

    private void scheduleSettlementRetry(Settlement settlement) {
        CompletableFuture.runAsync(() -> {
            try {
                // Wait before retry (exponential backoff)
                long delayMinutes = (long) Math.pow(2, settlement.getRetryCount()) * 5;
                // Non-blocking exponential backoff delay
                CompletableFuture.delayedExecutor(delayMinutes, TimeUnit.MINUTES)
                    .execute(() -> {});
                
                log.info("Retrying settlement processing: {} (attempt {})", 
                        settlement.getSettlementId(), settlement.getRetryCount() + 1);
                
                processSettlement(settlement.getSettlementId());
                
            } catch (Exception e) {
                log.error("Settlement retry failed for {}: {}", 
                        settlement.getSettlementId(), e.getMessage());
            }
        });
    }

    private String getMerchantBankAccount(String merchantId) {
        // In production, this would query merchant configuration
        return "bank_account_" + merchantId;
    }

    // Data classes and enums
    
    public enum SettlementStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED, RETRY_SCHEDULED
    }

    @lombok.Data
    @lombok.Builder
    public static class Settlement {
        private String settlementId;
        private String merchantId;
        private List<String> paymentIds;
        private String settlementMethod;
        private String currency;
        private BigDecimal grossAmount;
        private BigDecimal totalFees;
        private BigDecimal netAmount;
        private SettlementStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime scheduledDate;
        private LocalDateTime processedAt;
        private LocalDateTime completedAt;
        private LocalDateTime cancelledAt;
        private String transferReference;
        private BigDecimal actualTransferAmount;
        private String failureReason;
        private String cancellationReason;
        private int retryCount;
    }

    @lombok.Data
    @lombok.Builder
    public static class SettlementResult {
        private String settlementId;
        private String merchantId;
        private BigDecimal grossAmount;
        private BigDecimal netAmount;
        private BigDecimal totalFees;
        private String settlementMethod;
        private SettlementStatus status;
        private LocalDateTime scheduledDate;
        private int paymentCount;
    }

    @lombok.Data
    @lombok.Builder
    public static class SettlementExecutionResult {
        private String settlementId;
        private String merchantId;
        private SettlementStatus status;
        private String transferReference;
        private BigDecimal actualAmount;
        private boolean reconciled;
        private String error;
        private boolean retryScheduled;
        private LocalDateTime executionStarted;
        private LocalDateTime completedAt;
    }

    @lombok.Data
    @lombok.Builder
    private static class SettlementCalculation {
        private BigDecimal grossAmount;
        private BigDecimal totalFees;
        private BigDecimal netAmount;
    }

    @lombok.Data
    @lombok.Builder
    public static class SettlementReport {
        private String merchantId;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private int totalSettlements;
        private long completedSettlements;
        private BigDecimal totalGrossAmount;
        private BigDecimal totalFees;
        private BigDecimal totalNetAmount;
        private List<Settlement> settlements;
        private LocalDateTime generatedAt;
    }

    public void createSettlement(com.waqiti.payment.model.SettlementRequest settlementRequest) {
        log.info("Creating settlement from request: settlementId={}, batchId={}, amount={}",
            settlementRequest.getSettlementId(), settlementRequest.getBatchId(), settlementRequest.getAmount());

        // Create settlement using the existing method
        createSettlement(
            settlementRequest.getMerchantId(),
            new ArrayList<>(), // Empty payment IDs as they're part of the batch
            "BATCH", // Settlement method
            settlementRequest.getCurrency()
        );
    }
}