package com.waqiti.common.domain.services;

import com.waqiti.common.domain.Money;
import com.waqiti.common.domain.valueobjects.UserId;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Account Balance Domain Service
 * Encapsulates business rules for account balance calculations and validations
 *
 * Migrated from valueobjects.Money to domain.Money (November 8, 2025).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountBalanceService {

    private static final Money MINIMUM_BALANCE = Money.of(100.0, "NGN");
    private static final BigDecimal OVERDRAFT_MULTIPLIER = BigDecimal.valueOf(0.1); // 10% overdraft
    
    /**
     * Calculate available balance considering holds and reserves
     */
    public BalanceCalculationResult calculateAvailableBalance(BalanceCalculationRequest request) {
        log.debug("Calculating available balance for account: {}", request.getAccountId());
        
        Money currentBalance = request.getCurrentBalance();
        Money totalHolds = calculateTotalHolds(request.getHolds());
        Money totalReserves = calculateTotalReserves(request.getReserves());
        Money pendingDebits = calculatePendingDebits(request.getPendingTransactions());
        Money pendingCredits = calculatePendingCredits(request.getPendingTransactions());
        
        // Available = Current + Pending Credits - Holds - Reserves - Pending Debits
        Money availableBalance = currentBalance
                .add(pendingCredits)
                .subtract(totalHolds)
                .subtract(totalReserves)
                .subtract(pendingDebits);
        
        // Apply minimum balance rule
        Money minimumRequired = request.getAccountType() == AccountType.SAVINGS ?
                MINIMUM_BALANCE : Money.zero(currentBalance.getCurrencyCode());
        
        Money effectiveAvailable = availableBalance.subtract(minimumRequired);
        
        // Calculate overdraft limit if applicable
        Money overdraftLimit = Money.zero(currentBalance.getCurrencyCode());
        if (request.getAccountType() == AccountType.CURRENT && request.isOverdraftEnabled()) {
            overdraftLimit = currentBalance.multiply(OVERDRAFT_MULTIPLIER);
        }
        
        Money totalAvailable = effectiveAvailable.add(overdraftLimit);
        
        BalanceCalculationResult result = BalanceCalculationResult.builder()
                .accountId(request.getAccountId())
                .currentBalance(currentBalance)
                .availableBalance(totalAvailable.isPositive() ? totalAvailable : Money.zero(currentBalance.getCurrencyCode()))
                .totalHolds(totalHolds)
                .totalReserves(totalReserves)
                .pendingCredits(pendingCredits)
                .pendingDebits(pendingDebits)
                .overdraftLimit(overdraftLimit)
                .minimumBalance(minimumRequired)
                .calculatedAt(Instant.now())
                .build();
        
        log.debug("Balance calculation completed: available={}, holds={}, reserves={}", 
                result.getAvailableBalance(), totalHolds, totalReserves);
        
        return result;
    }
    
    /**
     * Validate sufficient funds for transaction
     */
    public SufficientFundsResult validateSufficientFunds(SufficientFundsRequest request) {
        BalanceCalculationResult balance = calculateAvailableBalance(BalanceCalculationRequest.builder()
                .accountId(request.getAccountId())
                .currentBalance(request.getCurrentBalance())
                .holds(request.getHolds())
                .reserves(request.getReserves())
                .pendingTransactions(request.getPendingTransactions())
                .accountType(request.getAccountType())
                .overdraftEnabled(request.isOverdraftEnabled())
                .build());
        
        boolean sufficient = balance.getAvailableBalance().isGreaterThanOrEqual(request.getRequestedAmount());
        Money shortfall = sufficient ? Money.zero(request.getRequestedAmount().getCurrencyCode()) :
                request.getRequestedAmount().subtract(balance.getAvailableBalance());
        
        return SufficientFundsResult.builder()
                .sufficient(sufficient)
                .availableBalance(balance.getAvailableBalance())
                .requestedAmount(request.getRequestedAmount())
                .shortfall(shortfall)
                .wouldUseOverdraft(balance.getAvailableBalance().isLessThan(request.getRequestedAmount()) && 
                                 balance.getOverdraftLimit().isPositive())
                .checkedAt(Instant.now())
                .build();
    }
    
    /**
     * Calculate interest earnings for savings accounts
     */
    public InterestCalculationResult calculateInterest(InterestCalculationRequest request) {
        Money principal = request.getAverageBalance();
        BigDecimal annualRate = request.getInterestRate();
        int days = request.getDays();
        
        // Daily interest = (Principal × Annual Rate × Days) / 365
        BigDecimal dailyRate = annualRate.divide(BigDecimal.valueOf(365), 10, RoundingMode.HALF_UP);
        BigDecimal interestAmount = principal.getAmount()
                .multiply(dailyRate)
                .multiply(BigDecimal.valueOf(days));
        
        Money interest = Money.of(interestAmount, principal.getCurrencyCode());
        Money taxAmount = interest.multiply(request.getTaxRate());
        Money netInterest = interest.subtract(taxAmount);
        
        return InterestCalculationResult.builder()
                .principal(principal)
                .grossInterest(interest)
                .taxAmount(taxAmount)
                .netInterest(netInterest)
                .interestRate(annualRate)
                .days(days)
                .calculatedAt(Instant.now())
                .build();
    }
    
    /**
     * Apply balance holds for pending transactions
     */
    public HoldApplicationResult applyHold(HoldApplicationRequest request) {
        String holdId = generateHoldId();
        
        Money holdAmount = request.getAmount();
        Instant expiryTime = request.getExpiryTime() != null ? 
                request.getExpiryTime() : Instant.now().plusSeconds(3600); // 1 hour default
        
        BalanceHold hold = BalanceHold.builder()
                .holdId(holdId)
                .accountId(request.getAccountId())
                .amount(holdAmount)
                .reason(request.getReason())
                .createdAt(Instant.now())
                .expiryTime(expiryTime)
                .status(HoldStatus.ACTIVE)
                .build();
        
        return HoldApplicationResult.builder()
                .holdId(holdId)
                .success(true)
                .hold(hold)
                .message("Hold applied successfully")
                .build();
    }
    
    // Private helper methods
    
    private Money calculateTotalHolds(List<BalanceHold> holds) {
        if (holds == null || holds.isEmpty()) {
            return Money.zero("NGN");
        }
        
        return holds.stream()
                .filter(hold -> hold.getStatus() == HoldStatus.ACTIVE)
                .filter(hold -> hold.getExpiryTime().isAfter(Instant.now()))
                .map(BalanceHold::getAmount)
                .reduce(Money.zero(holds.get(0).getAmount().getCurrencyCode()), Money::add);
    }
    
    private Money calculateTotalReserves(List<BalanceReserve> reserves) {
        if (reserves == null || reserves.isEmpty()) {
            return Money.zero("NGN");
        }
        
        return reserves.stream()
                .filter(reserve -> reserve.getStatus() == ReserveStatus.ACTIVE)
                .map(BalanceReserve::getAmount)
                .reduce(Money.zero(reserves.get(0).getAmount().getCurrencyCode()), Money::add);
    }
    
    private Money calculatePendingDebits(List<PendingTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return Money.zero("NGN");
        }
        
        return transactions.stream()
                .filter(txn -> txn.getType() == TransactionType.DEBIT)
                .map(PendingTransaction::getAmount)
                .reduce(Money.zero(transactions.get(0).getAmount().getCurrencyCode()), Money::add);
    }
    
    private Money calculatePendingCredits(List<PendingTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return Money.zero("NGN");
        }
        
        return transactions.stream()
                .filter(txn -> txn.getType() == TransactionType.CREDIT)
                .map(PendingTransaction::getAmount)
                .reduce(Money.zero(transactions.get(0).getAmount().getCurrencyCode()), Money::add);
    }
    
    private String generateHoldId() {
        return "hold_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    // Request and Result classes
    
    @Data
    @Builder
    public static class BalanceCalculationRequest {
        private String accountId;
        private Money currentBalance;
        private List<BalanceHold> holds;
        private List<BalanceReserve> reserves;
        private List<PendingTransaction> pendingTransactions;
        private AccountType accountType;
        private boolean overdraftEnabled;
    }
    
    @Data
    @Builder
    public static class BalanceCalculationResult {
        private String accountId;
        private Money currentBalance;
        private Money availableBalance;
        private Money totalHolds;
        private Money totalReserves;
        private Money pendingCredits;
        private Money pendingDebits;
        private Money overdraftLimit;
        private Money minimumBalance;
        private Instant calculatedAt;
    }
    
    @Data
    @Builder
    public static class SufficientFundsRequest {
        private String accountId;
        private Money currentBalance;
        private Money requestedAmount;
        private List<BalanceHold> holds;
        private List<BalanceReserve> reserves;
        private List<PendingTransaction> pendingTransactions;
        private AccountType accountType;
        private boolean overdraftEnabled;
    }
    
    @Data
    @Builder
    public static class SufficientFundsResult {
        private boolean sufficient;
        private Money availableBalance;
        private Money requestedAmount;
        private Money shortfall;
        private boolean wouldUseOverdraft;
        private Instant checkedAt;
    }
    
    @Data
    @Builder
    public static class InterestCalculationRequest {
        private String accountId;
        private Money averageBalance;
        private BigDecimal interestRate;
        private int days;
        private BigDecimal taxRate;
    }
    
    @Data
    @Builder
    public static class InterestCalculationResult {
        private Money principal;
        private Money grossInterest;
        private Money taxAmount;
        private Money netInterest;
        private BigDecimal interestRate;
        private int days;
        private Instant calculatedAt;
    }
    
    @Data
    @Builder
    public static class HoldApplicationRequest {
        private String accountId;
        private Money amount;
        private String reason;
        private Instant expiryTime;
    }
    
    @Data
    @Builder
    public static class HoldApplicationResult {
        private String holdId;
        private boolean success;
        private BalanceHold hold;
        private String message;
    }
    
    @Data
    @Builder
    public static class BalanceHold {
        private String holdId;
        private String accountId;
        private Money amount;
        private String reason;
        private Instant createdAt;
        private Instant expiryTime;
        private HoldStatus status;
    }
    
    @Data
    @Builder
    public static class BalanceReserve {
        private String reserveId;
        private String accountId;
        private Money amount;
        private String purpose;
        private ReserveStatus status;
    }
    
    @Data
    @Builder
    public static class PendingTransaction {
        private String transactionId;
        private Money amount;
        private TransactionType type;
        private Instant scheduledAt;
    }
    
    public enum AccountType {
        SAVINGS,
        CURRENT,
        BUSINESS,
        ESCROW
    }
    
    public enum HoldStatus {
        ACTIVE,
        EXPIRED,
        RELEASED
    }
    
    public enum ReserveStatus {
        ACTIVE,
        RELEASED
    }
    
    public enum TransactionType {
        DEBIT,
        CREDIT
    }
}