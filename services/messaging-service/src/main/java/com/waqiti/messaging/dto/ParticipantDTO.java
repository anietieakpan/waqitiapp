package com.waqiti.messaging.dto;

import com.waqiti.messaging.domain.ParticipantRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantDTO {
    
    private String userId;
    private String userName;
    private String userAvatar;
    private ParticipantRole role;
    private LocalDateTime joinedAt;
    private Boolean isAdmin;
    private String nickname;
    private Boolean isOnline;
    private LocalDateTime lastSeenAt;
}