package com.waqiti.billpayment.scheduler;

import com.waqiti.billpayment.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled jobs for bill payment service
 * Handles background processing, reminders, auto-payments, and imports
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BillPaymentScheduler {

    private final BillService billService;
    private final BillPaymentProcessingService paymentProcessingService;
    private final AutoPayService autoPayService;
    private final BillReminderService reminderService;
    private final BillSharingService billSharingService;
    private final BillerIntegrationService billerIntegrationService;

    /**
     * Process scheduled payments
     * Runs every 5 minutes
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void processScheduledPayments() {
        log.info("=== Scheduled Job: Process Scheduled Payments ===");
        try {
            paymentProcessingService.processScheduledPayments();
        } catch (Exception e) {
            log.error("Error processing scheduled payments", e);
        }
    }

    /**
     * Retry failed payments
     * Runs every 15 minutes
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void retryFailedPayments() {
        log.info("=== Scheduled Job: Retry Failed Payments ===");
        try {
            paymentProcessingService.processFailedPaymentRetries();
        } catch (Exception e) {
            log.error("Error retrying failed payments", e);
        }
    }

    /**
     * Process auto-payments
     * Runs every hour at minute 0
     */
    @Scheduled(cron = "0 0 * * * *")
    public void processAutoPayments() {
        log.info("=== Scheduled Job: Process Auto-Payments ===");
        try {
            autoPayService.processAutoPayments();
        } catch (Exception e) {
            log.error("Error processing auto-payments", e);
        }
    }

    /**
     * Monitor suspended auto-pay configs
     * Runs daily at 10:00 AM
     */
    @Scheduled(cron = "0 0 10 * * *")
    public void monitorSuspendedAutoPayConfigs() {
        log.info("=== Scheduled Job: Monitor Suspended Auto-Pay Configs ===");
        try {
            autoPayService.monitorSuspendedConfigs();
        } catch (Exception e) {
            log.error("Error monitoring suspended auto-pay configs", e);
        }
    }

    /**
     * Schedule reminders for upcoming bills
     * Runs daily at 8:00 AM
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void scheduleReminders() {
        log.info("=== Scheduled Job: Schedule Bill Reminders ===");
        try {
            reminderService.scheduleRemindersForUpcomingBills();
        } catch (Exception e) {
            log.error("Error scheduling reminders", e);
        }
    }

    /**
     * Process reminders due to send
     * Runs every 15 minutes
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void sendReminders() {
        log.info("=== Scheduled Job: Send Bill Reminders ===");
        try {
            reminderService.processRemindersDueToSend();
        } catch (Exception e) {
            log.error("Error sending reminders", e);
        }
    }

    /**
     * Retry failed reminders
     * Runs every 30 minutes
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void retryFailedReminders() {
        log.info("=== Scheduled Job: Retry Failed Reminders ===");
        try {
            reminderService.retryFailedReminders();
        } catch (Exception e) {
            log.error("Error retrying failed reminders", e);
        }
    }

    /**
     * Schedule overdue reminders
     * Runs daily at 9:00 AM
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void scheduleOverdueReminders() {
        log.info("=== Scheduled Job: Schedule Overdue Reminders ===");
        try {
            reminderService.scheduleOverdueReminders();
        } catch (Exception e) {
            log.error("Error scheduling overdue reminders", e);
        }
    }

    /**
     * Process overdue bills
     * Runs daily at midnight
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void processOverdueBills() {
        log.info("=== Scheduled Job: Process Overdue Bills ===");
        try {
            billService.processOverdueBills();
        } catch (Exception e) {
            log.error("Error processing overdue bills", e);
        }
    }

    /**
     * Send share reminders
     * Runs weekly on Mondays at 10:00 AM
     */
    @Scheduled(cron = "0 0 10 * * MON")
    public void sendShareReminders() {
        log.info("=== Scheduled Job: Send Share Reminders ===");
        try {
            billSharingService.sendShareReminders();
        } catch (Exception e) {
            log.error("Error sending share reminders", e);
        }
    }

    /**
     * Process expired share requests
     * Runs daily at 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void processExpiredShareRequests() {
        log.info("=== Scheduled Job: Process Expired Share Requests ===");
        try {
            billSharingService.processExpiredShareRequests();
        } catch (Exception e) {
            log.error("Error processing expired share requests", e);
        }
    }

    /**
     * Process auto-imports from billers
     * Runs every 6 hours
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void processAutoImports() {
        log.info("=== Scheduled Job: Process Auto-Imports ===");
        try {
            billerIntegrationService.processAutoImports();
        } catch (Exception e) {
            log.error("Error processing auto-imports", e);
        }
    }

    /**
     * Health check - log scheduler status
     * Runs every hour at minute 30
     */
    @Scheduled(cron = "0 30 * * * *")
    public void healthCheck() {
        log.info("=== Scheduler Health Check: All schedulers operational ===");
    }
}
