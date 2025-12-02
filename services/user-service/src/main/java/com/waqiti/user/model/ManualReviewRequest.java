package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualReviewRequest {
    private String userId;
    private String reviewReason;
    private String reviewPriority;
    private List<String> verificationsToReview;
    private String additionalNotes;
    private Instant requestTimestamp;
}
