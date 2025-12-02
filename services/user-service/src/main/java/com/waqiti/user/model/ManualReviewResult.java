package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualReviewResult {
    private boolean reviewInitiated;
    private String reviewId;
    private Duration estimatedReviewTime;
    private String assignedReviewer;
    private boolean notificationSent;
    private int queuePosition;
}
