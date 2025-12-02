package com.waqiti.lending.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Notification Service
 * Sends notifications to borrowers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    /**
     * Send application submitted notification
     */
    public void sendApplicationSubmittedNotification(UUID borrowerId, String applicationId) {
        log.info("Sending application submitted notification to borrower: {} for application: {}",
                borrowerId, applicationId);
        // TODO: Integrate with actual notification service (email/SMS)
    }

    /**
     * Send application approved notification
     */
    public void sendApplicationApprovedNotification(UUID borrowerId, String applicationId,
                                                   BigDecimal approvedAmount) {
        log.info("Sending approval notification to borrower: {} for application: {} - Amount: {}",
                borrowerId, applicationId, approvedAmount);
        // TODO: Integrate with actual notification service
    }

    /**
     * Send application rejected notification
     */
    public void sendApplicationRejectedNotification(UUID borrowerId, String applicationId, String reason) {
        log.info("Sending rejection notification to borrower: {} for application: {} - Reason: {}",
                borrowerId, applicationId, reason);
        // TODO: Integrate with actual notification service
    }

    /**
     * Send loan disbursed notification
     */
    public void sendLoanDisbursedNotification(UUID borrowerId, String loanId, BigDecimal amount) {
        log.info("Sending disbursement notification to borrower: {} for loan: {} - Amount: {}",
                borrowerId, loanId, amount);
        // TODO: Integrate with actual notification service
    }

    /**
     * Send payment received notification
     */
    public void sendPaymentReceivedNotification(UUID borrowerId, String loanId,
                                               BigDecimal paymentAmount, BigDecimal remainingBalance) {
        log.info("Sending payment confirmation to borrower: {} for loan: {} - Payment: {}, Remaining: {}",
                borrowerId, loanId, paymentAmount, remainingBalance);
        // TODO: Integrate with actual notification service
    }

    /**
     * Send payment due reminder
     */
    public void sendPaymentDueReminder(UUID borrowerId, String loanId, BigDecimal amountDue, int daysUntilDue) {
        log.info("Sending payment reminder to borrower: {} for loan: {} - Due in {} days, Amount: {}",
                borrowerId, loanId, daysUntilDue, amountDue);
        // TODO: Integrate with actual notification service
    }

    /**
     * Send late payment notification
     */
    public void sendLatePaymentNotification(UUID borrowerId, String loanId, int daysPastDue) {
        log.warn("Sending late payment notification to borrower: {} for loan: {} - {} days past due",
                borrowerId, loanId, daysPastDue);
        // TODO: Integrate with actual notification service
    }

    /**
     * Send loan paid off notification
     */
    public void sendLoanPaidOffNotification(UUID borrowerId, String loanId) {
        log.info("Sending loan paid off notification to borrower: {} for loan: {}",
                borrowerId, loanId);
        // TODO: Integrate with actual notification service
    }
}
