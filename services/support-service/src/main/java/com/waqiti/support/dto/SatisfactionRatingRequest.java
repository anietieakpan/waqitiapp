package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionRatingRequest {
    
    // Overall satisfaction rating
    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;
    
    // Detailed feedback
    @Size(max = 2000, message = "Feedback cannot exceed 2000 characters")
    private String feedback;
    
    // Specific aspects ratings
    @Min(value = 1, message = "Response time rating must be between 1 and 5")
    @Max(value = 5, message = "Response time rating must be between 1 and 5")
    private Integer responseTimeRating;
    
    @Min(value = 1, message = "Helpfulness rating must be between 1 and 5")
    @Max(value = 5, message = "Helpfulness rating must be between 1 and 5")
    private Integer helpfulnessRating;
    
    @Min(value = 1, message = "Professionalism rating must be between 1 and 5")
    @Max(value = 5, message = "Professionalism rating must be between 1 and 5")
    private Integer professionalismRating;
    
    @Min(value = 1, message = "Resolution quality rating must be between 1 and 5")
    @Max(value = 5, message = "Resolution quality rating must be between 1 and 5")
    private Integer resolutionQualityRating;
    
    // What went well
    private List<String> positiveAspects;
    
    // Areas for improvement
    private List<String> improvementAreas;
    
    // Specific agent feedback
    private String agentId;
    
    @Size(max = 1000, message = "Agent feedback cannot exceed 1000 characters")
    private String agentFeedback;
    
    // Would recommend service
    private Boolean wouldRecommend;
    
    // Contact preferences for follow-up
    private boolean allowFollowUpContact = false;
    private String preferredContactMethod; // EMAIL, PHONE, CHAT
    
    // Anonymous feedback option
    private boolean isAnonymous = false;
}