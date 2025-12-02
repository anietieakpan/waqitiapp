package com.waqiti.legal.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Request DTO for updating subpoena status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSubpoenaStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;

    private String statusNotes;
    private String updatedBy;
}
