package com.waqiti.corebanking.dto;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.domain.Ledger;
import com.waqiti.corebanking.domain.Transaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Objects for Core Banking Operations
 */

@Data
@Builder
public class TransactionRequest {
    private Transaction.TransactionType transactionType;
    private UUID sourceAccountId;
    private UUID targetAccountId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String narrative;
    private LocalDateTime valueDate;
    private BigDecimal feeAmount;
    private String feeCurrency;
    private String sourceSystem;
    private String channel;
    private UUID initiatedBy;
    private String externalReference;
    private String deviceFingerprint;
    private String locationData;
    private String metadata;
}

@Data
@Builder
public class TransactionResult {
    private UUID transactionId;
    private String referenceNumber;
    private Status status;
    private String message;
    private List<Ledger> ledgerEntries;

    public enum Status {
        COMPLETED, FAILED, PENDING_APPROVAL
    }

    public static TransactionResult failed(String message) {
        return TransactionResult.builder()
            .status(Status.FAILED)
            .message(message)
            .build();
    }
}

@Data
@Builder
public class TransactionDetails {
    private Transaction transaction;
    private List<Ledger> ledgerEntries;
    private Account sourceAccount;
    private Account targetAccount;
}

@Data
@Builder
public class TransactionProcessingResult {
    private boolean success;
    private String failureReason;
    private List<Ledger> ledgerEntries;

    public static TransactionProcessingResult success(List<Ledger> ledgerEntries) {
        return TransactionProcessingResult.builder()
            .success(true)
            .ledgerEntries(ledgerEntries)
            .build();
    }

    public static TransactionProcessingResult failure(String reason) {
        return TransactionProcessingResult.builder()
            .success(false)
            .failureReason(reason)
            .build();
    }
}

// Placeholder classes for external services
@Data
@Builder
public class TransactionContext {
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private String deviceFingerprint;
    private String locationData;
    
    public static TransactionContext fromTransaction(Transaction transaction) {
        return TransactionContext.builder()
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .deviceFingerprint(transaction.getDeviceFingerprint())
            .locationData(transaction.getLocationData())
            .build();
    }
}

@Data
@Builder
public class FraudDetectionResult {
    private boolean fraudulent;
    private double fraudScore;
    private String reason;
}

@Data
@Builder
public class ComplianceResult {
    private boolean approved;
    private Transaction.ComplianceStatus status;
    private String reason;
    private String amlFlagsAsJson;
}

// Placeholder services
public interface FraudDetectionService {
    FraudDetectionResult detectFraud(TransactionContext context);
}

public interface ComplianceService {
    ComplianceResult checkCompliance(TransactionContext context);
}

public interface BalanceCalculationService {
    BigDecimal calculateBalance(UUID accountId);
}