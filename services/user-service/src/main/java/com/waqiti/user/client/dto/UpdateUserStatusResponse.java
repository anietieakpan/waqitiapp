package com.waqiti.user.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response from updating a user's status in the external system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusResponse {
    private String externalId;
    private String status;
}