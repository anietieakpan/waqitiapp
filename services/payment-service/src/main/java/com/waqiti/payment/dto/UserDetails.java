package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * DTO representing user details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDetails {
    
    private String userId;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String status;
    private boolean verified;
    private boolean active;
    private String kycStatus;
    private String riskLevel;
    private Instant createdAt;
    private Instant lastLoginAt;
    private String country;
    private String currency;
    private String timezone;
    private String language;
    private Map<String, Object> metadata;
}