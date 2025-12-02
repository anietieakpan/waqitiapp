package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMessageRequest {
    
    // Message content
    @Size(max = 500, message = "Subject cannot exceed 500 characters")
    private String subject;
    
    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message content cannot exceed 5000 characters")
    private String content;
    
    // Message type
    private TicketMessageDTO.ContentType contentType = TicketMessageDTO.ContentType.TEXT;
    
    // Sender information (usually set by controller)
    private String senderId;
    private String senderName;
    private TicketMessageDTO.MessageType senderType;
    
    // Visibility options
    private boolean isInternal = false;
    private boolean isPublic = true;
    
    // Attachments
    private List<MultipartFile> attachments;
    
    // Notification preferences
    private boolean notifyCustomer = true;
    private boolean notifyAssignedAgent = true;
    private boolean notifyTeam = false;
    
    // Auto-actions
    private boolean markAsRead = false;
    private boolean updateTicketStatus = false;
    private TicketDTO.TicketStatus newTicketStatus;
    
    // AI processing options
    private boolean enableAIAnalysis = true;
    private boolean checkForAutoResponse = true;
    
    // Template usage
    private String templateId;
    private String templateVariables; // JSON string of variables
}