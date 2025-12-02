package com.waqiti.payment.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Email Statistics model
 *
 * @author Waqiti Platform Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailStatistics {
    private String templateName;
    private AtomicInteger sentCount;
    private AtomicInteger failedCount;
    private LocalDateTime firstSent;
    private LocalDateTime lastSent;
    private LocalDateTime lastFailed;
    private String lastError;

    public EmailStatistics(String templateName) {
        this.templateName = templateName;
        this.sentCount = new AtomicInteger(0);
        this.failedCount = new AtomicInteger(0);
    }

    public void incrementSent() {
        sentCount.incrementAndGet();
        lastSent = LocalDateTime.now();
        if (firstSent == null) {
            firstSent = lastSent;
        }
    }

    public void incrementFailed(String error) {
        failedCount.incrementAndGet();
        lastFailed = LocalDateTime.now();
        lastError = error;
    }
}
