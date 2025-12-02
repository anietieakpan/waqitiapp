package com.waqiti.user.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    private String documentType;
    
    private String documentUrl;
    
    private String documentNumber;
    
    private String issuingCountry;
    
    private LocalDate issueDate;
    
    private LocalDate expiryDate;
    
    private String verificationStatus;
    
    private BigDecimal verificationScore;
    
    @Column(columnDefinition = "jsonb")
    @Convert(converter = com.waqiti.user.domain.JsonMapConverter.class)
    private Map<String, Object> metadata;
    
    @Column(columnDefinition = "jsonb")
    private JsonNode extractedData;
    
    private Instant uploadedAt;
    
    private Instant verifiedAt;
    
    private Instant lastUpdated;
    
    private Instant createdAt;
    
    public boolean isVerified() {
        return "VERIFIED".equalsIgnoreCase(verificationStatus);
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lastUpdated == null) {
            lastUpdated = Instant.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = Instant.now();
    }
}