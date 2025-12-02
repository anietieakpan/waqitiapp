package com.waqiti.support.dto.gdpr;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GDPR Rectification Summary DTO (Article 16 - Right to Rectification)
 *
 * Summarizes what data was corrected for a user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GdprRectificationSummaryDTO {

    private String userId;
    private LocalDateTime rectificationDate;
    private String requestedBy;

    // What was corrected
    private List<String> correctedFields;
    private int recordsUpdated;

    // Status
    private String status; // SUCCESS, PARTIAL, FAILED
    private String notes;
}
