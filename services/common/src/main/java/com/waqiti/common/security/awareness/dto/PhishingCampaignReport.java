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
public class PhishingCampaignReport {
    private UUID campaignId;
    private String campaignName;
    private Integer totalTargeted;
    private Integer totalDelivered;
    private Integer totalOpened;
    private Integer totalClicked;
    private Integer totalSubmitted;
    private Integer totalReported;
    private Double openRate;
    private Double clickRate;
    private Double submitRate;
    private Double reportRate;
}
