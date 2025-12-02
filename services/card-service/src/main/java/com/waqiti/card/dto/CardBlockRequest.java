package com.waqiti.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CardBlockRequest DTO - Request to block a card
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardBlockRequest {

    @NotBlank(message = "Reason is required")
    @Size(max = 255)
    private String reason;

    private Boolean reportLostStolen;

    private Boolean reportFraud;
}
