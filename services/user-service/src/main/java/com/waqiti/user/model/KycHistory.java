package com.waqiti.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "kyc_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String userId;
    private String status;
    private String previousStatus;
    private String kycLevel;
    private String provider;
    private BigDecimal verificationScore;
    private String details;
    private String rejectionReason;
    private Instant timestamp;
}