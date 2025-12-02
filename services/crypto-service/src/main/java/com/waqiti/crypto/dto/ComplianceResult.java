/**
 * Compliance Result DTO
 * Contains results of compliance screening
 */
package com.waqiti.crypto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceResult {
    private boolean passed;
    private boolean blocked;
    private String reason;
    private boolean requiresReporting;
    private String reportingReason;
    private boolean requiresManualReview;
    private String reviewReason;
    
    public boolean isBlocked() {
        return blocked;
    }
}