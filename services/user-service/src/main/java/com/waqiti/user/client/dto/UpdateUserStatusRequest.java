package com.waqiti.user.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Request to update a user's status in the external system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequest {
    private String externalId;
    private String externalSystem; // "INTERNAL" (legacy field for backwards compatibility)
    private String status;
}
