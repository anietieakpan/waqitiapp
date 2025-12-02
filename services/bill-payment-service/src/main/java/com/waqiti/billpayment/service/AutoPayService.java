package com.waqiti.billpayment.service;

import com.waqiti.billpayment.entity.*;
import com.waqiti.billpayment.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing automatic bill payments
 * Handles auto-pay configuration, scheduling, and execution
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AutoPayService {

    private final AutoPayConfigRepository autoPayConfigRepository;
    private final BillRepository billRepository;
    private final BillerConnectionRepository billerConnectionRepository;
    private final BillPaymentProcessingService paymentProcessingService;
    private final BillPaymentAuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;

    private Counter autoPayConfiguredCounter;
    private Counter autoPayExecutedCounter;
    private Counter autoPayFailedCounter;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        autoPayConfiguredCounter = Counter.builder("autopay.configured")
                .description("Number of auto-pay configs created")
                .register(meterRegistry);

        autoPayExecutedCounter = Counter.builder("autopay.executed")
                .description("Number of auto-payments executed")
                .register(meterRegistry);

        autoPayFailedCounter = Counter.builder("autopay.failed")
                .description("Number of auto-payments failed")
                .register(meterRegistry);
    }

    /**
     * Create auto-pay configuration
     */
    @Transactional
    public AutoPayConfig createAutoPayConfig(String userId, AutoPayConfig config) {
        log.info("Creating auto-pay config for user: {}, biller: {}", userId, config.getBillerId());

        // Validate biller connection exists
        BillerConnection connection = billerConnectionRepository.findById(config.getBillerConnectionId())
                .orElseThrow(() -> new IllegalArgumentException("Biller connection not found"));

        if (!connection.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Biller connection does not belong to user");
        }

        if (!connection.isActive()) {
            throw new IllegalStateException("Biller connection is not active");
        }

        config.setUserId(userId);
        config.setStatus(AutoPayStatus.ACTIVE);

        AutoPayConfig savedConfig = autoPayConfigRepository.save(config);

        auditLog(savedConfig, "AUTOPAY_CONFIGURED", userId);
        autoPayConfiguredCounter.increment();

        log.info("Auto-pay config created: {}", savedConfig.getId());
        return savedConfig;
    }

    /**
     * Update auto-pay configuration
     */
    @Transactional
    public AutoPayConfig updateAutoPayConfig(UUID configId, String userId, AutoPayConfig updates) {
        log.info("Updating auto-pay config: {}", configId);

        AutoPayConfig existing = getAutoPayConfig(configId, userId);

        // Update fields
        existing.setPaymentAmountType(updates.getPaymentAmountType());
        existing.setFixedAmount(updates.getFixedAmount());
        existing.setDaysBeforeDueDate(updates.getDaysBeforeDueDate());
        existing.setMinimumAmountThreshold(updates.getMinimumAmountThreshold());
        existing.setMaximumAmountThreshold(updates.getMaximumAmountThreshold());
        existing.setNotifyBeforePayment(updates.getNotifyBeforePayment());
        existing.setNotificationHoursBefore(updates.getNotificationHoursBefore());

        AutoPayConfig savedConfig = autoPayConfigRepository.save(existing);

        auditLog(savedConfig, "AUTOPAY_UPDATED", userId);

        log.info("Auto-pay config updated: {}", configId);
        return savedConfig;
    }

    /**
     * Get auto-pay configuration
     */
    @Transactional(readOnly = true)
    public AutoPayConfig getAutoPayConfig(UUID configId, String userId) {
        return autoPayConfigRepository.findById(configId)
                .filter(config -> config.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Auto-pay config not found: " + configId));
    }

    /**
     * Get all auto-pay configs for user
     */
    @Transactional(readOnly = true)
    public List<AutoPayConfig> getAutoPayConfigsByUser(String userId) {
        return autoPayConfigRepository.findByUserId(userId);
    }

    /**
     * Get active auto-pay configs for user
     */
    @Transactional(readOnly = true)
    public List<AutoPayConfig> getActiveAutoPayConfigs(String userId) {
        return autoPayConfigRepository.findByUserIdAndStatus(userId, AutoPayStatus.ACTIVE);
    }

    /**
     * Pause auto-pay configuration
     */
    @Transactional
    public void pauseAutoPayConfig(UUID configId, String userId) {
        log.info("Pausing auto-pay config: {}", configId);

        AutoPayConfig config = getAutoPayConfig(configId, userId);
        config.setStatus(AutoPayStatus.PAUSED);
        autoPayConfigRepository.save(config);

        auditLog(config, "AUTOPAY_PAUSED", userId);

        log.info("Auto-pay config paused: {}", configId);
    }

    /**
     * Resume auto-pay configuration
     */
    @Transactional
    public void resumeAutoPayConfig(UUID configId, String userId) {
        log.info("Resuming auto-pay config: {}", configId);

        AutoPayConfig config = getAutoPayConfig(configId, userId);

        if (config.getStatus() != AutoPayStatus.PAUSED) {
            throw new IllegalStateException("Can only resume paused auto-pay configs");
        }

        config.setStatus(AutoPayStatus.ACTIVE);
        autoPayConfigRepository.save(config);

        auditLog(config, "AUTOPAY_RESUMED", userId);

        log.info("Auto-pay config resumed: {}", configId);
    }

    /**
     * Cancel auto-pay configuration
     */
    @Transactional
    public void cancelAutoPayConfig(UUID configId, String userId) {
        log.info("Cancelling auto-pay config: {}", configId);

        AutoPayConfig config = getAutoPayConfig(configId, userId);
        config.softDelete(userId);
        autoPayConfigRepository.save(config);

        auditLog(config, "AUTOPAY_CANCELLED", userId);

        log.info("Auto-pay config cancelled: {}", configId);
    }

    /**
     * Process auto-payments due now (called by scheduler)
     */
    @Transactional
    public void processAutoPayments() {
        log.info("Processing auto-payments");

        List<AutoPayConfig> dueConfigs = autoPayConfigRepository.findAutoPaymentsDueNow(LocalDateTime.now());

        for (AutoPayConfig config : dueConfigs) {
            try {
                executeAutoPayment(config);
            } catch (Exception e) {
                log.error("Error executing auto-payment for config: {}", config.getId(), e);
                handleAutoPaymentFailure(config, e.getMessage());
            }
        }

        log.info("Processed {} auto-payments", dueConfigs.size());
    }

    /**
     * Execute a single auto-payment
     */
    @Transactional
    public void executeAutoPayment(AutoPayConfig config) {
        log.info("Executing auto-payment for config: {}", config.getId());

        // Get the bill
        Bill bill = billRepository.findById(config.getBillId())
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + config.getBillId()));

        // Validate bill is unpaid
        if (bill.getStatus() != BillStatus.UNPAID && bill.getStatus() != BillStatus.PARTIALLY_PAID) {
            log.warn("Bill already paid, skipping auto-payment: {}", bill.getId());
            return;
        }

        // Calculate payment amount
        BigDecimal paymentAmount = config.calculatePaymentAmount(bill.getAmount());

        // Validate amount within thresholds
        if (!config.isAmountWithinThresholds(paymentAmount)) {
            log.warn("Payment amount {} outside thresholds for config: {}", paymentAmount, config.getId());
            handleAutoPaymentFailure(config, "Amount outside configured thresholds");
            return;
        }

        // Initiate payment
        try {
            BillPayment payment = paymentProcessingService.initiatePayment(
                    config.getUserId(),
                    bill.getId(),
                    paymentAmount,
                    config.getPaymentMethod(),
                    "AUTOPAY-" + config.getId() + "-" + UUID.randomUUID()
            );

            // Record success
            config.recordSuccess(payment.getId(), paymentAmount);
            autoPayConfigRepository.save(config);

            autoPayExecutedCounter.increment();

            log.info("Auto-payment executed successfully: config={}, payment={}", config.getId(), payment.getId());

        } catch (Exception e) {
            log.error("Auto-payment failed: config={}", config.getId(), e);
            handleAutoPaymentFailure(config, e.getMessage());
            throw e;
        }
    }

    /**
     * Handle auto-payment failure
     */
    @Transactional
    public void handleAutoPaymentFailure(AutoPayConfig config, String reason) {
        log.warn("Handling auto-payment failure for config: {}, reason: {}", config.getId(), reason);

        config.recordFailure(reason);
        autoPayConfigRepository.save(config);

        auditLog(config, "AUTOPAY_FAILED", config.getUserId());
        autoPayFailedCounter.increment();

        // Send notification to user
        // TODO: Call notification service

        log.info("Auto-payment failure recorded: config={}, failures={}/{}",
                config.getId(), config.getFailureCount(), config.getMaxFailureCount());
    }

    /**
     * Monitor and resume suspended auto-pay configs
     */
    @Transactional
    public void monitorSuspendedConfigs() {
        log.info("Monitoring suspended auto-pay configs");

        List<AutoPayConfig> suspendedConfigs = autoPayConfigRepository.findSuspendedConfigs();

        for (AutoPayConfig config : suspendedConfigs) {
            log.warn("Auto-pay config suspended due to failures: config={}, failures={}",
                    config.getId(), config.getFailureCount());

            // TODO: Send alert to user about suspended auto-pay
        }

        log.info("Found {} suspended auto-pay configs", suspendedConfigs.size());
    }

    // Helper methods

    private void auditLog(AutoPayConfig config, String action, String userId) {
        try {
            BillPaymentAuditLog auditLog = BillPaymentAuditLog.builder()
                    .entityType("AUTOPAY_CONFIG")
                    .entityId(config.getId())
                    .action(action)
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to create audit log for auto-pay config: {}", config.getId(), e);
        }
    }
}
