package com.waqiti.arpayment.integration;

import com.waqiti.arpayment.domain.ARSession;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * AR Fraud Detection Service
 * Behavioral analysis and anomaly detection for AR payments
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ARFraudDetectionService {

    private final MeterRegistry meterRegistry;
    private final Map<String, Integer> userSessionCounts = new ConcurrentHashMap<>();

    public float analyzeBehavioralRisk(ARSession session) {
        log.debug("Analyzing behavioral risk for session: {}", session.getSessionToken());

        float riskScore = 0.0f;

        // Check for suspicious session patterns
        int sessionCount = userSessionCounts.merge(session.getUserId().toString(), 1, Integer::sum);
        if (sessionCount > 10) riskScore += 0.2f; // Multiple sessions in short time

        // Check device anomalies
        if (session.getDeviceId() == null) riskScore += 0.3f;

        // Check location anomalies
        if (session.getCurrentLocationLat() == null) riskScore += 0.1f;

        meterRegistry.gauge("ar.fraud.risk_score", riskScore);

        return Math.min(riskScore, 1.0f);
    }

    public float analyzeDeviceIntegrity(ARSession session) {
        // Check device integrity indicators
        float integrityScore = 0.95f;

        if (!session.getDeviceCapabilities().contains("SECURE_ENCLAVE")) {
            integrityScore -= 0.1f;
        }

        return Math.max(integrityScore, 0.0f);
    }
}
