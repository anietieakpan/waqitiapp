package com.waqiti.frauddetection.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_models")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudModel {
    
    @Id
    private String id;
    
    @Column(name = "model_name", nullable = false)
    private String modelName;
    
    @Column(name = "model_type", nullable = false)
    private String modelType;
    
    @Column(name = "version", nullable = false)
    private String version;
    
    @Column(name = "model_path")
    private String modelPath;
    
    @Column(name = "is_active")
    private boolean isActive;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}