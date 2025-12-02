package com.waqiti.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(length = 100)
    private String category;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(nullable = false)
    private boolean read;

    @Column(name = "action_url")
    private String actionUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    private DeliveryStatus deliveryStatus;

    @Column(name = "delivery_error")
    private String deliveryError;

    @Version
    private Long version;

    // Audit fields
    @Setter
    @Column(name = "created_by")
    private String createdBy;

    /**
     * Creates a new notification
     */
    public static Notification create(UUID userId, String title, String message,
                                      NotificationType type, String category) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.title = title;
        notification.message = message;
        notification.type = type;
        notification.category = category;
        notification.read = false;
        notification.createdAt = LocalDateTime.now();
        notification.deliveryStatus = DeliveryStatus.PENDING;
        return notification;
    }

    /**
     * Marks the notification as read
     */
    public void markAsRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }

    /**
     * Updates the delivery status
     */
    public void updateDeliveryStatus(DeliveryStatus status, String error) {
        this.deliveryStatus = status;
        this.deliveryError = error;
    }

    /**
     * Sets the reference ID
     */
    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    /**
     * Sets the action URL
     */
    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    /**
     * Sets the expiry date
     */
    public void setExpiryDate(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Checks if the notification is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}