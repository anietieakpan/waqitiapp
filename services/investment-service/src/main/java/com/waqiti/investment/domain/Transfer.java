package com.waqiti.investment.domain;

import com.waqiti.investment.domain.enums.TransferStatus;
import com.waqiti.investment.domain.enums.TransferType;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "investment_transfers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"investmentAccount"})
@ToString(exclude = {"investmentAccount"})
@EntityListeners(AuditingEntityListener.class)
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_account_id", nullable = false)
    private InvestmentAccount investmentAccount;

    @Column(nullable = false, unique = true)
    private String transferNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String fromAccountId;

    @Column(nullable = false)
    private String toAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status = TransferStatus.PENDING;

    private String walletTransactionId;

    private String brokerageTransferId;

    private String description;

    private String failureReason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime processedAt;

    private LocalDateTime completedAt;

    private LocalDateTime failedAt;

    @Version
    private Long version;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    public void process() {
        this.status = TransferStatus.PROCESSING;
        this.processedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = TransferStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        this.status = TransferStatus.FAILED;
        this.failureReason = reason;
        this.failedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = TransferStatus.CANCELLED;
    }

    public boolean isPending() {
        return status == TransferStatus.PENDING;
    }

    public boolean isProcessing() {
        return status == TransferStatus.PROCESSING;
    }

    public boolean isCompleted() {
        return status == TransferStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == TransferStatus.FAILED;
    }

    public boolean isDeposit() {
        return type == TransferType.DEPOSIT;
    }

    public boolean isWithdrawal() {
        return type == TransferType.WITHDRAWAL;
    }

    public boolean canBeCancelled() {
        return status == TransferStatus.PENDING;
    }
}