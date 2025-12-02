package com.waqiti.user.client;

import com.waqiti.user.client.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class IntegrationServiceClientFallback implements IntegrationServiceClient {

    @Override
    public CreateUserResponse createUser(CreateUserRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING user creation - Integration Service unavailable. " +
                "Email: {}", request.getEmail());
        
        return CreateUserResponse.builder()
                .success(false)
                .userId(null)
                .status("FAILED")
                .message("User creation temporarily unavailable - integration service down")
                .errorCode("INTEGRATION_SERVICE_UNAVAILABLE")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    public UpdateUserResponse updateUser(UpdateUserRequest request) {
        log.warn("FALLBACK ACTIVATED: QUEUING user update - Integration Service unavailable. " +
                "UserId: {}", request.getUserId());
        
        // Queue non-critical updates
        return UpdateUserResponse.builder()
                .success(true)
                .userId(request.getUserId())
                .status("QUEUED")
                .message("User update queued for processing - will be applied when service recovers")
                .queuedAt(LocalDateTime.now())
                .estimatedProcessingTime("Within 2 hours")
                .build();
    }

    @Override
    public UpdateUserStatusResponse updateUserStatus(UpdateUserStatusRequest request) {
        log.error("FALLBACK ACTIVATED: Cannot update user status - Integration Service unavailable. " +
                "UserId: {}, NewStatus: {}", request.getUserId(), request.getNewStatus());
        
        // Block critical status changes
        boolean isCritical = "SUSPENDED".equals(request.getNewStatus()) || 
                           "BLOCKED".equals(request.getNewStatus()) ||
                           "DELETED".equals(request.getNewStatus());
        
        if (isCritical) {
            log.error("CRITICAL STATUS CHANGE BLOCKED - Manual intervention required");
            return UpdateUserStatusResponse.builder()
                    .success(false)
                    .userId(request.getUserId())
                    .currentStatus(null)
                    .requestedStatus(request.getNewStatus())
                    .message("Critical status change blocked - requires manual processing")
                    .errorCode("CRITICAL_STATUS_CHANGE_BLOCKED")
                    .requiresManualIntervention(true)
                    .build();
        }
        
        // Queue non-critical status changes
        return UpdateUserStatusResponse.builder()
                .success(true)
                .userId(request.getUserId())
                .currentStatus(null)
                .requestedStatus(request.getNewStatus())
                .message("Status update queued for processing")
                .queuedAt(LocalDateTime.now())
                .build();
    }
}