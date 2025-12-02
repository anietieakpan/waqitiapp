package com.waqiti.user.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Response from updating a user in the external system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserResponse {
    private String externalId;
    private String status;
}
