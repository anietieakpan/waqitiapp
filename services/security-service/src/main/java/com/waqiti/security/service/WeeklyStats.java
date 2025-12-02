package com.waqiti.security.service;

import lombok.Builder;
import lombok.Data;

/**
 * Data class for weekly transaction statistics
 */
@Data
@Builder
public class WeeklyStats {
    private int dayOfWeek; // 0 = Sunday, 1 = Monday, etc.
    private int transactionCount;
}