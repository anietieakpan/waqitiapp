package com.waqiti.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationResponse {
    private String userId;
    private String email;
    private String status;
    private boolean success;
    private String message;
    private String accountId;
}