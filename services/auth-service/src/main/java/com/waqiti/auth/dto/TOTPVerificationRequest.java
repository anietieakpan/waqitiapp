package com.waqiti.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TOTPVerificationRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "TOTP code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "TOTP code must be 6 digits")
    private String code;

    private String deviceId;

    private Boolean trustDevice;
}
