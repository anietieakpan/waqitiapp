package com.waqiti.security.service;

import com.waqiti.security.model.AuthenticationEvent;
import com.waqiti.security.model.TimingAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Timing Analysis Service
 * Analyzes authentication timing patterns
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimingAnalysisService {

    /**
     * Analyze authentication timing
     */
    public TimingAnalysisResult analyzeTiming(AuthenticationEvent event) {
        try {
            int hour = ZonedDateTime.ofInstant(
                event.getTimestamp(),
                java.time.ZoneId.systemDefault()
            ).getHour();

            List<String> anomalies = new ArrayList<>();
            int riskScore = 0;

            // Unusual hours (2 AM - 6 AM)
            if (hour >= 2 && hour <= 6) {
                anomalies.add("UNUSUAL_HOUR");
                riskScore += 20;
            }

            // Weekend login (if applicable)
            int dayOfWeek = ZonedDateTime.ofInstant(
                event.getTimestamp(),
                java.time.ZoneId.systemDefault()
            ).getDayOfWeek().getValue();

            if (dayOfWeek >= 6) { // Saturday or Sunday
                anomalies.add("WEEKEND_LOGIN");
                riskScore += 10;
            }

            // Time to complete
            if (event.getTimeToComplete() != null) {
                if (event.getTimeToComplete() < 1000) { // Less than 1 second
                    anomalies.add("SUSPICIOUSLY_FAST");
                    riskScore += 30;
                }
            }

            return TimingAnalysisResult.builder()
                .loginHour(hour)
                .isUnusualTime(!anomalies.isEmpty())
                .timingAnomalies(anomalies)
                .riskScore(riskScore)
                .build();

        } catch (Exception e) {
            log.error("Error analyzing timing: {}", e.getMessage(), e);
            return TimingAnalysisResult.builder()
                .loginHour(0)
                .isUnusualTime(false)
                .timingAnomalies(new ArrayList<>())
                .riskScore(0)
                .build();
        }
    }
}
