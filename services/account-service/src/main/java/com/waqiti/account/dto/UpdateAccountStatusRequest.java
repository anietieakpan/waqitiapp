package com.waqiti.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAccountStatusRequest {
    @NotBlank(message = "Status is required")
    private String status;
    private String reason;
}