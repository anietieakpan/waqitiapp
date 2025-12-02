package com.waqiti.currency.service;

import com.waqiti.common.service.NotificationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Currency Notification Service
 *
 * Handles notifications for currency conversion events:
 * - Successful conversion confirmations
 * - Rate unavailability alerts
 * - Unsupported currency pair notifications
 * - Retry scheduling notices
 * - Failure alerts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyNotificationService {

    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    /**
     * Send conversion success notification
     */
    @Async
    public void sendConversionSuccessNotification(String conversionId, String customerId,
                                                  String sourceCurrency, String targetCurrency,
                                                  BigDecimal sourceAmount, BigDecimal targetAmount,
                                                  BigDecimal exchangeRate, String correlationId) {

        log.info("Sending conversion success notification: conversionId={} customer={} {}→{} correlationId={}",
                conversionId, customerId, sourceCurrency, targetCurrency, correlationId);

        try {
            String subject = "Currency Conversion Successful";
            String message = String.format(
                "Your currency conversion has been completed successfully.\n\n" +
                "Conversion ID: %s\n" +
                "From: %s %s\n" +
                "To: %s %s\n" +
                "Exchange Rate: %.6f\n" +
                "Status: Completed",
                conversionId, sourceAmount, sourceCurrency,
                targetAmount, targetCurrency, exchangeRate
            );

            notificationService.sendNotification(
                customerId,
                subject,
                message,
                "CURRENCY_CONVERSION_SUCCESS",
                Map.of(
                    "conversionId", conversionId,
                    "sourceCurrency", sourceCurrency,
                    "targetCurrency", targetCurrency,
                    "exchangeRate", exchangeRate.toString()
                ),
                correlationId
            );

            incrementCounter("currency.notification.success.sent");

        } catch (Exception e) {
            log.error("Failed to send conversion success notification: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.notification.success.error");
        }
    }

    /**
     * Notify about retry scheduled for rate unavailability
     */
    @Async
    public void notifyRetryScheduled(String conversionId, String customerId,
                                    String sourceCurrency, String targetCurrency,
                                    Instant retryTime, String correlationId) {

        log.info("Sending retry scheduled notification: conversionId={} customer={} retryTime={} correlationId={}",
                conversionId, customerId, retryTime, correlationId);

        try {
            String subject = "Currency Conversion Retry Scheduled";
            String message = String.format(
                "Your currency conversion has been queued for retry.\n\n" +
                "Conversion ID: %s\n" +
                "Currency Pair: %s → %s\n" +
                "Reason: Exchange rate temporarily unavailable\n" +
                "Retry Time: %s\n" +
                "Status: Pending Retry",
                conversionId, sourceCurrency, targetCurrency, retryTime
            );

            notificationService.sendNotification(
                customerId,
                subject,
                message,
                "CURRENCY_CONVERSION_RETRY",
                Map.of(
                    "conversionId", conversionId,
                    "sourceCurrency", sourceCurrency,
                    "targetCurrency", targetCurrency,
                    "retryTime", retryTime.toString()
                ),
                correlationId
            );

            incrementCounter("currency.notification.retry.sent");

        } catch (Exception e) {
            log.error("Failed to send retry notification: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.notification.retry.error");
        }
    }

    /**
     * Notify about unsupported currency pair
     */
    @Async
    public void notifyUnsupportedPair(String conversionId, String customerId,
                                     String sourceCurrency, String targetCurrency,
                                     String reason, String correlationId) {

        log.info("Sending unsupported pair notification: conversionId={} customer={} {}→{} correlationId={}",
                conversionId, customerId, sourceCurrency, targetCurrency, correlationId);

        try {
            String subject = "Currency Conversion Not Supported";
            String message = String.format(
                "We're unable to process your currency conversion at this time.\n\n" +
                "Conversion ID: %s\n" +
                "Currency Pair: %s → %s\n" +
                "Reason: %s\n\n" +
                "This currency pair is currently not supported. Our product team has been " +
                "notified and will review adding support for this pair. You will be contacted " +
                "when this currency pair becomes available.",
                conversionId, sourceCurrency, targetCurrency, reason
            );

            notificationService.sendNotification(
                customerId,
                subject,
                message,
                "CURRENCY_CONVERSION_UNSUPPORTED",
                Map.of(
                    "conversionId", conversionId,
                    "sourceCurrency", sourceCurrency,
                    "targetCurrency", targetCurrency,
                    "reason", reason
                ),
                correlationId
            );

            incrementCounter("currency.notification.unsupported.sent");

        } catch (Exception e) {
            log.error("Failed to send unsupported pair notification: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.notification.unsupported.error");
        }
    }

    /**
     * Send failure alert
     */
    @Async
    public void sendFailureAlert(String conversionId, String customerId,
                                String sourceCurrency, String targetCurrency,
                                BigDecimal amount, String failureReason,
                                String correlationId) {

        log.info("Sending failure alert: conversionId={} customer={} reason={} correlationId={}",
                conversionId, customerId, failureReason, correlationId);

        try {
            String subject = "Currency Conversion Failed";
            String message = String.format(
                "We were unable to complete your currency conversion.\n\n" +
                "Conversion ID: %s\n" +
                "Amount: %s %s\n" +
                "Target Currency: %s\n" +
                "Reason: %s\n\n" +
                "Our support team has been notified and will contact you shortly to resolve this issue. " +
                "Any funds held for this conversion will be returned to your account.",
                conversionId, amount, sourceCurrency, targetCurrency, failureReason
            );

            notificationService.sendNotification(
                customerId,
                subject,
                message,
                "CURRENCY_CONVERSION_FAILED",
                Map.of(
                    "conversionId", conversionId,
                    "sourceCurrency", sourceCurrency,
                    "targetCurrency", targetCurrency,
                    "amount", amount.toString(),
                    "failureReason", failureReason
                ),
                correlationId
            );

            incrementCounter("currency.notification.failure.sent");

        } catch (Exception e) {
            log.error("Failed to send failure alert: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.notification.failure.error");
        }
    }

    /**
     * Send rate stale notification
     */
    @Async
    public void notifyRateStale(String conversionId, String customerId,
                               String sourceCurrency, String targetCurrency,
                               int rateAgeSeconds, String correlationId) {

        log.info("Sending rate stale notification: conversionId={} customer={} age={}s correlationId={}",
                conversionId, customerId, rateAgeSeconds, correlationId);

        try {
            String subject = "Currency Conversion Rate Refresh Required";
            String message = String.format(
                "Your currency conversion requires a rate refresh.\n\n" +
                "Conversion ID: %s\n" +
                "Currency Pair: %s → %s\n" +
                "Reason: Exchange rate is outdated (age: %d seconds)\n\n" +
                "We are fetching the latest exchange rate. Your conversion will be processed " +
                "shortly with the most current rate.",
                conversionId, sourceCurrency, targetCurrency, rateAgeSeconds
            );

            notificationService.sendNotification(
                customerId,
                subject,
                message,
                "CURRENCY_CONVERSION_RATE_STALE",
                Map.of(
                    "conversionId", conversionId,
                    "sourceCurrency", sourceCurrency,
                    "targetCurrency", targetCurrency,
                    "rateAgeSeconds", String.valueOf(rateAgeSeconds)
                ),
                correlationId
            );

            incrementCounter("currency.notification.rate_stale.sent");

        } catch (Exception e) {
            log.error("Failed to send rate stale notification: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.notification.rate_stale.error");
        }
    }

    /**
     * Send compliance hold notification
     */
    @Async
    public void notifyComplianceHold(String conversionId, String customerId,
                                    String sourceCurrency, String targetCurrency,
                                    BigDecimal amount, String holdReason,
                                    String correlationId) {

        log.info("Sending compliance hold notification: conversionId={} customer={} reason={} correlationId={}",
                conversionId, customerId, holdReason, correlationId);

        try {
            String subject = "Currency Conversion Under Review";
            String message = String.format(
                "Your currency conversion is being reviewed.\n\n" +
                "Conversion ID: %s\n" +
                "Amount: %s %s\n" +
                "Target Currency: %s\n" +
                "Reason: %s\n\n" +
                "This is a routine compliance review. Our team will complete the review within " +
                "24 hours. You will be notified once the review is complete and your conversion " +
                "is processed.",
                conversionId, amount, sourceCurrency, targetCurrency, holdReason
            );

            notificationService.sendNotification(
                customerId,
                subject,
                message,
                "CURRENCY_CONVERSION_COMPLIANCE_HOLD",
                Map.of(
                    "conversionId", conversionId,
                    "sourceCurrency", sourceCurrency,
                    "targetCurrency", targetCurrency,
                    "amount", amount.toString(),
                    "holdReason", holdReason
                ),
                correlationId
            );

            incrementCounter("currency.notification.compliance_hold.sent");

        } catch (Exception e) {
            log.error("Failed to send compliance hold notification: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.notification.compliance_hold.error");
        }
    }

    /**
     * Send currency conversion confirmation (alias for success notification)
     */
    @Async
    public void sendCurrencyConversionConfirmation(String customerId, String conversionId,
                                                   String fromCurrency, String toCurrency,
                                                   BigDecimal originalAmount, BigDecimal convertedAmount,
                                                   String correlationId) {
        sendConversionSuccessNotification(conversionId, customerId, fromCurrency, toCurrency,
                originalAmount, convertedAmount, BigDecimal.ONE, correlationId);
    }

    /**
     * Send exchange rate unavailable notification
     */
    @Async
    public void sendExchangeRateUnavailableNotification(String customerId, String conversionId,
                                                        String fromCurrency, String toCurrency,
                                                        String correlationId) {
        notifyRetryScheduled(conversionId, customerId, fromCurrency, toCurrency,
                Instant.now().plusSeconds(300), correlationId);
    }

    /**
     * Send unsupported currency pair notification
     */
    @Async
    public void sendUnsupportedCurrencyPairNotification(String customerId, String conversionId,
                                                       String fromCurrency, String toCurrency,
                                                       String correlationId) {
        notifyUnsupportedPair(conversionId, customerId, fromCurrency, toCurrency,
                "Currency pair not supported", correlationId);
    }

    /**
     * Send currency conversion failure notification
     */
    @Async
    public void sendCurrencyConversionFailureNotification(String customerId, String conversionId,
                                                         String failureReason, String correlationId) {
        sendFailureAlert(conversionId, customerId, "UNKNOWN", "UNKNOWN",
                BigDecimal.ZERO, failureReason, correlationId);
    }

    /**
     * Increment counter metric
     */
    private void incrementCounter(String metricName) {
        Counter.builder(metricName)
                .register(meterRegistry)
                .increment();
    }
}
