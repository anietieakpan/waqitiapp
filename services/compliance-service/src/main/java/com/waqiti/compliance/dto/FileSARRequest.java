package com.waqiti.compliance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSARRequest {
    
    @NotNull
    private UUID subjectUserId;
    
    @NotNull
    private String suspiciousActivity;
    
    @NotNull
    private LocalDate activityStartDate;
    
    @NotNull
    private LocalDate activityEndDate;
    
    @NotNull
    private BigDecimal totalAmount;
    
    @NotNull
    private String currency;
    
    private List<String> transactionIds;
    private String narrativeDescription;
    private List<String> redFlags;
    private String filingReason;
    private Boolean lawEnforcementNotified;
    private String additionalInformation;
}