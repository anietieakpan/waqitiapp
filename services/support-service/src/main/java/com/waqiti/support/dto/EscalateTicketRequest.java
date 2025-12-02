package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscalateTicketRequest {
    
    // Escalation target
    private String escalateTo; // Agent ID
    private String escalateToTeam; // Team name
    
    // Escalation details
    @NotBlank(message = "Escalation reason is required")
    @Size(max = 1000, message = "Escalation reason cannot exceed 1000 characters")
    private String reason;
    
    // Urgency and priority
    private TicketDTO.TicketPriority newPriority;
    private EscalationType escalationType;
    
    // Context information
    @Size(max = 2000, message = "Context notes cannot exceed 2000 characters")
    private String contextNotes;
    
    // Previous attempts
    @Size(max = 500, message = "Previous attempts description cannot exceed 500 characters")
    private String previousAttempts;
    
    // Customer impact
    private CustomerImpactLevel customerImpact;
    
    @Size(max = 500, message = "Impact description cannot exceed 500 characters")
    private String impactDescription;
    
    // Escalation options
    private boolean notifyCustomer = false;
    private boolean increasePriority = true;
    private boolean resetSlaTimer = false;
    
    // Performer information (set by controller)
    private String escalatedBy;
    private String escalatedByName;
    
    public enum EscalationType {
        TECHNICAL,
        BILLING,
        POLICY,
        MANAGEMENT,
        SPECIALIST,
        URGENT
    }
    
    public enum CustomerImpactLevel {
        LOW,
        MEDIUM, 
        HIGH,
        CRITICAL
    }
}