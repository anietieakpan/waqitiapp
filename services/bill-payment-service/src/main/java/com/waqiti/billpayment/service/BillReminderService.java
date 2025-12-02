package com.waqiti.billpayment.service;

import com.waqiti.billpayment.entity.*;
import com.waqiti.billpayment.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing bill reminders
 * Handles reminder scheduling, sending, and tracking
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillReminderService {

    private final BillReminderRepository reminderRepository;
    private final BillRepository billRepository;
    private final BillPaymentAuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;
    // private final NotificationServiceClient notificationClient;

    private Counter reminderCreatedCounter;
    private Counter reminderSentCounter;
    private Counter reminderFailedCounter;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        reminderCreatedCounter = Counter.builder("reminder.created")
                .description("Number of reminders created")
                .register(meterRegistry);

        reminderSentCounter = Counter.builder("reminder.sent")
                .description("Number of reminders sent")
                .register(meterRegistry);

        reminderFailedCounter = Counter.builder("reminder.failed")
                .description("Number of reminders failed")
                .register(meterRegistry);
    }

    /**
     * Create reminder for bill
     */
    @Transactional
    public BillReminder createReminder(String userId, UUID billId, ReminderType reminderType,
                                       LocalDateTime scheduledTime) {
        log.info("Creating reminder for bill: {}, type: {}, scheduled: {}", billId, reminderType, scheduledTime);

        // Validate bill
        Bill bill = billRepository.findByIdAndUserId(billId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        if (bill.getStatus() != BillStatus.UNPAID && bill.getStatus() != BillStatus.PARTIALLY_PAID) {
            throw new IllegalStateException("Cannot create reminder for paid bill");
        }

        // Check if reminder already exists
        boolean exists = reminderRepository.existsByBillIdAndReminderTypeAndStatus(
                billId, reminderType, ReminderStatus.PENDING
        );

        if (exists) {
            log.warn("Reminder already exists for bill: {}, type: {}", billId, reminderType);
            throw new IllegalStateException("Reminder already exists for this bill");
        }

        BillReminder reminder = BillReminder.builder()
                .userId(userId)
                .billId(billId)
                .reminderType(reminderType)
                .scheduledSendTime(scheduledTime)
                .status(ReminderStatus.PENDING)
                .notificationChannel("EMAIL") // Default channel
                .build();

        BillReminder savedReminder = reminderRepository.save(reminder);

        auditLog(savedReminder, "REMINDER_CREATED", userId);
        reminderCreatedCounter.increment();

        log.info("Reminder created: {}", savedReminder.getId());
        return savedReminder;
    }

    /**
     * Schedule reminders for upcoming bills (called by scheduler)
     */
    @Transactional
    public void scheduleRemindersForUpcomingBills() {
        log.info("Scheduling reminders for upcoming bills");

        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAhead = today.plusDays(7);

        // Find bills due in 7, 3, 1 days
        List<Bill> upcomingBills = billRepository.findUpcomingBills(null, sevenDaysAhead);

        int scheduledCount = 0;

        for (Bill bill : upcomingBills) {
            try {
                scheduledCount += scheduleRemindersForBill(bill);
            } catch (Exception e) {
                log.error("Error scheduling reminders for bill: {}", bill.getId(), e);
            }
        }

        log.info("Scheduled {} reminders", scheduledCount);
    }

    /**
     * Schedule all reminders for a specific bill
     */
    @Transactional
    public int scheduleRemindersForBill(Bill bill) {
        log.debug("Scheduling reminders for bill: {}", bill.getId());

        LocalDate today = LocalDate.now();
        LocalDate dueDate = bill.getDueDate();
        int scheduledCount = 0;

        // 7 days before
        if (dueDate.minusDays(7).equals(today)) {
            createReminderIfNotExists(bill, ReminderType.DUE_IN_7_DAYS, dueDate.minusDays(7).atTime(9, 0));
            scheduledCount++;
        }

        // 3 days before
        if (dueDate.minusDays(3).equals(today)) {
            createReminderIfNotExists(bill, ReminderType.DUE_IN_3_DAYS, dueDate.minusDays(3).atTime(9, 0));
            scheduledCount++;
        }

        // 1 day before
        if (dueDate.minusDays(1).equals(today)) {
            createReminderIfNotExists(bill, ReminderType.DUE_TOMORROW, dueDate.minusDays(1).atTime(9, 0));
            scheduledCount++;
        }

        // Due today
        if (dueDate.equals(today)) {
            createReminderIfNotExists(bill, ReminderType.DUE_TODAY, dueDate.atTime(9, 0));
            scheduledCount++;
        }

        return scheduledCount;
    }

    /**
     * Process reminders due to be sent (called by scheduler)
     */
    @Transactional
    public void processRemindersDueToSend() {
        log.info("Processing reminders due to send");

        List<BillReminder> dueReminders = reminderRepository.findRemindersDueToSend(LocalDateTime.now());

        for (BillReminder reminder : dueReminders) {
            try {
                sendReminder(reminder);
            } catch (Exception e) {
                log.error("Error sending reminder: {}", reminder.getId(), e);
                handleReminderFailure(reminder, e.getMessage());
            }
        }

        log.info("Processed {} reminders", dueReminders.size());
    }

    /**
     * Send a reminder
     */
    @Transactional
    public void sendReminder(BillReminder reminder) {
        log.info("Sending reminder: {}, type: {}", reminder.getId(), reminder.getReminderType());

        // Get bill details
        Bill bill = billRepository.findById(reminder.getBillId())
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + reminder.getBillId()));

        // Build reminder message
        String message = buildReminderMessage(reminder, bill);
        reminder.setMessageContent(message);

        // Send notification
        UUID notificationId = sendNotification(reminder, bill, message);

        // Mark as sent
        reminder.markAsSent(notificationId);
        reminderRepository.save(reminder);

        // Update bill reminder status
        bill.setReminderSent(true);
        bill.setReminderSentAt(LocalDateTime.now());
        billRepository.save(bill);

        auditLog(reminder, "REMINDER_SENT", reminder.getUserId());
        reminderSentCounter.increment();

        log.info("Reminder sent successfully: {}", reminder.getId());
    }

    /**
     * Handle reminder failure
     */
    @Transactional
    public void handleReminderFailure(BillReminder reminder, String reason) {
        log.warn("Handling reminder failure: {}, reason: {}", reminder.getId(), reason);

        reminder.markAsFailed(reason);
        reminderRepository.save(reminder);

        auditLog(reminder, "REMINDER_FAILED", reminder.getUserId());
        reminderFailedCounter.increment();

        log.info("Reminder failure recorded: {}, retry count: {}", reminder.getId(), reminder.getRetryCount());
    }

    /**
     * Retry failed reminders (called by scheduler)
     */
    @Transactional
    public void retryFailedReminders() {
        log.info("Retrying failed reminders");

        List<BillReminder> failedReminders = reminderRepository.findFailedRemindersForRetry();

        for (BillReminder reminder : failedReminders) {
            try {
                sendReminder(reminder);
            } catch (Exception e) {
                log.error("Error retrying reminder: {}", reminder.getId(), e);
                handleReminderFailure(reminder, e.getMessage());
            }
        }

        log.info("Retried {} failed reminders", failedReminders.size());
    }

    /**
     * Schedule overdue reminders
     */
    @Transactional
    public void scheduleOverdueReminders() {
        log.info("Scheduling overdue reminders");

        List<Bill> overdueBills = billRepository.findOverdueBills(null, LocalDate.now());
        int scheduledCount = 0;

        for (Bill bill : overdueBills) {
            try {
                // Check if overdue reminder already sent
                boolean exists = reminderRepository.existsByBillIdAndReminderTypeAndStatus(
                        bill.getId(), ReminderType.OVERDUE, ReminderStatus.SENT
                );

                if (!exists) {
                    createReminder(bill.getUserId(), bill.getId(), ReminderType.OVERDUE, LocalDateTime.now());
                    scheduledCount++;
                }
            } catch (Exception e) {
                log.error("Error scheduling overdue reminder for bill: {}", bill.getId(), e);
            }
        }

        log.info("Scheduled {} overdue reminders", scheduledCount);
    }

    /**
     * Get reminders for user
     */
    @Transactional(readOnly = true)
    public List<BillReminder> getRemindersByUser(String userId) {
        return reminderRepository.findByUserId(userId);
    }

    /**
     * Get reminders for bill
     */
    @Transactional(readOnly = true)
    public List<BillReminder> getRemindersByBill(UUID billId) {
        return reminderRepository.findByBillId(billId);
    }

    /**
     * Cancel reminder
     */
    @Transactional
    public void cancelReminder(UUID reminderId, String userId) {
        log.info("Cancelling reminder: {}", reminderId);

        BillReminder reminder = reminderRepository.findById(reminderId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Reminder not found: " + reminderId));

        if (reminder.getStatus() != ReminderStatus.PENDING) {
            throw new IllegalStateException("Can only cancel pending reminders");
        }

        reminder.setStatus(ReminderStatus.CANCELLED);
        reminderRepository.save(reminder);

        auditLog(reminder, "REMINDER_CANCELLED", userId);

        log.info("Reminder cancelled: {}", reminderId);
    }

    // Private helper methods

    private void createReminderIfNotExists(Bill bill, ReminderType type, LocalDateTime scheduledTime) {
        boolean exists = reminderRepository.existsByBillIdAndReminderTypeAndStatus(
                bill.getId(), type, ReminderStatus.PENDING
        );

        if (!exists) {
            createReminder(bill.getUserId(), bill.getId(), type, scheduledTime);
        }
    }

    private String buildReminderMessage(BillReminder reminder, Bill bill) {
        return switch (reminder.getReminderType()) {
            case DUE_IN_7_DAYS -> String.format(
                    "Reminder: Your %s bill of $%.2f is due in 7 days (%s)",
                    bill.getBillerName(), bill.getAmount(), bill.getDueDate()
            );
            case DUE_IN_3_DAYS -> String.format(
                    "Reminder: Your %s bill of $%.2f is due in 3 days (%s)",
                    bill.getBillerName(), bill.getAmount(), bill.getDueDate()
            );
            case DUE_TOMORROW -> String.format(
                    "Reminder: Your %s bill of $%.2f is due tomorrow (%s)",
                    bill.getBillerName(), bill.getAmount(), bill.getDueDate()
            );
            case DUE_TODAY -> String.format(
                    "Urgent: Your %s bill of $%.2f is due TODAY (%s)",
                    bill.getBillerName(), bill.getAmount(), bill.getDueDate()
            );
            case OVERDUE -> String.format(
                    "OVERDUE: Your %s bill of $%.2f was due on %s. Please pay immediately to avoid late fees.",
                    bill.getBillerName(), bill.getAmount(), bill.getDueDate()
            );
            case CUSTOM -> "Custom reminder for your bill";
        };
    }

    private UUID sendNotification(BillReminder reminder, Bill bill, String message) {
        log.info("Sending notification for reminder: {}, channel: {}", reminder.getId(), reminder.getNotificationChannel());

        // TODO: Call notification service
        // NotificationRequest request = NotificationRequest.builder()
        //     .userId(reminder.getUserId())
        //     .channel(reminder.getNotificationChannel())
        //     .title("Bill Reminder")
        //     .message(message)
        //     .build();
        // return notificationClient.send(request).getNotificationId();

        // Placeholder
        return UUID.randomUUID();
    }

    private void auditLog(BillReminder reminder, String action, String userId) {
        try {
            BillPaymentAuditLog auditLog = BillPaymentAuditLog.builder()
                    .entityType("BILL_REMINDER")
                    .entityId(reminder.getId())
                    .action(action)
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to create audit log for reminder: {}", reminder.getId(), e);
        }
    }
}
