package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveChatMessageRequest {
    
    // Message content
    @NotBlank(message = "Message content is required")
    @Size(max = 1000, message = "Message cannot exceed 1000 characters")
    private String message;
    
    // Message type
    private MessageType messageType = MessageType.TEXT;
    
    // Attachments
    private List<MultipartFile> attachments;
    
    // Rich content
    private String htmlContent;
    private List<QuickReplyOption> quickReplies;
    private FileShareInfo fileShare;
    
    // Message metadata
    private Map<String, String> metadata;
    
    // Sender context (set by service)
    private String senderId;
    private String senderName;
    private SenderRole senderRole;
    
    // Special message flags
    private boolean isSystemMessage = false;
    private boolean isPrivate = false;
    private boolean requiresResponse = false;
    
    // Auto-actions
    private boolean enableTypingIndicator = true;
    private boolean markAsRead = true;
    
    // AI assistance
    private boolean enableAISuggestions = false;
    private boolean checkForAutoResponse = false;
    
    public enum MessageType {
        TEXT,
        FILE,
        IMAGE,
        SYSTEM,
        TYPING_INDICATOR,
        QUICK_REPLY,
        FORM_RESPONSE
    }
    
    public enum SenderRole {
        CUSTOMER,
        AGENT,
        SYSTEM,
        BOT
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuickReplyOption {
        private String text;
        private String value;
        private String action;
        private Map<String, String> payload;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileShareInfo {
        private String fileId;
        private String fileName;
        private String fileType;
        private Long fileSize;
        private String downloadUrl;
        private String thumbnailUrl;
    }
}