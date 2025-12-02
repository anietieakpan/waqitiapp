package com.waqiti.user.service;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User activity update model for batch processing
 */
@Data
@Builder
public class UserActivityUpdate {
    private UUID userId;
    private LocalDateTime timestamp;
}