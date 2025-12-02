package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTopicRequest {
    
    @NotBlank(message = "Topic name is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Topic name can only contain letters, numbers, underscores, and hyphens")
    private String name;
    
    @NotBlank(message = "Display name is required")
    private String displayName;
    
    private String description;
    
    @NotBlank(message = "Category is required")
    private String category;
    
    @Builder.Default
    private boolean active = true;
    
    @Builder.Default
    private boolean autoSubscribe = false;
    
    @Builder.Default
    private int priority = 0;
    
    private String icon;
    
    private String color;
}