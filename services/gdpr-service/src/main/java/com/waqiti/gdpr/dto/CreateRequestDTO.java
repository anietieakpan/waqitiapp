package com.waqiti.gdpr.dto;

import com.waqiti.gdpr.domain.ExportFormat;
import com.waqiti.gdpr.domain.RequestType;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
public class CreateRequestDTO {
    
    @NotNull(message = "Request type is required")
    private RequestType requestType;
    
    @NotNull(message = "At least one data category must be specified")
    @Size(min = 1, message = "At least one data category must be specified")
    private List<String> dataCategories;
    
    private ExportFormat exportFormat;
    
    @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
    private String notes;
}