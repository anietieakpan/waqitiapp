package com.waqiti.support.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveChatSession {
    
    private String sessionId;
    private String userId;
    private String userEmail;
    private String userName;
    
    // Agent assignment
    private String agentId;
    private String agentName;
    private String agentAvatarUrl;
    
    // Session status
    private ChatStatus status;
    private ChatType chatType;
    
    // Queue information
    private Integer queuePosition;
    private Integer estimatedWaitTimeMinutes;
    
    // Session timing
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime endTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime lastActivity;
    
    // Session statistics
    private Integer messageCount;
    private Long averageResponseTimeMs;
    
    // Initial context
    private String initialQuery;
    private String category;
    private List<String> tags;
    
    // AI handoff information
    private boolean wasAIHandoff;
    private String handoffReason;
    private List<ChatMessage> preChatMessages;
    
    // Session metadata
    private Map<String, String> metadata;
    
    // Customer information
    private boolean isVipCustomer;
    private String customerTier;
    private String preferredLanguage;
    
    // Technical details
    private String browserInfo;
    private String ipAddress;
    private String referrerUrl;
    
    // Satisfaction
    private Integer satisfactionRating;
    private String satisfactionFeedback;
    
    public enum ChatStatus {
        QUEUED,
        ACTIVE,
        TRANSFERRED,
        ENDED,
        EXPIRED,
        CANCELLED
    }
    
    public enum ChatType {
        SUPPORT,
        SALES,
        BILLING,
        TECHNICAL,
        GENERAL
    }
}