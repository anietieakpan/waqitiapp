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
public class TicketMessageDTO {
    
    private String id;
    
    @NotBlank
    private String ticketId;
    
    // Message type and sender
    @NotNull
    private MessageType messageType;
    
    private String senderId;
    private String senderName;
    private String senderEmail;
    
    // Content
    private String subject;
    
    @NotBlank
    private String content;
    
    @NotNull
    private ContentType contentType;
    
    // Visibility and status
    private boolean isInternal;
    private boolean isPublic;
    private boolean isRead;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime readAt;
    
    private String readBy;
    
    // Attachments
    private List<TicketDetailsDTO.TicketAttachmentDTO> attachments;
    
    // AI analysis
    private String detectedIntent;
    private String sentiment;
    private Double sentimentScore;
    
    // Audit fields
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime updatedAt;
    
    public enum MessageType {
        CUSTOMER, AGENT, SYSTEM, INTERNAL
    }
    
    public enum ContentType {
        TEXT, HTML, MARKDOWN
    }
}