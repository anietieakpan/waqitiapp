package com.waqiti.support.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDTO {
    
    private String id;
    
    @NotBlank
    private String ticketNumber;
    
    // Customer information
    private String userId;
    private String customerEmail;
    private String customerName;
    private String customerPhone;
    
    // Ticket content
    @NotBlank
    private String subject;
    
    @NotBlank
    private String description;
    
    @NotNull
    private String category;
    private String subcategory;
    
    // Status and priority
    @NotNull
    private TicketStatus status;
    
    @NotNull
    private TicketPriority priority;
    
    // Assignment
    private String assignedTo;
    private String assignedToName;
    private String assignedTeam;
    
    // Escalation
    private String escalatedTo;
    private String escalatedToName;
    private String escalationReason;
    
    // SLA information
    private String slaTier;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime firstResponseDueAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime resolutionDueAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime firstResponseAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime resolvedAt;
    
    // Resolution
    private String resolutionSummary;
    private String resolutionCategory;
    
    // Satisfaction
    private Integer customerSatisfactionRating;
    private String customerFeedback;
    
    // Metadata
    private TicketChannel channel;
    private String language;
    private String timezone;
    
    // Tags and references
    private List<String> tags;
    private String relatedTransactionId;
    private String relatedAccountId;
    private String externalTicketId;
    
    // AI analysis
    private String detectedIntent;
    private Double sentimentScore;
    private List<String> suggestedTags;
    
    // Statistics
    private int messageCount;
    private int attachmentCount;
    
    // Audit fields
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime updatedAt;
    
    private String createdBy;
    private String updatedBy;
    
    // Helper flags
    private boolean isSlaBreached;
    private boolean isOverdue;
    private boolean isVip;
    private boolean hasUnreadMessages;
    
    public enum TicketStatus {
        OPEN, IN_PROGRESS, WAITING_CUSTOMER, WAITING_INTERNAL, RESOLVED, CLOSED, CANCELLED
    }
    
    public enum TicketPriority {
        LOW, MEDIUM, HIGH, URGENT, CRITICAL
    }
    
    public enum TicketChannel {
        EMAIL, CHAT, PHONE, WEB, MOBILE, SOCIAL
    }
}