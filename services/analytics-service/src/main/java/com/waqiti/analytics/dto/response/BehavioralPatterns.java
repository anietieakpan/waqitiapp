package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Behavioral Patterns DTO
 *
 * Comprehensive user spending and transaction behavior patterns.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehavioralPatterns {

    private List<String> spendingPatterns;
    private List<String> savingPatterns;
    private String transactionTiming; // MORNING_SPENDER, EVENING_SPENDER, etc.
    private String spendingPersonality; // CONSERVATIVE, MODERATE, LIBERAL
    private List<RecurringPattern> recurringTransactions;
    private Integer impulsePurchaseScore; // 0-100
}
