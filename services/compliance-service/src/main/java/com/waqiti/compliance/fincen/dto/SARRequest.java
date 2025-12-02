package com.waqiti.compliance.fincen.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Suspicious Activity Report (SAR) Request
 *
 * Data required to file a SAR with FinCEN for suspicious transactions
 */
@Data
@Builder
public class SARRequest {
    private String subjectUserId;
    private String subjectFirstName;
    private String subjectLastName;

    // Activity details
    private String suspiciousActivity;
    private String activityType; // "a" = Structuring, "b" = Money laundering, etc.
    private BigDecimal totalAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String narrative;
}
