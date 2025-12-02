package com.waqiti.common.security.awareness.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import com.waqiti.common.security.awareness.validation.ValidQuarter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhishingCampaignRequest {
    @NotBlank(message = "Campaign name is required")
    private String campaignName;

    private String description;

    @NotBlank(message = "Template type is required")
    private String templateType;

    private String difficultyLevel;

    @NotEmpty(message = "Target audience is required")
    private List<String> targetAudience;

    @NotNull(message = "Scheduled start is required")
    private LocalDateTime scheduledStart;

    @NotNull(message = "Scheduled end is required")
    private LocalDateTime scheduledEnd;

    private UUID createdBy;
}
