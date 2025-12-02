package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketFilterRequest {
    
    // Status filters
    private List<TicketDTO.TicketStatus> statuses;
    
    // Priority filters
    private List<TicketDTO.TicketPriority> priorities;
    
    // Category filters
    private List<String> categories;
    private List<String> subcategories;
    
    // Assignment filters
    private List<String> assignedAgents;
    private List<String> teams;
    private Boolean isUnassigned;
    private Boolean isEscalated;
    
    // Customer filters
    private String customerId;
    private String customerEmail;
    private Boolean isVipCustomer;
    
    // Date filters
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdAfter;
    
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdBefore;
    
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime updatedAfter;
    
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime updatedBefore;
    
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime dueBefore;
    
    // Content filters
    private String searchQuery;
    private List<String> tags;
    
    // SLA filters
    private Boolean isSlaBreached;
    private Boolean isOverdue;
    private List<String> slaTiers;
    
    // Channel filters
    private List<TicketDTO.TicketChannel> channels;
    
    // Language filters
    private List<String> languages;
    
    // Satisfaction filters
    private Integer minSatisfactionRating;
    private Integer maxSatisfactionRating;
    private Boolean hasFeedback;
    
    // AI analysis filters
    private List<String> detectedIntents;
    private Double minSentimentScore;
    private Double maxSentimentScore;
    
    // Reference filters
    private String relatedTransactionId;
    private String relatedAccountId;
    private String externalTicketId;
    
    // Sorting options
    private String sortBy; // createdAt, updatedAt, priority, status, dueDate
    private String sortDirection; // ASC, DESC
}