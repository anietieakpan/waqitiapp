package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Settlement Batch entity
 * CRITICAL: Added @Version for optimistic locking to prevent batch processing conflicts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "settlement_batches")
public class SettlementBatch {
    @Id
    private String id;

    @Version
    private Long version;

    private String settlementId;
    private String batchNumber;
    private String merchantId;
    
    // Batch details
    private Integer paymentCount;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    
    // Payment references
    private List<String> paymentIds;
    private List<String> successfulPaymentIds;
    private List<String> failedPaymentIds;
    
    // Processing
    private LocalDateTime processedAt;
    private Integer successCount;
    private Integer failureCount;
    private String processingError;
    
    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}