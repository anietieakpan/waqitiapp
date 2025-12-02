package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectAgentRequest {
    
    // Initial message/query
    @Size(max = 1000, message = "Initial message cannot exceed 1000 characters")
    private String initialMessage;
    
    // Chat category/type
    private LiveChatSession.ChatType chatType = LiveChatSession.ChatType.SUPPORT;
    private String category;
    private String subcategory;
    
    // Priority and urgency
    private TicketDTO.TicketPriority priority = TicketDTO.TicketPriority.MEDIUM;
    private boolean isUrgent = false;
    
    // Agent preferences
    private String preferredAgentId;
    private List<String> preferredDepartments;
    private List<String> requiredLanguages;
    private List<String> requiredSkills;
    
    // Context information
    private String relatedTicketId;
    private String relatedTransactionId;
    private String relatedAccountId;
    
    // Customer information
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String customerTier;
    
    // Session context
    private String sessionId; // For AI handoff scenarios
    private List<ChatMessage> previousMessages; // From AI chat
    private String handoffReason;
    
    // Technical context
    private String browserInfo;
    private String deviceInfo;
    private String referrerUrl;
    private String currentPageUrl;
    
    // Additional metadata
    private Map<String, String> metadata;
    
    // Preferences
    private String preferredLanguage = "en";
    private boolean allowQueueing = true;
    private Integer maxWaitTimeMinutes;
    
    // Notification settings
    private boolean enableTypingIndicator = true;
    private boolean enablePushNotifications = true;
}