package com.waqiti.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID; /**
 * Response for user operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private String phoneNumber;
    private String status;
    private String kycStatus;
    private Set<String> roles;
    private LocalDateTime createdAt;
    private UserProfileResponse profile;
}
