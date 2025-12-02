package com.waqiti.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CardPinSetRequest DTO - Request to set/change card PIN
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardPinSetRequest {

    @NotBlank(message = "New PIN is required")
    @Size(min = 4, max = 6)
    @Pattern(regexp = "^[0-9]{4,6}$", message = "PIN must be 4-6 digits")
    private String newPin;

    @Size(min = 4, max = 6)
    @Pattern(regexp = "^[0-9]{4,6}$", message = "PIN must be 4-6 digits")
    private String currentPin;

    @NotBlank(message = "PIN confirmation is required")
    @Size(min = 4, max = 6)
    @Pattern(regexp = "^[0-9]{4,6}$", message = "PIN must be 4-6 digits")
    private String confirmPin;
}
