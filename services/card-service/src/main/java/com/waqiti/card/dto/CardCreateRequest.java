package com.waqiti.card.dto;

import com.waqiti.card.enums.CardBrand;
import com.waqiti.card.enums.CardType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * CardCreateRequest DTO - Request to create a new card
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardCreateRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotNull(message = "Card type is required")
    private CardType cardType;

    @NotNull(message = "Card brand is required")
    private CardBrand cardBrand;

    @NotBlank(message = "Product ID is required")
    @Size(max = 100)
    private String productId;

    @Size(max = 255)
    private String embossedName;

    @DecimalMin(value = "0.00", message = "Credit limit must be positive")
    private BigDecimal creditLimit;

    private BigDecimal dailySpendLimit;

    private BigDecimal monthlySpendLimit;

    private Boolean isVirtual;

    private Boolean isContactless;

    private Boolean isInternationalEnabled;

    private Boolean isOnlineEnabled;

    @Size(max = 500)
    private String deliveryAddress;
}
