package com.waqiti.messaging.service;

import com.waqiti.messaging.dto.UserInfo;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    
    public UserInfo getUserInfo(String userId) {
        // In production, integrate with user service
        // For now, return mock data
        return UserInfo.builder()
            .userId(userId)
            .name("User " + userId)
            .avatarUrl("https://ui-avatars.com/api/?name=User+" + userId)
            .build();
    }
    
    public boolean hasKeys(String userId) {
        // Check if user has encryption keys initialized
        return true; // Mock implementation
    }
}