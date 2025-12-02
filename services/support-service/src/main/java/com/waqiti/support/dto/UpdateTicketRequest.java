package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTicketRequest {
    
    // Status and priority updates
    private TicketDTO.TicketStatus status;
    private TicketDTO.TicketPriority priority;
    
    // Assignment updates
    private String assignedTo;
    private String assignedTeam;
    
    // Categorization updates
    private String category;
    private String subcategory;
    
    // Content updates
    @Size(max = 500, message = "Subject cannot exceed 500 characters")
    private String subject;
    
    private String description;
    
    // Tags
    private List<String> tags;
    
    // Resolution
    @Size(max = 2000, message = "Resolution summary cannot exceed 2000 characters")
    private String resolutionSummary;
    
    private String resolutionCategory;
    
    // SLA updates
    private String slaTier;
    
    // Metadata updates
    private String language;
    private String timezone;
    
    // References
    private String relatedTransactionId;
    private String relatedAccountId;
    private String externalTicketId;
    
    // Internal notes
    @Size(max = 1000, message = "Internal notes cannot exceed 1000 characters")
    private String internalNotes;
    
    // Update context
    private String performedBy;
    private String performedByName;
    private String updateReason;
    
    // Notification preferences
    private boolean notifyCustomer;
    private boolean notifyAssignedAgent;
    private boolean notifyTeam;
    
    // Auto-actions
    private boolean triggerEscalation;
    private boolean sendAutoResponse;
}