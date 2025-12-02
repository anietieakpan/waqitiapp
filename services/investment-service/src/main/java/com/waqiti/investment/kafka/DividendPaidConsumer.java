package com.waqiti.investment.kafka;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.investment.service.InvestmentNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL FIX #25: DividendPaidConsumer
 * Notifies investors when dividends are paid to their accounts
 * Impact: Improves investor satisfaction and transparency
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DividendPaidConsumer {
    private final InvestmentNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "investment.dividend.paid", groupId = "investment-dividend-notification")
    public void handle(DividendPaidEvent event, Acknowledgment ack) {
        try {
            log.info("💵 DIVIDEND PAID: userId={}, symbol={}, amount=${}, shares={}",
                event.getUserId(), event.getSymbol(), event.getDividendAmount(), event.getShareCount());

            String key = "dividend:paid:" + event.getDividendId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                Dividend Payment Received

                You've received a dividend payment!

                Security Details:
                - Symbol: %s
                - Company: %s
                - Shares Owned: %s
                - Dividend Per Share: $%s
                - Total Dividend: $%s

                Payment Details:
                - Ex-Dividend Date: %s
                - Payment Date: %s
                - Dividend Type: %s
                - Dividend Frequency: %s

                Tax Information:
                - Qualified Dividend: %s
                - Tax Withholding: $%s
                - Net Amount: $%s

                This dividend has been %s

                Year-to-Date Summary:
                - Total Dividends from %s: $%s
                - Total Dividends (All Holdings): $%s

                Tax Reporting:
                This dividend will be reported on your year-end Form 1099-DIV.
                %s

                Reinvestment:
                %s

                View Your Portfolio:
                https://example.com/investments/portfolio

                Questions? Contact investment support:
                Email: investments@example.com
                Phone: 1-800-WAQITI-INV
                """,
                event.getSymbol(),
                event.getCompanyName(),
                formatShares(event.getShareCount()),
                event.getDividendPerShare(),
                event.getDividendAmount(),
                event.getExDividendDate().toLocalDate(),
                event.getPaymentDate().toLocalDate(),
                event.getDividendType(),
                event.getDividendFrequency(),
                event.isQualifiedDividend() ? "Yes (taxed at lower capital gains rate)" : "No (taxed as ordinary income)",
                event.getTaxWithholding(),
                event.getNetAmount(),
                event.isReinvested()
                    ? String.format("automatically reinvested, purchasing %s additional shares at $%s/share",
                        formatShares(event.getReinvestedShares()), event.getReinvestmentPrice())
                    : "deposited to your investment account",
                event.getCompanyName(),
                event.getYtdDividendsThisSecurity(),
                event.getYtdTotalDividends(),
                event.isQualifiedDividend()
                    ? "Qualified dividends are taxed at preferential long-term capital gains rates (0%, 15%, or 20%)."
                    : "Non-qualified dividends are taxed as ordinary income at your marginal tax rate.",
                event.isReinvested()
                    ? "✅ Dividend Reinvestment Plan (DRIP) is enabled for this security."
                    : "To enable automatic dividend reinvestment, visit: Settings > Investments > DRIP");

            notificationService.sendDividendPaidNotification(
                event.getUserId(), event.getDividendId(), event.getSymbol(),
                event.getDividendAmount(), message);

            metricsCollector.incrementCounter("investment.dividend.paid.notification.sent");
            metricsCollector.recordGauge("investment.dividend.amount", event.getDividendAmount().doubleValue());
            if (event.isReinvested()) {
                metricsCollector.incrementCounter("investment.dividend.reinvested");
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process dividend paid event", e);
            dlqHandler.sendToDLQ("investment.dividend.paid", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private String formatShares(BigDecimal shares) {
        if (shares.stripTrailingZeros().scale() <= 0) {
            return shares.toBigInteger().toString();
        }
        return shares.stripTrailingZeros().toPlainString();
    }

    private static class DividendPaidEvent {
        private UUID userId, dividendId, positionId;
        private String symbol, companyName, dividendType, dividendFrequency;
        private BigDecimal dividendAmount, dividendPerShare, shareCount;
        private BigDecimal taxWithholding, netAmount;
        private BigDecimal ytdDividendsThisSecurity, ytdTotalDividends;
        private LocalDateTime exDividendDate, paymentDate;
        private boolean qualifiedDividend, reinvested;
        private BigDecimal reinvestedShares, reinvestmentPrice;

        public UUID getUserId() { return userId; }
        public UUID getDividendId() { return dividendId; }
        public UUID getPositionId() { return positionId; }
        public String getSymbol() { return symbol; }
        public String getCompanyName() { return companyName; }
        public String getDividendType() { return dividendType; }
        public String getDividendFrequency() { return dividendFrequency; }
        public BigDecimal getDividendAmount() { return dividendAmount; }
        public BigDecimal getDividendPerShare() { return dividendPerShare; }
        public BigDecimal getShareCount() { return shareCount; }
        public BigDecimal getTaxWithholding() { return taxWithholding; }
        public BigDecimal getNetAmount() { return netAmount; }
        public BigDecimal getYtdDividendsThisSecurity() { return ytdDividendsThisSecurity; }
        public BigDecimal getYtdTotalDividends() { return ytdTotalDividends; }
        public LocalDateTime getExDividendDate() { return exDividendDate; }
        public LocalDateTime getPaymentDate() { return paymentDate; }
        public boolean isQualifiedDividend() { return qualifiedDividend; }
        public boolean isReinvested() { return reinvested; }
        public BigDecimal getReinvestedShares() { return reinvestedShares; }
        public BigDecimal getReinvestmentPrice() { return reinvestmentPrice; }
    }
}
