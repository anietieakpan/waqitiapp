package com.waqiti.grouppayment.dto;

import com.waqiti.grouppayment.entity.GroupPayment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPaymentResponse {

    private String groupPaymentId;
    private String createdBy;
    private String title;
    private String description;
    private BigDecimal totalAmount;
    private String currency;
    private GroupPayment.GroupPaymentStatus status;
    private GroupPayment.SplitType splitType;
    private String receiptImageUrl;
    private String category;
    private Instant dueDate;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ParticipantResponse> participants;
    private List<ItemResponse> items;
    private PaymentSummary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantResponse {
        private String userId;
        private String email;
        private String displayName;
        private BigDecimal owedAmount;
        private BigDecimal paidAmount;
        private BigDecimal remainingAmount;
        private String status;
        private String paymentMethod;
        private Instant paidAt;
        private Instant invitedAt;
        private Instant acceptedAt;
        private boolean isPaidInFull;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResponse {
        private Long id;
        private String name;
        private String description;
        private BigDecimal amount;
        private Integer quantity;
        private BigDecimal totalAmount;
        private String category;
        private List<ItemParticipantResponse> participants;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemParticipantResponse {
        private String userId;
        private BigDecimal share;
        private String shareType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSummary {
        private BigDecimal totalAmount;
        private BigDecimal totalPaid;
        private BigDecimal totalOutstanding;
        private int totalParticipants;
        private int paidParticipants;
        private BigDecimal percentageComplete;
        private boolean isFullyPaid;
    }
}