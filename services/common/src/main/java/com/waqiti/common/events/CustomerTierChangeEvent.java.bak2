package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted when a customer's tier/segment changes.
 *
 * This event is published when:
 * - Customer upgrades to premium tier
 * - Customer downgraded due to inactivity
 * - Automatic tier change based on behavior
 * - Manual tier adjustment by admin
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomerTierChangeEvent extends FinancialEvent {

    private UUID customerId;
    private String previousTier;
    private String newTier;
    private String changeReason;
    private String changeType;  // UPGRADE, DOWNGRADE, LATERAL
    private Instant effectiveDate;
    private UUID changedBy;
    private boolean isAutomatic;
    private BigDecimal previousMonthlyFee;
    private BigDecimal newMonthlyFee;
    private Map<String, Object> tierBenefits;
    private Map<String, Object> tierLimits;
    private String notificationStatus;

    /**
     * Factory method to create tier change event
     */
    public static CustomerTierChangeEvent create(UUID customerId, UUID userId,
                                                  String previousTier, String newTier,
                                                  String reason) {
        return CustomerTierChangeEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("CUSTOMER_TIER_CHANGE")
            .eventCategory("CUSTOMER")
            .customerId(customerId)
            .userId(userId)
            .previousTier(previousTier)
            .newTier(newTier)
            .changeReason(reason)
            .changeType(determineChangeType(previousTier, newTier))
            .effectiveDate(Instant.now())
            .timestamp(Instant.now())
            .aggregateType("Customer")
            .aggregateId(customerId)
            .build();
    }

    /**
     * Determine if change is upgrade, downgrade, or lateral
     */
    private static String determineChangeType(String previousTier, String newTier) {
        if (previousTier == null || newTier == null) {
            return "UNKNOWN";
        }

        // Define tier hierarchy
        String[] tiers = {"BASIC", "STANDARD", "PREMIUM", "VIP", "PLATINUM"};
        int previousIndex = getTierIndex(previousTier, tiers);
        int newIndex = getTierIndex(newTier, tiers);

        if (newIndex > previousIndex) {
            return "UPGRADE";
        } else if (newIndex < previousIndex) {
            return "DOWNGRADE";
        } else {
            return "LATERAL";
        }
    }

    /**
     * Get tier index in hierarchy
     */
    private static int getTierIndex(String tier, String[] tiers) {
        for (int i = 0; i < tiers.length; i++) {
            if (tiers[i].equalsIgnoreCase(tier)) {
                return i;
            }
        }
        return 0; // Default to lowest tier
    }

    /**
     * Check if this is an upgrade
     */
    public boolean isUpgrade() {
        return "UPGRADE".equalsIgnoreCase(changeType);
    }

    /**
     * Check if this is a downgrade
     */
    public boolean isDowngrade() {
        return "DOWNGRADE".equalsIgnoreCase(changeType);
    }

    /**
     * Check if fee changed
     */
    public boolean feeChanged() {
        if (previousMonthlyFee == null || newMonthlyFee == null) {
            return false;
        }
        return previousMonthlyFee.compareTo(newMonthlyFee) != 0;
    }
}
