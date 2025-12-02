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
@Table(name = "notification_topics", indexes = {
    @Index(name = "idx_topic_name", columnList = "name", unique = true),
    @Index(name = "idx_topic_category", columnList = "category"),
    @Index(name = "idx_topic_active", columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTopic {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    @Column(name = "name", nullable = false, unique = true)
    private String name;
    
    @Column(name = "display_name", nullable = false)
    private String displayName;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "category", nullable = false)
    private String category;
    
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @Column(name = "auto_subscribe", nullable = false)
    @Builder.Default
    private boolean autoSubscribe = false;
    
    @Column(name = "subscriber_count")
    @Builder.Default
    private long subscriberCount = 0;
    
    @Column(name = "priority")
    @Builder.Default
    private int priority = 0;
    
    @Column(name = "icon")
    private String icon;
    
    @Column(name = "color")
    private String color;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}