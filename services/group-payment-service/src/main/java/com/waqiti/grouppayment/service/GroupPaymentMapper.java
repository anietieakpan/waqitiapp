package com.waqiti.grouppayment.service;

import com.waqiti.grouppayment.dto.GroupPaymentResponse;
import com.waqiti.grouppayment.entity.GroupPayment;
import com.waqiti.grouppayment.entity.GroupPaymentItem;
import com.waqiti.grouppayment.entity.GroupPaymentItemParticipant;
import com.waqiti.grouppayment.entity.GroupPaymentParticipant;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GroupPaymentMapper {

    public GroupPaymentResponse toResponse(GroupPayment groupPayment) {
        return GroupPaymentResponse.builder()
            .groupPaymentId(groupPayment.getGroupPaymentId())
            .createdBy(groupPayment.getCreatedBy())
            .title(groupPayment.getTitle())
            .description(groupPayment.getDescription())
            .totalAmount(groupPayment.getTotalAmount())
            .currency(groupPayment.getCurrency())
            .status(groupPayment.getStatus())
            .splitType(groupPayment.getSplitType())
            .receiptImageUrl(groupPayment.getReceiptImageUrl())
            .category(groupPayment.getCategory())
            .dueDate(groupPayment.getDueDate())
            .createdAt(groupPayment.getCreatedAt())
            .updatedAt(groupPayment.getUpdatedAt())
            .participants(toParticipantResponses(groupPayment.getParticipants()))
            .items(toItemResponses(groupPayment.getItems()))
            .summary(createPaymentSummary(groupPayment))
            .build();
    }

    private List<GroupPaymentResponse.ParticipantResponse> toParticipantResponses(List<GroupPaymentParticipant> participants) {
        if (participants == null) {
            return List.of();
        }

        return participants.stream()
            .map(this::toParticipantResponse)
            .collect(Collectors.toList());
    }

    private GroupPaymentResponse.ParticipantResponse toParticipantResponse(GroupPaymentParticipant participant) {
        return GroupPaymentResponse.ParticipantResponse.builder()
            .userId(participant.getUserId())
            .email(participant.getEmail())
            .displayName(participant.getDisplayName())
            .owedAmount(participant.getOwedAmount())
            .paidAmount(participant.getPaidAmount())
            .remainingAmount(participant.getRemainingAmount())
            .status(participant.getStatus().name())
            .paymentMethod(participant.getPaymentMethod())
            .paidAt(participant.getPaidAt())
            .invitedAt(participant.getInvitedAt())
            .acceptedAt(participant.getAcceptedAt())
            .isPaidInFull(participant.isPaidInFull())
            .build();
    }

    private List<GroupPaymentResponse.ItemResponse> toItemResponses(List<GroupPaymentItem> items) {
        if (items == null) {
            return List.of();
        }

        return items.stream()
            .map(this::toItemResponse)
            .collect(Collectors.toList());
    }

    private GroupPaymentResponse.ItemResponse toItemResponse(GroupPaymentItem item) {
        return GroupPaymentResponse.ItemResponse.builder()
            .id(item.getId())
            .name(item.getName())
            .description(item.getDescription())
            .amount(item.getAmount())
            .quantity(item.getQuantity())
            .totalAmount(item.getTotalAmount())
            .category(item.getCategory())
            .participants(toItemParticipantResponses(item.getItemParticipants()))
            .build();
    }

    private List<GroupPaymentResponse.ItemParticipantResponse> toItemParticipantResponses(List<GroupPaymentItemParticipant> itemParticipants) {
        if (itemParticipants == null) {
            return List.of();
        }

        return itemParticipants.stream()
            .map(this::toItemParticipantResponse)
            .collect(Collectors.toList());
    }

    private GroupPaymentResponse.ItemParticipantResponse toItemParticipantResponse(GroupPaymentItemParticipant itemParticipant) {
        return GroupPaymentResponse.ItemParticipantResponse.builder()
            .userId(itemParticipant.getUserId())
            .share(itemParticipant.getShare())
            .shareType(itemParticipant.getShareType().name())
            .build();
    }

    private GroupPaymentResponse.PaymentSummary createPaymentSummary(GroupPayment groupPayment) {
        List<GroupPaymentParticipant> participants = groupPayment.getParticipants();
        
        if (participants == null || participants.isEmpty()) {
            return GroupPaymentResponse.PaymentSummary.builder()
                .totalAmount(groupPayment.getTotalAmount())
                .totalPaid(BigDecimal.ZERO)
                .totalOutstanding(groupPayment.getTotalAmount())
                .totalParticipants(0)
                .paidParticipants(0)
                .percentageComplete(BigDecimal.ZERO)
                .isFullyPaid(false)
                .build();
        }

        BigDecimal totalPaid = participants.stream()
            .map(GroupPaymentParticipant::getPaidAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = groupPayment.getTotalAmount().subtract(totalPaid);

        int totalParticipants = participants.size();
        int paidParticipants = (int) participants.stream()
            .filter(GroupPaymentParticipant::isPaidInFull)
            .count();

        BigDecimal percentageComplete = totalPaid
            .divide(groupPayment.getTotalAmount(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);

        boolean isFullyPaid = totalOutstanding.compareTo(BigDecimal.ZERO) <= 0;

        return GroupPaymentResponse.PaymentSummary.builder()
            .totalAmount(groupPayment.getTotalAmount())
            .totalPaid(totalPaid)
            .totalOutstanding(totalOutstanding)
            .totalParticipants(totalParticipants)
            .paidParticipants(paidParticipants)
            .percentageComplete(percentageComplete)
            .isFullyPaid(isFullyPaid)
            .build();
    }
}