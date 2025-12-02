package com.waqiti.common.observability.dto;

import com.waqiti.common.enums.ViolationSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SLAViolation {
    private String id;
    private String slaName;
    private ViolationSeverity severity;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime detectedAt; // Timestamp when violation was detected
    private String description;
    private double impactValue;
    private boolean isResolved;
    private String resolutionNotes;

    public String getSlaName() {
        return slaName;
    }
}

