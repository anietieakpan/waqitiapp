package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTopicDto {
    private String id;
    private String name;
    private String displayName;
    private String description;
    private String category;
    private boolean active;
    private boolean autoSubscribe;
    private long subscriberCount;
    private int priority;
    private String icon;
    private String color;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}