package com.waqiti.security.service;

import lombok.Builder;
import lombok.Data;

/**
 * Data class for hourly transaction statistics
 */
@Data
@Builder
public class HourlyStats {
    private int hour;
    private int transactionCount;
}