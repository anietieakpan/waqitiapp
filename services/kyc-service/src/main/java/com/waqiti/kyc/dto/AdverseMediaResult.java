package com.waqiti.kyc.dto;

import com.waqiti.kyc.service.AdverseMediaService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing adverse media screening results
 * 
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-01-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdverseMediaResult {
    
    private String fullName;
    private int mentionCount;
    private int highRiskMentions;
    private int mediumRiskMentions;
    private int lowRiskMentions;
    private String severity; // NONE, LOW, MEDIUM, HIGH
    private List<String> categories;
    private List<AdverseMediaService.MediaMention> mentions;
    private boolean searchCompleted;
    private String errorMessage;
    private LocalDateTime screeningDate;
    
    public boolean hasHighRiskMentions() {
        return highRiskMentions > 0;
    }
    
    public boolean hasMediumRiskMentions() {
        return mediumRiskMentions > 0;
    }
    
    public boolean hasAdverseNews() {
        return mentionCount > 0;
    }
}