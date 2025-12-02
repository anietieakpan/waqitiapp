package com.waqiti.grouppayment.dto;

import com.waqiti.grouppayment.entity.GroupPayment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupPaymentRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Total amount must be greater than 0")
    @Digits(integer = 17, fraction = 2, message = "Invalid amount format")
    private BigDecimal totalAmount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter code")
    private String currency;

    @NotNull(message = "Split type is required")
    private GroupPayment.SplitType splitType;

    private String receiptImageUrl;

    private String category;

    private Instant dueDate;

    @NotEmpty(message = "At least one participant is required")
    @Valid
    private List<ParticipantRequest> participants;

    @Valid
    private List<ItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantRequest {
        @NotBlank(message = "User ID or email is required")
        private String userIdOrEmail;

        @Email(message = "Invalid email format")
        private String email;

        private String displayName;

        @DecimalMin(value = "0", message = "Owed amount cannot be negative")
        private BigDecimal owedAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRequest {
        @NotBlank(message = "Item name is required")
        private String name;

        private String description;

        @NotNull(message = "Item amount is required")
        @DecimalMin(value = "0.01", message = "Item amount must be greater than 0")
        private BigDecimal amount;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        private String category;

        @Valid
        private List<ItemParticipantRequest> participants;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemParticipantRequest {
        @NotBlank(message = "User ID is required")
        private String userId;

        @NotNull(message = "Share is required")
        @DecimalMin(value = "0", message = "Share cannot be negative")
        private BigDecimal share;

        @NotNull(message = "Share type is required")
        private ShareType shareType;

        public enum ShareType {
            AMOUNT,
            PERCENTAGE,
            QUANTITY
        }
    }
}