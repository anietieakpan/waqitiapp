package com.waqiti.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspiciousPattern {

    private UUID patternId;
    private String patternType;
    private String description;
    private Double confidence;
    private LocalDateTime detectedAt;
    private String severity;

    public String getDescription() {
        return description;
    }
}
