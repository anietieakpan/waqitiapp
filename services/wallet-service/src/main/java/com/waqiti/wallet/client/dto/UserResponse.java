package com.waqiti.wallet.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Response DTO for user-service integration
 * 
 * Contains essential user information needed by wallet-service
 * for user validation and wallet operations.
 * 
 * @author Waqiti Development Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    
    private UUID id;
    
    private String username;
    
    private String email;
    
    private String firstName;
    
    private String lastName;
    
    private String phoneNumber;
    
    private Boolean emailVerified;
    
    private Boolean phoneVerified;
    
    private String kycStatus;
    
    private String accountStatus;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}