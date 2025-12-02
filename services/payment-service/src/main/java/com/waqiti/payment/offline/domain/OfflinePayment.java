package com.waqiti.payment.offline.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "offline_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflinePayment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "sender_id", nullable = false)
    private String senderId;
    
    @Column(name = "recipient_id", nullable = false)
    private String recipientId;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Column(length = 500)
    private String description;
    
    @Column(name = "device_id", nullable = false)
    private String deviceId;
    
    @Column(name = "client_timestamp", nullable = false)
    private LocalDateTime clientTimestamp;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfflinePaymentStatus status;
    
    @Column(name = "offline_signature", nullable = false)
    private String offlineSignature;
    
    @Column(name = "qr_code", length = 1000)
    private String qrCode;
    
    @Column(name = "bluetooth_token")
    private String bluetoothToken;
    
    @Column(name = "nfc_data", columnDefinition = "TEXT")
    private String nfcData;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    
    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "sync_attempts")
    private int syncAttempts = 0;
    
    @Column(name = "sync_error", length = 1000)
    private String syncError;
    
    @Column(name = "online_payment_id")
    private String onlinePaymentId;
    
    @Column(name = "recipient_verification_data", length = 1000)
    private String recipientVerificationData;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}