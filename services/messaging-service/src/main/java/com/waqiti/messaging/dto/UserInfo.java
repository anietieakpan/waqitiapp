package com.waqiti.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    
    private String userId;
    private String name;
    private String avatarUrl;
    private String phoneNumber;
    private String email;
    private Boolean isVerified;
}