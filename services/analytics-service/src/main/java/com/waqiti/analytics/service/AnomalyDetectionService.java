package com.waqiti.analytics.service;

import com.waqiti.analytics.domain.TransactionAnalytics;
import com.waqiti.analytics.dto.anomaly.TransactionAnomaly;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public interface AnomalyDetectionService {
    
    /**
     * Detect anomalies for a user within a date range
     */
    List<TransactionAnomaly> detectAnomalies(UUID userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Check for anomalies in real-time for a specific transaction
     */
    void checkForAnomalies(UUID userId, TransactionAnalytics transaction);
    
    /**
     * Get anomaly threshold for a specific user
     */
    Double getAnomalyThreshold(UUID userId);
    
    /**
     * Update anomaly detection model with new data
     */
    void updateAnomalyModel(UUID userId, List<TransactionAnalytics> transactions);
}