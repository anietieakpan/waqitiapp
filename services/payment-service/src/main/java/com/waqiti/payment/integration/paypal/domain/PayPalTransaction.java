package com.waqiti.payment.integration.paypal.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PayPal transaction entity
 */
@Entity
@Table(name = "paypal_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayPalTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "paypal_order_id", unique = true)
    private String paypalOrderId;
    
    @Column(name = "paypal_transaction_id")
    private String paypalTransactionId;
    
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "currency")
    private String currency;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "approval_url", length = 500)
    private String approvalUrl;
    
    @Column(name = "return_url", length = 500)
    private String returnUrl;
    
    @Column(name = "cancel_url", length = 500)
    private String cancelUrl;
    
    @Column(name = "webhook_event_id")
    private String webhookEventId;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}