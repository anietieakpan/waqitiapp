package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for balance confirmation between inter-company entities
 * Used to confirm and validate balances between related entities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterCompanyBalanceConfirmation {
    
    private UUID confirmationId;
    private UUID sourceEntityId;
    private String sourceEntityName;
    private UUID targetEntityId;
    private String targetEntityName;
    private LocalDate confirmationDate;
    private LocalDate balanceDate;
    private String accountCode;
    private String accountName;
    private BigDecimal sourceBalance;
    private BigDecimal targetBalance;
    private BigDecimal balanceDifference;
    private String currency;
    private String confirmationStatus; // CONFIRMED, DISPUTED, PENDING_RESPONSE, EXPIRED
    private String confirmationType; // POSITIVE, NEGATIVE, BLANK
    private LocalDateTime sentAt;
    private String sentBy;
    private LocalDateTime respondedAt;
    private String respondedBy;
    private LocalDateTime confirmedAt;
    private String confirmedBy;
    private List<String> exceptions;
    private String responseNotes;
    private String followUpRequired;
    private LocalDate followUpDate;
    private String followUpNotes;
    private boolean isRecurring;
    private String frequency; // MONTHLY, QUARTERLY, ANNUALLY
    private LocalDate nextConfirmationDate;
    private List<String> attachedDocuments;
    private String createdBy;
    private LocalDateTime createdAt;
}