package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationResponse {
    private String deviceId;
    private String userId;
    private boolean registered;
    private LocalDateTime registeredAt;
    private String message;
}