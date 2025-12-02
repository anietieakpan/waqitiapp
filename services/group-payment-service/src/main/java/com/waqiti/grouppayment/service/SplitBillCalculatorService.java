package com.waqiti.grouppayment.service;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.grouppayment.domain.SplitBillCalculation;
import com.waqiti.grouppayment.domain.SplitMethod;
import com.waqiti.grouppayment.dto.*;
import com.waqiti.grouppayment.repository.SplitBillCalculationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for sophisticated split bill calculations
 * Supports multiple splitting methods with tax, tip, and discount handling
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SplitBillCalculatorService {
    
    private final SplitBillCalculationRepository splitBillCalculationRepository;
    
    @Value("${waqiti.frontend.base-url:https://app.example.com}")
    private String frontendBaseUrl;
    
    private static final int CURRENCY_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    /**
     * Calculate split bill with comprehensive options
     */
    @Transactional
    public SplitBillCalculationResponse calculateSplitBill(SplitBillCalculationRequest request) {
        log.info("Calculating split bill for {} participants using method: {}", 
                request.getParticipants().size(), request.getSplitMethod());
        
        // Validate request
        validateSplitBillRequest(request);
        
        // Perform calculation based on split method
        SplitBillResult result = switch (request.getSplitMethod()) {
            case EQUAL -> calculateEqualSplit(request);
            case BY_PERCENTAGE -> calculatePercentageSplit(request);
            case BY_AMOUNT -> calculateAmountSplit(request);
            case BY_ITEM -> calculateItemSplit(request);
            case BY_WEIGHT -> calculateWeightSplit(request);
            case CUSTOM -> calculateCustomSplit(request);
        };
        
        // Apply adjustments for rounding discrepancies
        result = applyRoundingAdjustments(result, request.getTotalAmount());
        
        // Save calculation for audit and future reference
        SplitBillCalculation calculation = saveCalculation(request, result);
        
        return SplitBillCalculationResponse.builder()
                .calculationId(calculation.getId())
                .totalAmount(request.getTotalAmount())
                .currency(request.getCurrency())
                .splitMethod(request.getSplitMethod())
                .participantSplits(result.getParticipantSplits())
                .adjustments(result.getAdjustments())
                .summary(calculateSummary(result))
                .qrCode(generateGroupQRCode(calculation))
                .shareableLink(generateShareableLink(calculation))
                .createdAt(calculation.getCreatedAt())
                .expiresAt(calculation.getExpiresAt())
                .build();
    }
    
    /**
     * Calculate equal split among all participants
     */
    private SplitBillResult calculateEqualSplit(SplitBillCalculationRequest request) {
        List<ParticipantSplit> splits = new ArrayList<>();
        List<SplitAdjustment> adjustments = new ArrayList<>();
        
        BigDecimal totalAmount = calculateTotalWithTaxAndTip(request);
        if (request.getParticipants() == null || request.getParticipants().isEmpty()) {
            throw new IllegalArgumentException("Participants list cannot be null or empty");
        }
        int participantCount = request.getParticipants().size();
        
        // Calculate base amount per person
        BigDecimal baseAmountPerPerson = totalAmount.divide(
            BigDecimal.valueOf(participantCount), 
            CURRENCY_SCALE + 2, 
            ROUNDING_MODE
        );
        
        // Round to currency precision
        BigDecimal roundedAmountPerPerson = baseAmountPerPerson.setScale(CURRENCY_SCALE, ROUNDING_MODE);
        
        // Calculate splits for each participant
        BigDecimal totalAllocated = BigDecimal.ZERO;
        
        for (int i = 0; i < request.getParticipants().size(); i++) {
            SplitParticipant participant = request.getParticipants().get(i);
            BigDecimal participantAmount = roundedAmountPerPerson;
            
            // Apply participant-specific adjustments
            if (participant.getDiscountAmount() != null) {
                participantAmount = participantAmount.subtract(participant.getDiscountAmount());
                adjustments.add(SplitAdjustment.builder()
                    .participantId(participant.getParticipantId())
                    .type(SplitAdjustment.AdjustmentType.DISCOUNT)
                    .amount(participant.getDiscountAmount())
                    .description("Personal discount")
                    .build());
            }
            
            if (participant.getAdditionalAmount() != null) {
                participantAmount = participantAmount.add(participant.getAdditionalAmount());
                adjustments.add(SplitAdjustment.builder()
                    .participantId(participant.getParticipantId())
                    .type(SplitAdjustment.AdjustmentType.ADDITIONAL)
                    .amount(participant.getAdditionalAmount())
                    .description("Additional amount")
                    .build());
            }
            
            splits.add(ParticipantSplit.builder()
                .participantId(participant.getParticipantId())
                .participantName(participant.getParticipantName())
                .amount(participantAmount)
                .percentage(participantAmount.divide(totalAmount, 4, ROUNDING_MODE).multiply(BigDecimal.valueOf(100)))
                .items(Collections.emptyList())
                .taxes(calculateParticipantTax(participantAmount, request))
                .tips(calculateParticipantTip(participantAmount, request))
                .build());
            
            totalAllocated = totalAllocated.add(participantAmount);
        }
        
        return SplitBillResult.builder()
            .participantSplits(splits)
            .adjustments(adjustments)
            .totalAllocated(totalAllocated)
            .build();
    }
    
    /**
     * Calculate split by percentage
     */
    private SplitBillResult calculatePercentageSplit(SplitBillCalculationRequest request) {
        List<ParticipantSplit> splits = new ArrayList<>();
        List<SplitAdjustment> adjustments = new ArrayList<>();
        
        BigDecimal totalAmount = calculateTotalWithTaxAndTip(request);
        BigDecimal totalPercentage = request.getParticipants().stream()
            .map(p -> p.getPercentage() != null ? p.getPercentage() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Validate percentages add up to 100%
        if (totalPercentage.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new ValidationException("Percentages must add up to 100%");
        }
        
        BigDecimal totalAllocated = BigDecimal.ZERO;
        
        for (SplitParticipant participant : request.getParticipants()) {
            BigDecimal percentage = participant.getPercentage();
            BigDecimal participantAmount = totalAmount
                .multiply(percentage)
                .divide(BigDecimal.valueOf(100), CURRENCY_SCALE, ROUNDING_MODE);
            
            splits.add(ParticipantSplit.builder()
                .participantId(participant.getParticipantId())
                .participantName(participant.getParticipantName())
                .amount(participantAmount)
                .percentage(percentage)
                .items(Collections.emptyList())
                .taxes(calculateParticipantTax(participantAmount, request))
                .tips(calculateParticipantTip(participantAmount, request))
                .build());
            
            totalAllocated = totalAllocated.add(participantAmount);
        }
        
        return SplitBillResult.builder()
            .participantSplits(splits)
            .adjustments(adjustments)
            .totalAllocated(totalAllocated)
            .build();
    }
    
    /**
     * Calculate split by specific amounts
     */
    private SplitBillResult calculateAmountSplit(SplitBillCalculationRequest request) {
        List<ParticipantSplit> splits = new ArrayList<>();
        List<SplitAdjustment> adjustments = new ArrayList<>();
        
        BigDecimal totalAmount = calculateTotalWithTaxAndTip(request);
        BigDecimal totalAllocated = BigDecimal.ZERO;
        
        for (SplitParticipant participant : request.getParticipants()) {
            BigDecimal participantAmount = participant.getAmount();
            if (participantAmount == null) {
                throw new ValidationException("Amount must be specified for amount-based split");
            }
            
            BigDecimal percentage = participantAmount.divide(totalAmount, 4, ROUNDING_MODE)
                .multiply(BigDecimal.valueOf(100));
            
            splits.add(ParticipantSplit.builder()
                .participantId(participant.getParticipantId())
                .participantName(participant.getParticipantName())
                .amount(participantAmount)
                .percentage(percentage)
                .items(Collections.emptyList())
                .taxes(calculateParticipantTax(participantAmount, request))
                .tips(calculateParticipantTip(participantAmount, request))
                .build());
            
            totalAllocated = totalAllocated.add(participantAmount);
        }
        
        // Validate total amounts match
        if (totalAllocated.compareTo(totalAmount) != 0) {
            throw new ValidationException("Sum of participant amounts must equal total amount");
        }
        
        return SplitBillResult.builder()
            .participantSplits(splits)
            .adjustments(adjustments)
            .totalAllocated(totalAllocated)
            .build();
    }
    
    /**
     * Calculate split by individual items
     */
    private SplitBillResult calculateItemSplit(SplitBillCalculationRequest request) {
        List<ParticipantSplit> splits = new ArrayList<>();
        List<SplitAdjustment> adjustments = new ArrayList<>();
        
        // Create map of participant to their items
        Map<String, List<BillItem>> participantItems = new HashMap<>();
        Map<String, BigDecimal> participantTotals = new HashMap<>();
        
        // Initialize participant maps
        request.getParticipants().forEach(p -> {
            participantItems.put(p.getParticipantId(), new ArrayList<>());
            participantTotals.put(p.getParticipantId(), BigDecimal.ZERO);
        });
        
        // Process each bill item
        for (BillItem item : request.getItems()) {
            if (item.getSharedBy() != null && !item.getSharedBy().isEmpty()) {
                // Item is shared among specific participants
                BigDecimal itemCostPerPerson = item.getAmount()
                    .divide(BigDecimal.valueOf(item.getSharedBy().size()), CURRENCY_SCALE, ROUNDING_MODE);
                
                for (String participantId : item.getSharedBy()) {
                    participantItems.get(participantId).add(BillItem.builder()
                        .name(item.getName())
                        .amount(itemCostPerPerson)
                        .quantity(BigDecimal.ONE)
                        .sharedBy(item.getSharedBy())
                        .build());
                    
                    participantTotals.merge(participantId, itemCostPerPerson, BigDecimal::add);
                }
            } else {
                // Item assigned to specific participant
                String assignedTo = item.getAssignedTo();
                if (assignedTo != null) {
                    participantItems.get(assignedTo).add(item);
                    participantTotals.merge(assignedTo, item.getAmount(), BigDecimal::add);
                }
            }
        }
        
        // Apply taxes and tips proportionally
        BigDecimal subtotal = request.getItems().stream()
            .map(BillItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalWithExtras = calculateTotalWithTaxAndTip(request);
        BigDecimal totalAllocated = BigDecimal.ZERO;
        
        for (SplitParticipant participant : request.getParticipants()) {
            BigDecimal participantSubtotal = participantTotals.get(participant.getParticipantId());
            BigDecimal proportion = participantSubtotal.divide(subtotal, 4, ROUNDING_MODE);
            
            // Apply proportional tax and tip
            BigDecimal participantTax = request.getTaxAmount() != null ? 
                request.getTaxAmount().multiply(proportion).setScale(CURRENCY_SCALE, ROUNDING_MODE) : 
                BigDecimal.ZERO;
            
            BigDecimal participantTip = request.getTipAmount() != null ? 
                request.getTipAmount().multiply(proportion).setScale(CURRENCY_SCALE, ROUNDING_MODE) : 
                BigDecimal.ZERO;
            
            BigDecimal totalParticipantAmount = participantSubtotal.add(participantTax).add(participantTip);
            
            splits.add(ParticipantSplit.builder()
                .participantId(participant.getParticipantId())
                .participantName(participant.getParticipantName())
                .amount(totalParticipantAmount)
                .percentage(totalParticipantAmount.divide(totalWithExtras, 4, ROUNDING_MODE)
                    .multiply(BigDecimal.valueOf(100)))
                .items(participantItems.get(participant.getParticipantId()))
                .taxes(participantTax)
                .tips(participantTip)
                .build());
            
            totalAllocated = totalAllocated.add(totalParticipantAmount);
        }
        
        return SplitBillResult.builder()
            .participantSplits(splits)
            .adjustments(adjustments)
            .totalAllocated(totalAllocated)
            .build();
    }
    
    /**
     * Calculate split by weight/factor
     */
    private SplitBillResult calculateWeightSplit(SplitBillCalculationRequest request) {
        List<ParticipantSplit> splits = new ArrayList<>();
        List<SplitAdjustment> adjustments = new ArrayList<>();
        
        BigDecimal totalAmount = calculateTotalWithTaxAndTip(request);
        BigDecimal totalWeight = request.getParticipants().stream()
            .map(p -> p.getWeight() != null ? p.getWeight() : BigDecimal.ONE)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalAllocated = BigDecimal.ZERO;
        
        for (SplitParticipant participant : request.getParticipants()) {
            BigDecimal weight = participant.getWeight() != null ? participant.getWeight() : BigDecimal.ONE;
            BigDecimal proportion = weight.divide(totalWeight, 4, ROUNDING_MODE);
            BigDecimal participantAmount = totalAmount.multiply(proportion)
                .setScale(CURRENCY_SCALE, ROUNDING_MODE);
            
            splits.add(ParticipantSplit.builder()
                .participantId(participant.getParticipantId())
                .participantName(participant.getParticipantName())
                .amount(participantAmount)
                .percentage(proportion.multiply(BigDecimal.valueOf(100)))
                .weight(weight)
                .items(Collections.emptyList())
                .taxes(calculateParticipantTax(participantAmount, request))
                .tips(calculateParticipantTip(participantAmount, request))
                .build());
            
            totalAllocated = totalAllocated.add(participantAmount);
        }
        
        return SplitBillResult.builder()
            .participantSplits(splits)
            .adjustments(adjustments)
            .totalAllocated(totalAllocated)
            .build();
    }
    
    /**
     * Calculate custom split with mixed methods
     */
    private SplitBillResult calculateCustomSplit(SplitBillCalculationRequest request) {
        // For custom split, participants can specify their preferred calculation method
        // This is the most flexible option allowing hybrid approaches
        
        List<ParticipantSplit> splits = new ArrayList<>();
        List<SplitAdjustment> adjustments = new ArrayList<>();
        
        BigDecimal totalAmount = calculateTotalWithTaxAndTip(request);
        BigDecimal allocatedAmount = BigDecimal.ZERO;
        
        // Process fixed amounts first
        for (SplitParticipant participant : request.getParticipants()) {
            if (participant.getAmount() != null) {
                splits.add(ParticipantSplit.builder()
                    .participantId(participant.getParticipantId())
                    .participantName(participant.getParticipantName())
                    .amount(participant.getAmount())
                    .percentage(participant.getAmount().divide(totalAmount, 4, ROUNDING_MODE)
                        .multiply(BigDecimal.valueOf(100)))
                    .build());
                
                allocatedAmount = allocatedAmount.add(participant.getAmount());
            }
        }
        
        // Distribute remaining amount among participants without fixed amounts
        BigDecimal remainingAmount = totalAmount.subtract(allocatedAmount);
        List<SplitParticipant> remainingParticipants = request.getParticipants().stream()
            .filter(p -> p.getAmount() == null)
            .collect(Collectors.toList());
        
        if (!remainingParticipants.isEmpty() && remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amountPerRemaining = remainingAmount.divide(
                BigDecimal.valueOf(remainingParticipants.size()), 
                CURRENCY_SCALE, 
                ROUNDING_MODE
            );
            
            for (SplitParticipant participant : remainingParticipants) {
                splits.add(ParticipantSplit.builder()
                    .participantId(participant.getParticipantId())
                    .participantName(participant.getParticipantName())
                    .amount(amountPerRemaining)
                    .percentage(amountPerRemaining.divide(totalAmount, 4, ROUNDING_MODE)
                        .multiply(BigDecimal.valueOf(100)))
                    .build());
            }
        }
        
        return SplitBillResult.builder()
            .participantSplits(splits)
            .adjustments(adjustments)
            .totalAllocated(totalAmount)
            .build();
    }
    
    // Helper methods
    
    private BigDecimal calculateTotalWithTaxAndTip(SplitBillCalculationRequest request) {
        BigDecimal total = request.getTotalAmount();
        
        if (request.getTaxAmount() != null) {
            total = total.add(request.getTaxAmount());
        }
        
        if (request.getTipAmount() != null) {
            total = total.add(request.getTipAmount());
        }
        
        if (request.getDiscountAmount() != null) {
            total = total.subtract(request.getDiscountAmount());
        }
        
        return total;
    }
    
    private BigDecimal calculateParticipantTax(BigDecimal participantAmount, SplitBillCalculationRequest request) {
        if (request.getTaxAmount() == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalPreTax = request.getTotalAmount();
        BigDecimal proportion = participantAmount.divide(totalPreTax, 4, ROUNDING_MODE);
        return request.getTaxAmount().multiply(proportion).setScale(CURRENCY_SCALE, ROUNDING_MODE);
    }
    
    private BigDecimal calculateParticipantTip(BigDecimal participantAmount, SplitBillCalculationRequest request) {
        if (request.getTipAmount() == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalPreTip = request.getTotalAmount();
        if (request.getTaxAmount() != null) {
            totalPreTip = totalPreTip.add(request.getTaxAmount());
        }
        
        BigDecimal proportion = participantAmount.divide(totalPreTip, 4, ROUNDING_MODE);
        return request.getTipAmount().multiply(proportion).setScale(CURRENCY_SCALE, ROUNDING_MODE);
    }
    
    private SplitBillResult applyRoundingAdjustments(SplitBillResult result, BigDecimal expectedTotal) {
        BigDecimal actualTotal = result.getParticipantSplits().stream()
            .map(ParticipantSplit::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal difference = expectedTotal.subtract(actualTotal);
        
        if (difference.abs().compareTo(BigDecimal.valueOf(0.01)) > 0) {
            // Significant difference, add adjustment to participant with highest amount
            ParticipantSplit largest = result.getParticipantSplits().stream()
                .max(Comparator.comparing(ParticipantSplit::getAmount))
                .orElseThrow();
            
            largest.setAmount(largest.getAmount().add(difference));
            
            result.getAdjustments().add(SplitAdjustment.builder()
                .participantId(largest.getParticipantId())
                .type(SplitAdjustment.AdjustmentType.ROUNDING)
                .amount(difference)
                .description("Rounding adjustment")
                .build());
        }
        
        return result;
    }
    
    private void validateSplitBillRequest(SplitBillCalculationRequest request) {
        if (request.getParticipants() == null || request.getParticipants().isEmpty()) {
            throw new ValidationException("At least one participant is required");
        }
        
        if (request.getTotalAmount() == null || request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Total amount must be positive");
        }
        
        // Validate participant IDs are unique
        Set<String> participantIds = request.getParticipants().stream()
            .map(SplitParticipant::getParticipantId)
            .collect(Collectors.toSet());
        
        if (participantIds.size() != request.getParticipants().size()) {
            throw new ValidationException("Participant IDs must be unique");
        }
        
        // Method-specific validations
        switch (request.getSplitMethod()) {
            case BY_PERCENTAGE -> validatePercentageSplit(request);
            case BY_AMOUNT -> validateAmountSplit(request);
            case BY_ITEM -> validateItemSplit(request);
        }
    }
    
    private void validatePercentageSplit(SplitBillCalculationRequest request) {
        for (SplitParticipant participant : request.getParticipants()) {
            if (participant.getPercentage() == null) {
                throw new ValidationException("Percentage must be specified for percentage-based split");
            }
            if (participant.getPercentage().compareTo(BigDecimal.ZERO) <= 0 || 
                participant.getPercentage().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ValidationException("Percentage must be between 0 and 100");
            }
        }
    }
    
    private void validateAmountSplit(SplitBillCalculationRequest request) {
        for (SplitParticipant participant : request.getParticipants()) {
            if (participant.getAmount() == null) {
                throw new ValidationException("Amount must be specified for amount-based split");
            }
            if (participant.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Participant amount must be positive");
            }
        }
    }
    
    private void validateItemSplit(SplitBillCalculationRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ValidationException("Items must be specified for item-based split");
        }
        
        // Validate all items are assigned or shared
        for (BillItem item : request.getItems()) {
            if (item.getAssignedTo() == null && (item.getSharedBy() == null || item.getSharedBy().isEmpty())) {
                throw new ValidationException("Each item must be assigned to a participant or shared");
            }
        }
    }
    
    private SplitBillCalculation saveCalculation(SplitBillCalculationRequest request, SplitBillResult result) {
        SplitBillCalculation calculation = SplitBillCalculation.builder()
            .organizerId(request.getOrganizerId())
            .groupName(request.getGroupName())
            .totalAmount(request.getTotalAmount())
            .currency(request.getCurrency())
            .splitMethod(request.getSplitMethod())
            .participantSplits(result.getParticipantSplits())
            .adjustments(result.getAdjustments())
            .status(SplitBillCalculation.Status.CALCULATED)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(7)) // 7 days to complete payment
            .build();
        
        return splitBillCalculationRepository.save(calculation);
    }
    
    private SplitBillSummary calculateSummary(SplitBillResult result) {
        return SplitBillSummary.builder()
            .totalParticipants(result.getParticipantSplits().size())
            .averageAmount(result.getParticipantSplits().stream()
                .map(ParticipantSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(result.getParticipantSplits().size()), CURRENCY_SCALE, ROUNDING_MODE))
            .highestAmount(result.getParticipantSplits().stream()
                .map(ParticipantSplit::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO))
            .lowestAmount(result.getParticipantSplits().stream()
                .map(ParticipantSplit::getAmount)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO))
            .totalAdjustments(result.getAdjustments().size())
            .build();
    }
    
    private String generateGroupQRCode(SplitBillCalculation calculation) {
        // Generate QR code for group payment
        return "QR_GROUP_" + calculation.getId();
    }
    
    private String generateShareableLink(SplitBillCalculation calculation) {
        // Generate shareable link for the split bill
        return frontendBaseUrl + "/split-bill/" + calculation.getId();
    }
}