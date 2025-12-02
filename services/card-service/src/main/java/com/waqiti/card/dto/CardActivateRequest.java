package com.waqiti.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CardActivateRequest DTO - Request to activate a card
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardActivateRequest {

    @NotBlank(message = "Card ID is required")
    private String cardId;

    @NotBlank(message = "Activation code is required")
    @Size(min = 6, max = 10)
    private String activationCode;

    @NotBlank(message = "CVV is required")
    @Size(min = 3, max = 4)
    private String cvv;

    @Size(min = 4, max = 4)
    private String lastFourDigits;
}
