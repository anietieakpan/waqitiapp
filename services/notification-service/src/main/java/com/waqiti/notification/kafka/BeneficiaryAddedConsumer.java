package com.waqiti.notification.kafka;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.notification.domain.NotificationPriority;
import com.waqiti.notification.domain.NotificationType;
import com.waqiti.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #28: BeneficiaryAddedConsumer
 * Notifies users when beneficiaries are added to their accounts
 * Impact: Estate planning transparency, fraud prevention
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BeneficiaryAddedConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "account.beneficiary.added", groupId = "notification-beneficiary-added")
    public void handle(BeneficiaryAddedEvent event, Acknowledgment ack) {
        try {
            log.info("👤 BENEFICIARY ADDED: userId={}, beneficiaryName={}, allocation={}%",
                event.getUserId(), event.getBeneficiaryName(), event.getAllocationPercentage());

            String key = "beneficiary:added:" + event.getBeneficiaryId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                Beneficiary Added to Your Account

                You've successfully added a beneficiary to your account.

                Beneficiary Details:
                - Name: %s
                - Relationship: %s
                - Date of Birth: %s
                - Allocation: %s%%
                - Beneficiary Type: %s
                - Tax ID/SSN: %s

                Account Information:
                - Account: %s
                - Account Type: %s
                - Added Date: %s

                What This Means:
                In the event of your passing, this beneficiary will receive %s%% of the
                funds in your %s account.

                %s

                Important Information:
                • Beneficiary designations override your will
                • Keep beneficiary information up to date
                • Review beneficiaries annually or after major life events
                • You can update or remove beneficiaries at any time

                Total Allocations:
                %s

                Next Steps:
                1. Review your beneficiary designations: https://example.com/account/beneficiaries
                2. Ensure contact information is accurate
                3. Notify your beneficiary (optional but recommended)
                4. Keep beneficiary information secure

                Questions? Contact estate planning support:
                Email: beneficiaries@example.com
                Phone: 1-800-WAQITI-ESTATE
                Reference: Beneficiary ID %s
                """,
                event.getBeneficiaryName(),
                event.getRelationship(),
                event.getDateOfBirth() != null ? event.getDateOfBirth().toLocalDate() : "Not provided",
                event.getAllocationPercentage(),
                event.getBeneficiaryType(),
                maskTaxId(event.getTaxId()),
                event.getAccountNumber(),
                event.getAccountType(),
                event.getAddedAt(),
                event.getAllocationPercentage(),
                event.getAccountType(),
                getContingentBeneficiaryNote(event.getBeneficiaryType()),
                getAllocationSummary(event.getTotalPrimaryAllocation(), event.getTotalContingentAllocation()),
                event.getBeneficiaryId());

            notificationService.sendNotification(event.getUserId(), NotificationType.BENEFICIARY_ADDED,
                NotificationChannel.EMAIL, NotificationPriority.MEDIUM,
                "Beneficiary Added to Your Account", message, Map.of());

            // Security alert via push
            notificationService.sendNotification(event.getUserId(), NotificationType.BENEFICIARY_ADDED,
                NotificationChannel.PUSH, NotificationPriority.MEDIUM,
                "Beneficiary Added",
                String.format("You added %s as a beneficiary (%s%% allocation). Review at any time in your account settings.",
                    event.getBeneficiaryName(), event.getAllocationPercentage()), Map.of());

            metricsCollector.incrementCounter("notification.beneficiary.added.sent");
            metricsCollector.incrementCounter("notification.beneficiary.added." +
                event.getBeneficiaryType().toLowerCase());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process beneficiary added event", e);
            dlqHandler.sendToDLQ("account.beneficiary.added", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String getContingentBeneficiaryNote(String beneficiaryType) {
        if ("CONTINGENT".equalsIgnoreCase(beneficiaryType)) {
            return """

                Note: This is a contingent beneficiary. They will only receive funds if all
                primary beneficiaries are unable to receive the assets (e.g., they predecease you).
                """;
        }
        return "";
    }

    private String getAllocationSummary(BigDecimal primaryTotal, BigDecimal contingentTotal) {
        StringBuilder summary = new StringBuilder();
        summary.append("- Primary Beneficiaries: ").append(primaryTotal).append("%\n");
        summary.append("- Contingent Beneficiaries: ").append(contingentTotal).append("%");

        if (primaryTotal.compareTo(new BigDecimal("100")) != 0) {
            summary.append("\n\n⚠️ WARNING: Primary beneficiary allocations do not total 100%.");
            summary.append("\nPlease add or adjust beneficiaries to reach 100% allocation.");
        }

        return summary.toString();
    }

    private String maskTaxId(String taxId) {
        if (taxId == null || taxId.length() < 4) return "***-**-****";
        return "***-**-" + taxId.substring(taxId.length() - 4);
    }

    private static class BeneficiaryAddedEvent {
        private UUID userId, beneficiaryId, accountId;
        private String beneficiaryName, relationship, beneficiaryType;
        private String accountType, accountNumber, taxId;
        private BigDecimal allocationPercentage, totalPrimaryAllocation, totalContingentAllocation;
        private LocalDateTime dateOfBirth, addedAt;

        public UUID getUserId() { return userId; }
        public UUID getBeneficiaryId() { return beneficiaryId; }
        public UUID getAccountId() { return accountId; }
        public String getBeneficiaryName() { return beneficiaryName; }
        public String getRelationship() { return relationship; }
        public String getBeneficiaryType() { return beneficiaryType; }
        public String getAccountType() { return accountType; }
        public String getAccountNumber() { return accountNumber; }
        public String getTaxId() { return taxId; }
        public BigDecimal getAllocationPercentage() { return allocationPercentage; }
        public BigDecimal getTotalPrimaryAllocation() { return totalPrimaryAllocation; }
        public BigDecimal getTotalContingentAllocation() { return totalContingentAllocation; }
        public LocalDateTime getDateOfBirth() { return dateOfBirth; }
        public LocalDateTime getAddedAt() { return addedAt; }
    }
}
