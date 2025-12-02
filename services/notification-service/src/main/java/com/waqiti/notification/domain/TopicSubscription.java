package com.waqiti.notification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "topic_subscriptions", indexes = {
    @Index(name = "idx_topic_device_topic", columnList = "device_token_id,topic", unique = true),
    @Index(name = "idx_topic_active", columnList = "active"),
    @Index(name = "idx_topic_topic", columnList = "topic")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicSubscription {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_token_id", nullable = false)
    private DeviceToken deviceToken;
    
    @Column(name = "topic", nullable = false)
    private String topic;
    
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @Column(name = "subscribed_at")
    private LocalDateTime subscribedAt;
    
    @Column(name = "unsubscribed_at")
    private LocalDateTime unsubscribedAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (subscribedAt == null && active) {
            subscribedAt = LocalDateTime.now();
        }
    }
}