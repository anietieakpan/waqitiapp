package com.waqiti.grouppayment.service;

import com.waqiti.grouppayment.dto.CreateGroupPaymentRequest;
import com.waqiti.grouppayment.entity.GroupPayment;
import com.waqiti.grouppayment.entity.GroupPaymentParticipant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SplitCalculatorService {

    public List<GroupPaymentParticipant> calculateSplits(
            GroupPayment groupPayment,
            List<CreateGroupPaymentRequest.ParticipantRequest> participantRequests,
            List<CreateGroupPaymentRequest.ItemRequest> itemRequests) {

        log.debug("Calculating splits for group payment: {} with type: {}", 
            groupPayment.getGroupPaymentId(), groupPayment.getSplitType());

        return switch (groupPayment.getSplitType()) {
            case EQUAL -> calculateEqualSplit(groupPayment, participantRequests);
            case CUSTOM_AMOUNTS -> calculateCustomAmountSplit(groupPayment, participantRequests);
            case PERCENTAGE -> calculatePercentageSplit(groupPayment, participantRequests);
            case BY_ITEM -> calculateItemBasedSplit(groupPayment, participantRequests, itemRequests);
            case BY_WEIGHT -> calculateWeightBasedSplit(groupPayment, participantRequests);
        };
    }

    private List<GroupPaymentParticipant> calculateEqualSplit(
            GroupPayment groupPayment,
            List<CreateGroupPaymentRequest.ParticipantRequest> participantRequests) {

        BigDecimal totalAmount = groupPayment.getTotalAmount();
        int participantCount = participantRequests.size();
        BigDecimal splitAmount = totalAmount.divide(BigDecimal.valueOf(participantCount), 2, RoundingMode.HALF_UP);

        // Handle remainder by adding cents to first few participants
        BigDecimal remainder = totalAmount.subtract(splitAmount.multiply(BigDecimal.valueOf(participantCount)));
        BigDecimal centsToAdd = remainder.multiply(BigDecimal.valueOf(100));

        return participantRequests.stream()
            .map(request -> {
                BigDecimal owedAmount = splitAmount;
                
                // Add one cent if there's remainder to distribute
                if (centsToAdd.compareTo(BigDecimal.ZERO) > 0) {
                    owedAmount = owedAmount.add(BigDecimal.valueOf(0.01));
                    centsToAdd = centsToAdd.subtract(BigDecimal.ONE);
                }

                return createParticipant(groupPayment, request, owedAmount);
            })
            .collect(Collectors.toList());
    }

    private List<GroupPaymentParticipant> calculateCustomAmountSplit(
            GroupPayment groupPayment,
            List<CreateGroupPaymentRequest.ParticipantRequest> participantRequests) {

        // Validate that custom amounts sum to total
        BigDecimal customAmountSum = participantRequests.stream()
            .map(CreateGroupPaymentRequest.ParticipantRequest::getOwedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (customAmountSum.compareTo(groupPayment.getTotalAmount()) != 0) {
            throw new IllegalArgumentException(
                String.format("Custom amounts (%s) do not sum to total amount (%s)", 
                    customAmountSum, groupPayment.getTotalAmount()));
        }

        return participantRequests.stream()
            .map(request -> createParticipant(groupPayment, request, request.getOwedAmount()))
            .collect(Collectors.toList());
    }

    private List<GroupPaymentParticipant> calculatePercentageSplit(
            GroupPayment groupPayment,
            List<CreateGroupPaymentRequest.ParticipantRequest> participantRequests) {

        // For percentage split, owedAmount in request represents percentage
        BigDecimal totalPercentage = participantRequests.stream()
            .map(CreateGroupPaymentRequest.ParticipantRequest::getOwedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPercentage.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new IllegalArgumentException(
                String.format("Percentages must sum to 100, but sum to %s", totalPercentage));
        }

        return participantRequests.stream()
            .map(request -> {
                BigDecimal percentage = request.getOwedAmount();
                BigDecimal owedAmount = groupPayment.getTotalAmount()
                    .multiply(percentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                
                return createParticipant(groupPayment, request, owedAmount);
            })
            .collect(Collectors.toList());
    }

    private List<GroupPaymentParticipant> calculateItemBasedSplit(
            GroupPayment groupPayment,
            List<CreateGroupPaymentRequest.ParticipantRequest> participantRequests,
            List<CreateGroupPaymentRequest.ItemRequest> itemRequests) {

        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new IllegalArgumentException("Item-based split requires items to be specified");
        }

        // Calculate each participant's share based on items they're associated with
        return participantRequests.stream()
            .map(participantRequest -> {
                BigDecimal owedAmount = BigDecimal.ZERO;

                // Sum up all items this participant is involved with
                for (CreateGroupPaymentRequest.ItemRequest item : itemRequests) {
                    if (item.getParticipants() != null) {
                        for (CreateGroupPaymentRequest.ItemParticipantRequest itemParticipant : item.getParticipants()) {
                            if (itemParticipant.getUserId().equals(participantRequest.getUserIdOrEmail())) {
                                BigDecimal itemTotal = item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity()));
                                
                                if (itemParticipant.getShareType() == CreateGroupPaymentRequest.ItemParticipantRequest.ShareType.AMOUNT) {
                                    owedAmount = owedAmount.add(itemParticipant.getShare());
                                } else if (itemParticipant.getShareType() == CreateGroupPaymentRequest.ItemParticipantRequest.ShareType.PERCENTAGE) {
                                    BigDecimal shareAmount = itemTotal
                                        .multiply(itemParticipant.getShare())
                                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                                    owedAmount = owedAmount.add(shareAmount);
                                }
                            }
                        }
                    }
                }

                return createParticipant(groupPayment, participantRequest, owedAmount);
            })
            .collect(Collectors.toList());
    }

    private List<GroupPaymentParticipant> calculateWeightBasedSplit(
            GroupPayment groupPayment,
            List<CreateGroupPaymentRequest.ParticipantRequest> participantRequests) {

        // For weight-based split, owedAmount in request represents weight
        BigDecimal totalWeight = participantRequests.stream()
            .map(CreateGroupPaymentRequest.ParticipantRequest::getOwedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total weight must be greater than zero");
        }

        return participantRequests.stream()
            .map(request -> {
                BigDecimal weight = request.getOwedAmount();
                BigDecimal owedAmount = groupPayment.getTotalAmount()
                    .multiply(weight)
                    .divide(totalWeight, 2, RoundingMode.HALF_UP);
                
                return createParticipant(groupPayment, request, owedAmount);
            })
            .collect(Collectors.toList());
    }

    private GroupPaymentParticipant createParticipant(
            GroupPayment groupPayment,
            CreateGroupPaymentRequest.ParticipantRequest request,
            BigDecimal owedAmount) {

        return GroupPaymentParticipant.builder()
            .groupPayment(groupPayment)
            .userId(request.getUserIdOrEmail())
            .email(request.getEmail() != null ? request.getEmail() : request.getUserIdOrEmail())
            .displayName(request.getDisplayName())
            .owedAmount(owedAmount)
            .paidAmount(BigDecimal.ZERO)
            .status(GroupPaymentParticipant.ParticipantStatus.INVITED)
            .remindersSent(0)
            .invitedAt(Instant.now())
            .build();
    }
}