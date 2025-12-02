package com.waqiti.gamification.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddPointsRequest {
    
    @NotNull(message = "Points amount is required")
    @Min(value = 1, message = "Points must be positive")
    private Long points;
    
    @NotBlank(message = "Event type is required")
    private String eventType;
    
    private String description;
    
    private String referenceId;
    
    private String sourceService;
}