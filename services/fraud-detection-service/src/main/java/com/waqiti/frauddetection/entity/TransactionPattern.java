package com.waqiti.frauddetection.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Transaction Pattern Entity
 * Stores learned transaction patterns for users
 */
@Entity
@Table(name = "transaction_patterns", indexes = {
    @Index(name = "idx_pattern_user", columnList = "userId"),
    @Index(name = "idx_pattern_type", columnList = "patternType")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionPattern {
    
    @Id
    @Column(name = "pattern_id", nullable = false, length = 36)
    private String patternId;
    
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
    
    @Column(name = "pattern_type", nullable = false, length = 50)
    private String patternType; // VELOCITY, AMOUNT, MERCHANT, LOCATION, TIME_OF_DAY
    
    @Column(name = "frequency")
    private Double frequency;
    
    @Column(name = "average_amount", precision = 19, scale = 4)
    private java.math.BigDecimal averageAmount;
    
    @Column(name = "max_amount", precision = 19, scale = 4)
    private java.math.BigDecimal maxAmount;
    
    @Column(name = "typical_merchants", columnDefinition = "TEXT")
    private String typicalMerchants; // JSON array
    
    @Column(name = "typical_locations", columnDefinition = "TEXT")
    private String typicalLocations; // JSON array
    
    @Column(name = "typical_hours", length = 100)
    private String typicalHours; // Comma-separated hours
    
    @Column(name = "first_observed", nullable = false)
    private Instant firstObserved;
    
    @Column(name = "last_updated")
    private Instant lastUpdated;
    
    @Column(name = "sample_count")
    private Long sampleCount = 0L;
    
    @Version
    private Long version;
}
