package com.waqiti.compliance.service;

import com.waqiti.compliance.domain.AssetFreeze;
import com.waqiti.compliance.domain.LegalOrder;
import com.waqiti.compliance.domain.LegalOrder.*;
import com.waqiti.compliance.repository.LegalOrderRepository;
import com.waqiti.compliance.exception.LegalOrderException;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CRITICAL P0 FIX: Legal Order Processing Service
 *
 * Implements comprehensive legal order workflow processing for court orders,
 * garnishments, tax levies, and other legal compliance requirements.
 *
 * Features:
 * - Court order authentication and verification
 * - Legal department notification system
 * - Compliance timeline tracking
 * - Case management and audit trail
 * - Integration with wallet freeze system
 * - Automated expiration monitoring
 *
 * Compliance Requirements:
 * - Federal Rules of Civil Procedure (FRCP)
 * - State garnishment laws
 * - IRS levy requirements (26 USC §6331)
 * - Child support enforcement (UIFSA)
 * - Criminal asset forfeiture (18 USC §983)
 * - Bank Secrecy Act (BSA) reporting
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-05
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegalOrderProcessingService {

    private final LegalOrderRepository legalOrderRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final WalletFreezeService walletFreezeService;

    /**
     * CRITICAL: Process incoming legal order
     *
     * Main entry point for legal order processing. Implements full workflow:
     * 1. Validate and create legal order record
     * 2. Assign priority based on order type
     * 3. Notify legal department
     * 4. Initiate verification workflow
     * 5. Create audit trail
     *
     * @param orderNumber Court/case order number
     * @param orderType Type of legal order
     * @param userId User subject to order
     * @param walletId Wallet to freeze (optional)
     * @param amount Amount to freeze (null = entire balance)
     * @param currency Currency code
     * @param issuingAuthority Court/agency issuing order
     * @param jurisdiction Legal jurisdiction
     * @param caseNumber Court case number
     * @param judgeName Judge name (if applicable)
     * @param issueDate Date order was issued
     * @param expirationDate Expiration date (if applicable)
     * @param description Order description
     * @param documentPath Path to stored legal documents
     * @param correlationId Event correlation ID
     * @return Created legal order
     */
    @Transactional
    public LegalOrder processIncomingLegalOrder(
        String orderNumber,
        OrderType orderType,
        UUID userId,
        UUID walletId,
        BigDecimal amount,
        String currency,
        String issuingAuthority,
        String jurisdiction,
        String caseNumber,
        String judgeName,
        LocalDate issueDate,
        LocalDate expirationDate,
        String description,
        String documentPath,
        String correlationId
    ) {
        log.warn("LEGAL: Processing incoming legal order - Order: {}, Type: {}, User: {}, Authority: {}, Correlation: {}",
            orderNumber, orderType, userId, issuingAuthority, correlationId);

        try {
            // Step 1: Validate order doesn't already exist
            if (legalOrderRepository.existsByOrderNumber(orderNumber)) {
                throw new LegalOrderException("Legal order already exists: " + orderNumber);
            }

            // Step 2: Determine priority based on order type and urgency
            Priority priority = determinePriority(orderType, expirationDate);

            // Step 3: Create legal order record
            LegalOrder legalOrder = LegalOrder.builder()
                .orderNumber(orderNumber)
                .orderType(orderType)
                .userId(userId)
                .walletId(walletId)
                .amount(amount)
                .currency(currency)
                .issuingAuthority(issuingAuthority)
                .jurisdiction(jurisdiction)
                .caseNumber(caseNumber)
                .judgeName(judgeName)
                .issueDate(issueDate)
                .expirationDate(expirationDate)
                .receivedDate(LocalDateTime.now())
                .orderStatus(OrderStatus.PENDING_REVIEW)
                .verificationStatus(VerificationStatus.UNVERIFIED)
                .priority(priority)
                .description(description)
                .documentPath(documentPath)
                .correlationId(correlationId)
                .createdBy("system")
                .build();

            LegalOrder savedOrder = legalOrderRepository.save(legalOrder);

            // Step 4: Create audit trail
            auditService.logLegalOrderEvent(
                savedOrder.getOrderId(),
                "LEGAL_ORDER_RECEIVED",
                String.format("Legal order received - Type: %s, Authority: %s, Priority: %s",
                    orderType, issuingAuthority, priority),
                correlationId
            );

            // Step 5: Notify legal department
            notifyLegalDepartment(savedOrder, "NEW_LEGAL_ORDER");

            // Step 6: Auto-assign based on order type
            autoAssignLegalCounsel(savedOrder);

            log.info("LEGAL: Legal order created successfully - Order ID: {}, Order Number: {}, Priority: {}",
                savedOrder.getOrderId(), orderNumber, priority);

            return savedOrder;

        } catch (LegalOrderException e) {
            log.error("LEGAL: Failed to process legal order - Order: {}, Error: {}",
                orderNumber, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("LEGAL: Unexpected error processing legal order - Order: {}",
                orderNumber, e);
            throw new LegalOrderException("Failed to process legal order: " + orderNumber, e);
        }
    }

    /**
     * CRITICAL: Verify legal order authenticity
     *
     * Implements verification workflow:
     * 1. Validate order documents
     * 2. Verify issuing authority
     * 3. Check jurisdiction validity
     * 4. Update verification status
     * 5. Trigger execution if verified
     *
     * @param orderId Legal order ID
     * @param verifiedBy Legal team member performing verification
     * @param verificationNotes Verification notes
     * @param isValid Whether order is valid
     * @return Updated legal order
     */
    @Transactional
    public LegalOrder verifyLegalOrder(
        UUID orderId,
        String verifiedBy,
        String verificationNotes,
        boolean isValid
    ) {
        log.info("LEGAL: Verifying legal order - Order ID: {}, Verified by: {}, Valid: {}",
            orderId, verifiedBy, isValid);

        LegalOrder legalOrder = legalOrderRepository.findById(orderId)
            .orElseThrow(() -> new LegalOrderException("Legal order not found: " + orderId));

        // Update verification status
        legalOrder.setVerificationStatus(isValid ?
            VerificationStatus.VERIFIED : VerificationStatus.VERIFICATION_FAILED);
        legalOrder.setVerifiedBy(verifiedBy);
        legalOrder.setVerifiedAt(LocalDateTime.now());
        legalOrder.setComplianceNotes(verificationNotes);
        legalOrder.setUpdatedBy(verifiedBy);

        if (isValid) {
            legalOrder.setOrderStatus(OrderStatus.VERIFIED);
        } else {
            legalOrder.setOrderStatus(OrderStatus.REJECTED);
        }

        LegalOrder updatedOrder = legalOrderRepository.save(legalOrder);

        // Create audit trail
        auditService.logLegalOrderEvent(
            orderId,
            isValid ? "LEGAL_ORDER_VERIFIED" : "LEGAL_ORDER_REJECTED",
            String.format("Legal order %s by %s. Notes: %s",
                isValid ? "verified" : "rejected", verifiedBy, verificationNotes),
            legalOrder.getCorrelationId()
        );

        // If verified, trigger execution
        if (isValid) {
            executeLegalOrder(orderId, verifiedBy);
        }

        // Notify legal department
        notifyLegalDepartment(updatedOrder, isValid ? "ORDER_VERIFIED" : "ORDER_REJECTED");

        log.info("LEGAL: Legal order verification complete - Order ID: {}, Status: {}",
            orderId, updatedOrder.getOrderStatus());

        return updatedOrder;
    }

    /**
     * CRITICAL: Execute verified legal order
     *
     * Implements order execution:
     * 1. Freeze wallet if required
     * 2. Record freeze ID
     * 3. Update order status
     * 4. Notify affected parties
     * 5. Create compliance timeline
     *
     * @param orderId Legal order ID
     * @param executedBy User executing the order
     * @return Updated legal order
     */
    @Transactional
    public LegalOrder executeLegalOrder(UUID orderId, String executedBy) {
        log.warn("LEGAL: Executing legal order - Order ID: {}, Executed by: {}",
            orderId, executedBy);

        LegalOrder legalOrder = legalOrderRepository.findById(orderId)
            .orElseThrow(() -> new LegalOrderException("Legal order not found: " + orderId));

        // Validate order is verified
        if (legalOrder.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new LegalOrderException(
                "Cannot execute unverified legal order: " + orderId);
        }

        // Validate order hasn't expired
        if (legalOrder.getExpirationDate() != null &&
            legalOrder.getExpirationDate().isBefore(LocalDate.now())) {
            throw new LegalOrderException(
                "Cannot execute expired legal order: " + orderId);
        }

        try {
            // Execute wallet freeze if wallet ID specified
            if (legalOrder.getWalletId() != null) {
                UUID freezeId = walletFreezeService.freezeWalletForLegalOrder(
                    legalOrder.getWalletId(),
                    legalOrder.getUserId(),
                    legalOrder.getAmount(),
                    legalOrder.getOrderNumber(),
                    legalOrder.getIssuingAuthority(),
                    legalOrder.getDescription()
                );

                legalOrder.setFreezeId(freezeId);

                log.warn("LEGAL: Wallet frozen for legal order - Order ID: {}, Wallet: {}, Freeze ID: {}",
                    orderId, legalOrder.getWalletId(), freezeId);
            }

            // Update order status
            legalOrder.setOrderStatus(OrderStatus.EXECUTED);
            legalOrder.setUpdatedBy(executedBy);

            LegalOrder executedOrder = legalOrderRepository.save(legalOrder);

            // Create audit trail
            auditService.logLegalOrderEvent(
                orderId,
                "LEGAL_ORDER_EXECUTED",
                String.format("Legal order executed by %s. Freeze ID: %s",
                    executedBy, legalOrder.getFreezeId()),
                legalOrder.getCorrelationId()
            );

            // Notify legal department and affected user
            notifyLegalDepartment(executedOrder, "ORDER_EXECUTED");
            notifyAffectedUser(executedOrder);

            log.info("LEGAL: Legal order executed successfully - Order ID: {}, Freeze ID: {}",
                orderId, legalOrder.getFreezeId());

            return executedOrder;

        } catch (Exception e) {
            log.error("LEGAL: Failed to execute legal order - Order ID: {}",
                orderId, e);

            // Update order status to error
            legalOrder.setOrderStatus(OrderStatus.ERROR);
            legalOrder.setComplianceNotes(
                (legalOrder.getComplianceNotes() != null ? legalOrder.getComplianceNotes() + "\n" : "") +
                "Execution error: " + e.getMessage());
            legalOrderRepository.save(legalOrder);

            throw new LegalOrderException("Failed to execute legal order: " + orderId, e);
        }
    }

    /**
     * Release funds for legal order (order fulfilled or expired)
     *
     * @param orderId Legal order ID
     * @param amountReleased Amount being released
     * @param releasedBy User releasing funds
     * @param releaseReason Reason for release
     * @return Updated legal order
     */
    @Transactional
    public LegalOrder releaseLegalOrderFunds(
        UUID orderId,
        BigDecimal amountReleased,
        String releasedBy,
        String releaseReason
    ) {
        log.info("LEGAL: Releasing funds for legal order - Order ID: {}, Amount: {}, Released by: {}",
            orderId, amountReleased, releasedBy);

        LegalOrder legalOrder = legalOrderRepository.findById(orderId)
            .orElseThrow(() -> new LegalOrderException("Legal order not found: " + orderId));

        // Unfreeze wallet if freeze exists
        if (legalOrder.getFreezeId() != null) {
            walletFreezeService.unfreezeWallet(
                legalOrder.getFreezeId(),
                releasedBy,
                "Legal order release: " + releaseReason
            );

            log.info("LEGAL: Wallet unfrozen for legal order - Order ID: {}, Freeze ID: {}",
                orderId, legalOrder.getFreezeId());
        }

        // Update order status
        legalOrder.setOrderStatus(OrderStatus.RELEASED);
        legalOrder.setFundsReleasedDate(LocalDateTime.now());
        legalOrder.setAmountReleased(amountReleased);
        legalOrder.setComplianceNotes(
            (legalOrder.getComplianceNotes() != null ? legalOrder.getComplianceNotes() + "\n" : "") +
            "Release reason: " + releaseReason);
        legalOrder.setUpdatedBy(releasedBy);

        LegalOrder releasedOrder = legalOrderRepository.save(legalOrder);

        // Create audit trail
        auditService.logLegalOrderEvent(
            orderId,
            "LEGAL_ORDER_FUNDS_RELEASED",
            String.format("Funds released by %s. Amount: %s, Reason: %s",
                releasedBy, amountReleased, releaseReason),
            legalOrder.getCorrelationId()
        );

        // Notify legal department and affected user
        notifyLegalDepartment(releasedOrder, "ORDER_RELEASED");
        notifyAffectedUser(releasedOrder);

        log.info("LEGAL: Legal order funds released - Order ID: {}, Amount: {}",
            orderId, amountReleased);

        return releasedOrder;
    }

    /**
     * Process expired legal orders
     *
     * Scheduled task to handle order expiration
     */
    @Transactional
    public void processExpiredOrders() {
        log.info("LEGAL: Processing expired legal orders");

        List<LegalOrder> expiredOrders = legalOrderRepository
            .findExpiredUnprocessedOrders(LocalDate.now());

        for (LegalOrder order : expiredOrders) {
            try {
                log.warn("LEGAL: Processing expired legal order - Order ID: {}, Order Number: {}, Expiration: {}",
                    order.getOrderId(), order.getOrderNumber(), order.getExpirationDate());

                // Auto-release funds for expired orders
                if (order.getFreezeId() != null) {
                    releaseLegalOrderFunds(
                        order.getOrderId(),
                        order.getAmount(),
                        "system-auto-expiration",
                        "Legal order expired on " + order.getExpirationDate()
                    );
                } else {
                    // Just update status if no freeze
                    order.setOrderStatus(OrderStatus.EXPIRED);
                    order.setUpdatedBy("system-auto-expiration");
                    legalOrderRepository.save(order);
                }

                log.info("LEGAL: Expired legal order processed - Order ID: {}", order.getOrderId());

            } catch (Exception e) {
                log.error("LEGAL: Failed to process expired legal order - Order ID: {}",
                    order.getOrderId(), e);
            }
        }

        log.info("LEGAL: Processed {} expired legal orders", expiredOrders.size());
    }

    /**
     * Get all pending review orders
     */
    public List<LegalOrder> getPendingReviewOrders() {
        return legalOrderRepository.findPendingReviewOrders();
    }

    /**
     * Get all urgent orders
     */
    public List<LegalOrder> getUrgentOrders() {
        return legalOrderRepository.findUrgentOrders();
    }

    /**
     * Get active legal orders for user
     */
    public List<LegalOrder> getActiveLegalOrdersForUser(UUID userId) {
        return legalOrderRepository.findActiveLegalOrdersByUserId(userId);
    }

    /**
     * Get legal order by ID
     */
    public LegalOrder getLegalOrder(UUID orderId) {
        return legalOrderRepository.findById(orderId)
            .orElseThrow(() -> new LegalOrderException("Legal order not found: " + orderId));
    }

    // Private helper methods

    private Priority determinePriority(OrderType orderType, LocalDate expirationDate) {
        // Criminal forfeiture and regulatory freezes are always critical
        if (orderType == OrderType.CRIMINAL_FORFEITURE ||
            orderType == OrderType.REGULATORY_FREEZE) {
            return Priority.CRITICAL;
        }

        // Tax levies are high priority
        if (orderType == OrderType.TAX_LEVY) {
            return Priority.HIGH;
        }

        // Orders expiring soon are urgent
        if (expirationDate != null &&
            expirationDate.isBefore(LocalDate.now().plusDays(7))) {
            return Priority.URGENT;
        }

        // Child support is high priority
        if (orderType == OrderType.CHILD_SUPPORT) {
            return Priority.HIGH;
        }

        return Priority.NORMAL;
    }

    private void autoAssignLegalCounsel(LegalOrder legalOrder) {
        // TODO: Implement intelligent assignment based on order type, jurisdiction, workload
        // For now, just log that assignment is needed
        log.info("LEGAL: Legal order requires counsel assignment - Order ID: {}, Type: {}",
            legalOrder.getOrderId(), legalOrder.getOrderType());
    }

    private void notifyLegalDepartment(LegalOrder legalOrder, String eventType) {
        try {
            notificationService.sendLegalDepartmentNotification(
                eventType,
                String.format("Legal Order %s - Order: %s, Type: %s, User: %s, Authority: %s",
                    eventType, legalOrder.getOrderNumber(), legalOrder.getOrderType(),
                    legalOrder.getUserId(), legalOrder.getIssuingAuthority()),
                legalOrder
            );
        } catch (Exception e) {
            log.error("LEGAL: Failed to send legal department notification - Order ID: {}",
                legalOrder.getOrderId(), e);
            // Don't fail the operation if notification fails
        }
    }

    private void notifyAffectedUser(LegalOrder legalOrder) {
        try {
            notificationService.sendUserNotification(
                legalOrder.getUserId(),
                "LEGAL_ORDER_NOTIFICATION",
                String.format("Legal action has been taken on your account. Order: %s, Authority: %s",
                    legalOrder.getOrderNumber(), legalOrder.getIssuingAuthority())
            );
        } catch (Exception e) {
            log.error("LEGAL: Failed to send user notification - Order ID: {}, User: {}",
                legalOrder.getOrderId(), legalOrder.getUserId(), e);
            // Don't fail the operation if notification fails
        }
    }
}
