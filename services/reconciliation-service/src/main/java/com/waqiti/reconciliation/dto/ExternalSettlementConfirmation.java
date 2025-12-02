package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalSettlementConfirmation {

    private UUID settlementId;
    
    private String externalReference;
    
    private String bankReference;
    
    private BigDecimal amount;
    
    private String currency;
    
    private LocalDate settlementDate;
    
    private LocalDate valueDate;
    
    private String counterpartyName;
    
    private String counterpartyAccount;
    
    private String counterpartyBankCode;
    
    private ConfirmationStatus status;
    
    private String confirmationMethod;
    
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();
    
    private String receivedFrom;
    
    private Map<String, String> additionalFields;
    
    private String swiftReference;
    
    private String messageType;

    public enum ConfirmationStatus {
        CONFIRMED,
        PENDING,
        REJECTED,
        PARTIAL,
        CANCELLED
    }

    public boolean isConfirmed() {
        return ConfirmationStatus.CONFIRMED.equals(status);
    }

    public boolean isPending() {
        return ConfirmationStatus.PENDING.equals(status);
    }

    public boolean isRejected() {
        return ConfirmationStatus.REJECTED.equals(status);
    }

    public boolean hasSwiftReference() {
        return swiftReference != null && !swiftReference.isEmpty();
    }

    public boolean isRecent() {
        return receivedAt != null && 
               receivedAt.isAfter(LocalDateTime.now().minusHours(24));
    }
}