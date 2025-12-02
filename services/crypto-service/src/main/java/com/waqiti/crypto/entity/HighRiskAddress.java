package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Entity representing a high-risk cryptocurrency address
 */
@Entity
@Table(name = "high_risk_addresses", indexes = {
    @Index(name = "idx_high_risk_address", columnList = "address", unique = true),
    @Index(name = "idx_high_risk_category", columnList = "category"),
    @Index(name = "idx_high_risk_active", columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class HighRiskAddress {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Column(nullable = false, unique = true)
    private String address;
    
    @NotBlank
    @Column(nullable = false)
    private String category; // MIXER, DARK_MARKET, GAMBLING, RANSOMWARE
    
    @Column(length = 500)
    private String description;
    
    @NotNull
    @Column(nullable = false)
    @Builder.Default
    private Integer riskLevel = 5; // 1-10 scale
    
    @Column
    private String source; // Where this information came from
    
    @NotNull
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    @Column
    private LocalDateTime lastSeenAt;
    
    @Column
    @Builder.Default
    private Integer transactionCount = 0;
    
    @Column
    @Builder.Default
    private Boolean highFrequency = false;
    
    @Column
    @Builder.Default
    private Boolean hasPrivacyCoinInteraction = false;
    
    @Column
    @Builder.Default
    private Boolean isFromHighRiskJurisdiction = false;
    
    @Column
    private LocalDateTime firstSeenDate;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column
    private String createdBy;
    
    @Column
    private String updatedBy;
    
    public boolean isHighFrequency() {
        return Boolean.TRUE.equals(highFrequency);
    }
    
    public boolean hasPrivacyCoinInteraction() {
        return Boolean.TRUE.equals(hasPrivacyCoinInteraction);
    }
    
    public boolean isFromHighRiskJurisdiction() {
        return Boolean.TRUE.equals(isFromHighRiskJurisdiction);
    }
    
    public java.util.Date getFirstSeenDate() {
        return firstSeenDate != null ? java.sql.Timestamp.valueOf(firstSeenDate) : new java.util.Date();
    }
}