package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketRequest {
    @NotBlank(message = "Subject is required")
    private String subject;
    
    @NotBlank(message = "Description is required")
    private String description;
    
    @NotNull(message = "User ID is required")
    private String userId;
    
    private String category;
    private String priority;
    private List<String> tags;
    private Map<String, Object> metadata;
    private String sessionId;
    private List<String> attachmentUrls;
}