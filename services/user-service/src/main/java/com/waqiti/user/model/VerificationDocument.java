package com.waqiti.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "verification_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationDocument {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    private String documentType;
    
    private String documentNumber;
    
    private String documentImageUrl;
    
    private String backImageUrl;
    
    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;
    
    @Column(columnDefinition = "jsonb")
    @Convert(converter = com.waqiti.user.domain.JsonMapConverter.class)
    private Map<String, Object> extractedData;
    
    private BigDecimal authenticity;
    
    private BigDecimal qualityScore;
    
    private String verificationProvider;
    
    private Instant createdAt;
    
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}