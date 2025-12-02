package com.waqiti.messaging.dto;

import com.waqiti.messaging.domain.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    
    @NotBlank
    private String conversationId;
    
    @NotBlank
    private String senderId;
    
    @NotNull
    private MessageType messageType;
    
    @NotBlank
    @Size(max = 5000)
    private String content;
    
    private Boolean isEphemeral;
    
    private Integer ephemeralDuration;
    
    private String replyToMessageId;
    
    private List<AttachmentRequest> attachments;
}