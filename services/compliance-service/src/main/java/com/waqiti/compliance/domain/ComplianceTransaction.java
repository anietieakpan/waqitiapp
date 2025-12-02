package com.waqiti.compliance.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "compliance_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;
    
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    
    @Column(name = "counterparty_id")
    private UUID counterpartyId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "transaction_type", length = 50)
    private String transactionType;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "risk_score")
    private Double riskScore;
    
    @ElementCollection
    @CollectionTable(name = "compliance_transaction_flags", 
                      joinColumns = @JoinColumn(name = "transaction_id"))
    @Column(name = "flag")
    private List<String> flags;
    
    @Column(name = "source_country", length = 2)
    private String sourceCountry;
    
    @Column(name = "destination_country", length = 2)
    private String destinationCountry;
    
    @Column(name = "is_suspicious")
    private Boolean isSuspicious;
    
    @Column(name = "is_reported")
    private Boolean isReported;
    
    @Column(name = "compliance_status", length = 50)
    private String complianceStatus;
    
    @Column(name = "screening_result", columnDefinition = "TEXT")
    private String screeningResult;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "screened_at")
    private LocalDateTime screenedAt;
    
    @Column(name = "reported_at")
    private LocalDateTime reportedAt;
}