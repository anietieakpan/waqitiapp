package com.waqiti.frauddetection.entity;

import com.waqiti.frauddetection.dto.ModelPrediction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "fraud_predictions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudPrediction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "transaction_id", nullable = false)
    private String transactionId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "amount")
    private BigDecimal amount;
    
    @Column(name = "fraud_score")
    private Double fraudScore;
    
    @Column(name = "decision")
    private String decision;
    
    @Column(name = "model_version")
    private String modelVersion;
    
    @Column(name = "confidence")
    private Double confidence;
    
    @Transient
    private List<ModelPrediction> predictions;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}