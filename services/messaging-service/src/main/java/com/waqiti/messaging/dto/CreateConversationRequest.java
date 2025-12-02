package com.waqiti.messaging.dto;

import com.waqiti.messaging.domain.ConversationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {
    
    @NotNull
    private ConversationType type;
    
    @Size(max = 100)
    private String name;
    
    @Size(max = 500)
    private String description;
    
    private String avatarUrl;
    
    @NotEmpty
    private List<String> participantIds;
    
    private String creatorId; // Set by controller
    
    private Boolean isEncrypted = true;
    
    private Boolean ephemeralMessagesEnabled = false;
    
    private Integer defaultEphemeralDuration;
    
    private Boolean adminApprovalRequired = false;
}