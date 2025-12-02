package com.waqiti.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionDTO {
    
    private String userId;
    private String userName;
    private String emoji;
    private LocalDateTime createdAt;
}