package com.waqiti.atm.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "atm_inquiries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ATMInquiry {

    @Id
    private UUID id;

    @Column(name = "atm_id", nullable = false)
    private UUID atmId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "inquiry_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private InquiryType inquiryType;

    @Column(name = "available_balance", precision = 19, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "current_balance", precision = 19, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "mini_statement_data", columnDefinition = "TEXT")
    private String miniStatementData;

    @Column(name = "receipt_requested")
    private Boolean receiptRequested = false;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private InquiryStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "inquiry_date", nullable = false)
    private LocalDateTime inquiryDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    public enum InquiryType {
        BALANCE, MINI_STATEMENT
    }

    public enum InquiryStatus {
        PROCESSING, COMPLETED, FAILED
    }
}
