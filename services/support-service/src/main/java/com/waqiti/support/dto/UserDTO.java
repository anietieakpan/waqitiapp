package com.waqiti.support.dto;

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
public class UserDTO {
    
    private String id;
    private String username;
    private String email;
    private String name;
    private String firstName;
    private String lastName;
    private String phone;
    
    // Profile information
    private String avatarUrl;
    private String timezone;
    private String language;
    private String country;
    
    // User status
    private boolean isActive;
    private boolean isVerified;
    private boolean isVip;
    
    // Customer tier and preferences
    private String customerTier; // BASIC, PREMIUM, VIP
    private String preferredContactMethod; // EMAIL, PHONE, CHAT, SMS
    
    // Roles and permissions
    private List<String> roles;
    private List<String> permissions;
    
    // Account information
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    
    // Additional metadata
    private Map<String, Object> metadata;
}