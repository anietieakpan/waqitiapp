package com.waqiti.messaging.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "message_receipts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReceipt {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ReceiptStatus status;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @Column(name = "read_at")
    private LocalDateTime readAt;
    
    @Column(name = "device_id")
    private String deviceId;
    
    @Column(name = "client_version")
    private String clientVersion;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = ReceiptStatus.SENT;
        }
    }
    
    public void markAsDelivered() {
        this.status = ReceiptStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }
    
    public void markAsRead() {
        this.status = ReceiptStatus.READ;
        this.readAt = LocalDateTime.now();
        if (this.deliveredAt == null) {
            this.deliveredAt = this.readAt;
        }
    }
}

enum ReceiptStatus {
    SENT,
    DELIVERED,
    READ,
    FAILED
}