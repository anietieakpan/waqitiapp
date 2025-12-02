package com.waqiti.card.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * CardAuthorizationRequest DTO - Authorization request
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardAuthorizationRequest {

    @NotNull(message = "Card ID is required")
    private UUID cardId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3)
    private String currencyCode;

    @Size(max = 100)
    private String merchantId;

    @Size(max = 255)
    private String merchantName;

    @Size(min = 4, max = 4)
    private String merchantCategoryCode;

    @Size(min = 2, max = 3)
    private String merchantCountry;

    @Size(max = 3)
    private String posEntryMode;

    @Size(max = 50)
    private String terminalId;

    private Boolean isOnline;

    private Boolean isContactless;

    private Boolean isCardPresent;

    private Map<String, Object> posData;

    private Map<String, Object> metadata;
}
