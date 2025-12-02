package com.waqiti.currency.service;

import com.waqiti.currency.model.AlertType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Treasury Team Alert Service
 *
 * Sends alerts to treasury operations team for currency-related issues
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TreasuryTeamAlertService {

    private final MeterRegistry meterRegistry;

    /**
     * Send alert to treasury team
     */
    @Async
    public void sendAlert(AlertType alertType, String message, Map<String, String> metadata,
                         String correlationId) {

        log.error("Sending treasury team alert: alertType={} correlationId={}", alertType, correlationId);

        try {
            // Send via email
            sendEmailAlert(alertType, message, metadata, correlationId);

            // Send via Slack for urgent alerts
            if (isUrgent(alertType)) {
                sendSlackAlert(alertType, message, metadata, correlationId);
            }

            // Send via PagerDuty for critical alerts
            if (isCritical(alertType)) {
                sendPagerDutyAlert(alertType, message, metadata, correlationId);
            }

            Counter.builder("treasury.alert.sent")
                    .tag("alertType", alertType.name())
                    .tag("urgent", String.valueOf(isUrgent(alertType)))
                    .register(meterRegistry)
                    .increment();

            log.error("Treasury team alert sent successfully: alertType={} correlationId={}", alertType, correlationId);

        } catch (Exception e) {
            log.error("Failed to send treasury team alert: alertType={} correlationId={}", alertType, correlationId, e);

            Counter.builder("treasury.alert.error")
                    .tag("alertType", alertType.name())
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * Send email alert
     */
    private void sendEmailAlert(AlertType alertType, String message, Map<String, String> metadata,
                               String correlationId) {
        log.debug("Sending treasury email alert: alertType={} correlationId={}", alertType, correlationId);
        // In production: Send via email service
    }

    /**
     * Send Slack alert
     */
    private void sendSlackAlert(AlertType alertType, String message, Map<String, String> metadata,
                               String correlationId) {
        log.error("Sending treasury Slack alert: alertType={} correlationId={}", alertType, correlationId);
        // In production: Send via Slack webhook
    }

    /**
     * Send PagerDuty alert
     */
    private void sendPagerDutyAlert(AlertType alertType, String message, Map<String, String> metadata,
                                   String correlationId) {
        log.error("PAGING TREASURY ON-CALL: alertType={} correlationId={}", alertType, correlationId);
        // In production: Trigger PagerDuty incident
    }

    /**
     * Check if alert is urgent
     */
    private boolean isUrgent(AlertType alertType) {
        return alertType == AlertType.CURRENCY_CONVERSION_PERMANENT_FAILURE ||
               alertType == AlertType.HIGH_VALUE_CONVERSION_FAILURE;
    }

    /**
     * Check if alert is critical
     */
    private boolean isCritical(AlertType alertType) {
        return alertType == AlertType.HIGH_VALUE_CONVERSION_FAILURE ||
               alertType == AlertType.EXCHANGE_RATE_SYSTEM_FAILURE;
    }
}
