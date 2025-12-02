package com.waqiti.analytics.kafka;

import com.waqiti.analytics.event.AnalyticsEvent;
import com.waqiti.analytics.service.AnalyticsService;
import com.waqiti.analytics.service.ReportingService;
import com.waqiti.analytics.service.DataProcessingService;
import com.waqiti.analytics.service.InsightsService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for analytics and reporting events
 * Handles: general-analytics, realtime-analytics, error-analytics, transaction-analytics,
 * executive-reports, compliance-reports, analytics-alerts, analytics-alert-resolutions,
 * scaling-prediction-events, anomaly-detection-events, enhanced-monitoring-events,
 * risk-scoring-events, observability-events, security-analytics, performance-analytics,
 * business-intelligence-events, data-quality-events, ml-model-events, predictive-analytics,
 * usage-analytics
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsReportingConsumer {

    private final AnalyticsService analyticsService;
    private final ReportingService reportingService;
    private final DataProcessingService dataProcessingService;
    private final InsightsService insightsService;
    private final UniversalDLQHandler universalDLQHandler;

    @KafkaListener(topics = {"general-analytics", "realtime-analytics", "error-analytics", "transaction-analytics",
                             "executive-reports", "compliance-reports", "analytics-alerts", "analytics-alert-resolutions",
                             "scaling-prediction-events", "anomaly-detection-events", "enhanced-monitoring-events",
                             "risk-scoring-events", "observability-events", "security-analytics", "performance-analytics",
                             "business-intelligence-events", "data-quality-events", "ml-model-events", "predictive-analytics",
                             "usage-analytics"}, 
                   groupId = "analytics-reporting-processor")
    @Transactional
    public void processAnalyticsEvent(@Payload AnalyticsEvent event,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset,
                                    Acknowledgment acknowledgment) {
        try {
            log.info("Processing analytics event: {} - Type: {} - Source: {} - Metric: {}", 
                    event.getEventId(), event.getEventType(), event.getSource(), event.getMetricName());
            
            // Process based on topic
            switch (topic) {
                case "general-analytics" -> handleGeneralAnalytics(event);
                case "realtime-analytics" -> handleRealtimeAnalytics(event);
                case "error-analytics" -> handleErrorAnalytics(event);
                case "transaction-analytics" -> handleTransactionAnalytics(event);
                case "executive-reports" -> handleExecutiveReports(event);
                case "compliance-reports" -> handleComplianceReports(event);
                case "analytics-alerts" -> handleAnalyticsAlerts(event);
                case "analytics-alert-resolutions" -> handleAlertResolutions(event);
                case "scaling-prediction-events" -> handleScalingPredictions(event);
                case "anomaly-detection-events" -> handleAnomalyDetection(event);
                case "enhanced-monitoring-events" -> handleEnhancedMonitoring(event);
                case "risk-scoring-events" -> handleRiskScoring(event);
                case "observability-events" -> handleObservability(event);
                case "security-analytics" -> handleSecurityAnalytics(event);
                case "performance-analytics" -> handlePerformanceAnalytics(event);
                case "business-intelligence-events" -> handleBusinessIntelligence(event);
                case "data-quality-events" -> handleDataQuality(event);
                case "ml-model-events" -> handleMlModelEvents(event);
                case "predictive-analytics" -> handlePredictiveAnalytics(event);
                case "usage-analytics" -> handleUsageAnalytics(event);
            }
            
            // Update analytics metadata
            updateAnalyticsMetadata(event);
            
            // Acknowledge
            acknowledgment.acknowledge();
            
            log.info("Successfully processed analytics event: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to process analytics event {}: {}",
                    event.getEventId(), e.getMessage(), e);

            // Send to DLQ via UniversalDLQHandler
            try {
                // Need to create a ConsumerRecord for the DLQ handler
                // Since we receive AnalyticsEvent directly, we need to serialize it
                String eventJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event);
                org.apache.kafka.clients.consumer.ConsumerRecord<String, String> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        topic, partition, offset, event.getEventId(), eventJson
                    );
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqException) {
                log.error("Failed to send message to DLQ: {}", dlqException.getMessage());
            }

            throw new RuntimeException("Analytics processing failed", e);
        }
    }

    private void handleGeneralAnalytics(AnalyticsEvent event) {
        // Process general analytics
        String metricType = event.getMetricType();
        
        switch (metricType) {
            case "USER_ENGAGEMENT" -> {
                analyticsService.processUserEngagement(
                    event.getUserId(),
                    event.getEngagementType(),
                    event.getEngagementValue(),
                    event.getSessionId()
                );
            }
            case "FEATURE_USAGE" -> {
                analyticsService.processFeatureUsage(
                    event.getFeatureName(),
                    event.getUserId(),
                    event.getUsageCount(),
                    event.getUsageDuration()
                );
            }
            case "CONVERSION_FUNNEL" -> {
                analyticsService.processConversionStep(
                    event.getFunnelId(),
                    event.getStepName(),
                    event.getUserId(),
                    event.getStepResult()
                );
            }
            case "RETENTION_METRICS" -> {
                analyticsService.processRetentionMetric(
                    event.getCohortId(),
                    event.getRetentionPeriod(),
                    event.getRetentionRate(),
                    event.getUserCount()
                );
            }
            case "REVENUE_METRICS" -> {
                analyticsService.processRevenueMetric(
                    event.getRevenueType(),
                    event.getRevenueAmount(),
                    event.getCurrency(),
                    event.getTimePeriod()
                );
            }
        }
        
        // Store aggregated metrics
        analyticsService.storeAggregatedMetrics(
            event.getMetricType(),
            event.getDimensions(),
            event.getValue(),
            event.getTimestamp()
        );
    }

    private void handleRealtimeAnalytics(AnalyticsEvent event) {
        // Process real-time analytics with immediate processing
        CompletableFuture.runAsync(() -> {
            try {
                // Process streaming data
                analyticsService.processStreamingData(
                    event.getStreamId(),
                    event.getDataPoints(),
                    event.getProcessingRules()
                );
                
                // Update real-time dashboards
                analyticsService.updateRealtimeDashboard(
                    event.getDashboardId(),
                    event.getMetricName(),
                    event.getValue(),
                    LocalDateTime.now()
                );
                
                // Check for real-time alerts
                if (analyticsService.checkAlertThresholds(event.getMetricName(), event.getValue())) {
                    analyticsService.triggerRealtimeAlert(
                        event.getMetricName(),
                        event.getValue(),
                        event.getThreshold()
                    );
                }
                
            } catch (Exception e) {
                log.error("Real-time analytics processing failed: {}", e.getMessage());
                analyticsService.logRealtimeError(event.getEventId(), e.getMessage());
            }
        });
        
        // Update streaming metrics
        analyticsService.updateStreamingMetrics(
            event.getMetricName(),
            event.getValue(),
            event.getLatency()
        );
    }

    private void handleErrorAnalytics(AnalyticsEvent event) {
        // Process error analytics
        String errorType = event.getErrorType();
        
        // Categorize error
        String category = analyticsService.categorizeError(
            errorType,
            event.getErrorMessage(),
            event.getStackTrace()
        );
        
        // Store error details
        analyticsService.storeErrorAnalytics(
            event.getServiceName(),
            errorType,
            category,
            event.getErrorFrequency(),
            event.getImpactScore(),
            event.getTimestamp()
        );
        
        // Analyze error patterns
        Map<String, Object> patterns = analyticsService.analyzeErrorPatterns(
            event.getServiceName(),
            event.getTimeWindow(),
            event.getErrorThreshold()
        );
        
        // Generate error insights
        if ((Boolean) patterns.getOrDefault("hasPattern", false)) {
            insightsService.generateErrorInsight(
                event.getServiceName(),
                patterns,
                event.getRecommendations()
            );
        }
        
        // Update error tracking metrics
        analyticsService.updateErrorMetrics(
            event.getServiceName(),
            errorType,
            event.getErrorCount(),
            event.getResolutionTime()
        );
    }

    private void handleTransactionAnalytics(AnalyticsEvent event) {
        // Process transaction analytics
        String transactionType = event.getTransactionType();
        
        switch (transactionType) {
            case "PAYMENT_FLOW" -> {
                analyticsService.analyzePaymentFlow(
                    event.getPaymentId(),
                    event.getFlowSteps(),
                    event.getProcessingTime(),
                    event.getSuccessRate()
                );
            }
            case "FRAUD_DETECTION" -> {
                analyticsService.analyzeFraudMetrics(
                    event.getFraudScore(),
                    event.getFraudType(),
                    event.getDetectionAccuracy(),
                    event.getFalsePositiveRate()
                );
            }
            case "SETTLEMENT_ANALYSIS" -> {
                analyticsService.analyzeSettlementMetrics(
                    event.getSettlementId(),
                    event.getSettlementTime(),
                    event.getSettlementAmount(),
                    event.getFeeStructure()
                );
            }
            case "VOLUME_ANALYSIS" -> {
                analyticsService.analyzeTransactionVolume(
                    event.getVolumePeriod(),
                    event.getTransactionCount(),
                    event.getTotalAmount(),
                    event.getVolumeGrowth()
                );
            }
        }
        
        // Generate transaction insights
        insightsService.generateTransactionInsights(
            event.getTransactionType(),
            event.getAnalysisResults(),
            event.getTrendData()
        );
    }

    private void handleExecutiveReports(AnalyticsEvent event) {
        // Generate executive reports
        String reportType = event.getReportType();
        
        switch (reportType) {
            case "MONTHLY_SUMMARY" -> {
                String reportId = reportingService.generateMonthlySummary(
                    event.getReportPeriod(),
                    event.getKpis(),
                    event.getExecutiveMetrics()
                );
                
                // Distribute to executives
                reportingService.distributeExecutiveReport(
                    reportId,
                    event.getExecutiveList(),
                    event.getDistributionMethod()
                );
            }
            case "QUARTERLY_REVIEW" -> {
                String reportId = reportingService.generateQuarterlyReview(
                    event.getQuarter(),
                    event.getYear(),
                    event.getPerformanceMetrics(),
                    event.getGoalComparisons()
                );
                
                // Schedule board presentation
                reportingService.scheduleBoardPresentation(
                    reportId,
                    event.getPresentationDate(),
                    event.getBoardMembers()
                );
            }
            case "REAL_TIME_DASHBOARD" -> {
                reportingService.updateExecutiveDashboard(
                    event.getDashboardId(),
                    event.getKpis(),
                    event.getAlerts(),
                    LocalDateTime.now()
                );
            }
            case "FINANCIAL_SUMMARY" -> {
                reportingService.generateFinancialSummary(
                    event.getReportPeriod(),
                    event.getRevenueMetrics(),
                    event.getCostMetrics(),
                    event.getProfitabilityAnalysis()
                );
            }
        }
    }

    private void handleComplianceReports(AnalyticsEvent event) {
        // Generate compliance reports
        String complianceType = event.getComplianceType();
        
        switch (complianceType) {
            case "AML_REPORT" -> {
                String reportId = reportingService.generateAmlReport(
                    event.getReportPeriod(),
                    event.getSuspiciousActivities(),
                    event.getSarFilings(),
                    event.getAmlMetrics()
                );
                
                // Submit to regulators
                reportingService.submitRegulatoryReport(
                    reportId,
                    event.getRegulatoryBody(),
                    event.getSubmissionDeadline()
                );
            }
            case "KYC_COMPLIANCE" -> {
                reportingService.generateKycComplianceReport(
                    event.getReportPeriod(),
                    event.getKycStatistics(),
                    event.getComplianceRate(),
                    event.getExceptions()
                );
            }
            case "TRANSACTION_MONITORING" -> {
                reportingService.generateTransactionMonitoringReport(
                    event.getMonitoringPeriod(),
                    event.getMonitoredTransactions(),
                    event.getAlerts(),
                    event.getFalsePositives()
                );
            }
            case "AUDIT_TRAIL" -> {
                reportingService.generateAuditTrailReport(
                    event.getAuditPeriod(),
                    event.getAuditEvents(),
                    event.getAccessLogs(),
                    event.getComplianceFindings()
                );
            }
        }
        
        // Store compliance metrics
        analyticsService.storeComplianceMetrics(
            event.getComplianceType(),
            event.getComplianceScore(),
            event.getViolationCount(),
            event.getRemediationStatus()
        );
    }

    private void handleAnalyticsAlerts(AnalyticsEvent event) {
        // Process analytics alerts
        String alertType = event.getAlertType();
        
        // Evaluate alert conditions
        boolean shouldTrigger = analyticsService.evaluateAlertConditions(
            alertType,
            event.getThreshold(),
            event.getCurrentValue(),
            event.getAlertRules()
        );
        
        if (shouldTrigger) {
            // Create alert
            String alertId = analyticsService.createAlert(
                alertType,
                event.getSeverity(),
                event.getAlertMessage(),
                event.getAffectedMetrics()
            );
            
            // Send notifications
            analyticsService.sendAlertNotifications(
                alertId,
                event.getNotificationTargets(),
                event.getEscalationPath()
            );
            
            // Auto-remediation if configured
            if (event.hasAutoRemediation()) {
                analyticsService.executeAutoRemediation(
                    alertId,
                    event.getRemediationActions()
                );
            }
        }
        
        // Update alert statistics
        analyticsService.updateAlertStatistics(
            alertType,
            shouldTrigger,
            event.getResponseTime()
        );
    }

    private void handleAlertResolutions(AnalyticsEvent event) {
        // Handle alert resolutions
        String alertId = event.getAlertId();
        
        // Record resolution
        analyticsService.recordAlertResolution(
            alertId,
            event.getResolutionType(),
            event.getResolutionTime(),
            event.getResolvedBy(),
            event.getResolutionNotes()
        );
        
        // Analyze resolution effectiveness
        Map<String, Object> effectiveness = analyticsService.analyzeResolutionEffectiveness(
            alertId,
            event.getResolutionType(),
            event.getResolutionTime()
        );
        
        // Update alert learning model
        analyticsService.updateAlertModel(
            event.getAlertType(),
            effectiveness,
            event.getFeedback()
        );
        
        // Generate improvement recommendations
        if (event.shouldGenerateRecommendations()) {
            insightsService.generateAlertImprovements(
                event.getAlertType(),
                effectiveness,
                event.getHistoricalData()
            );
        }
    }

    private void handleScalingPredictions(AnalyticsEvent event) {
        // Process scaling predictions
        Map<String, Object> scalingData = event.getScalingData();
        
        // Analyze current load patterns
        Map<String, Object> loadAnalysis = analyticsService.analyzeLoadPatterns(
            event.getServiceName(),
            event.getLoadMetrics(),
            event.getHistoricalTrends()
        );
        
        // Generate scaling predictions
        Map<String, Object> predictions = analyticsService.generateScalingPredictions(
            event.getServiceName(),
            loadAnalysis,
            event.getPredictionHorizon()
        );
        
        // Store predictions
        analyticsService.storeScalingPredictions(
            event.getServiceName(),
            predictions,
            event.getConfidenceLevel(),
            LocalDateTime.now()
        );
        
        // Trigger auto-scaling if needed
        if (event.isAutoScalingEnabled()) {
            Double urgencyScore = (Double) predictions.get("urgencyScore");
            if (urgencyScore != null && urgencyScore > event.getAutoScalingThreshold()) {
                analyticsService.triggerAutoScaling(
                    event.getServiceName(),
                    predictions,
                    event.getScalingParams()
                );
            }
        }
    }

    private void handleAnomalyDetection(AnalyticsEvent event) {
        // Process anomaly detection
        String anomalyType = event.getAnomalyType();
        
        // Run anomaly detection algorithms
        Map<String, Object> anomalyResult = analyticsService.detectAnomalies(
            event.getDataSeries(),
            event.getDetectionAlgorithm(),
            event.getSensitivityLevel()
        );
        
        boolean hasAnomaly = (Boolean) anomalyResult.get("hasAnomaly");
        
        if (hasAnomaly) {
            // Record anomaly
            String anomalyId = analyticsService.recordAnomaly(
                anomalyType,
                event.getMetricName(),
                anomalyResult,
                event.getTimestamp()
            );
            
            // Analyze anomaly impact
            Map<String, Object> impact = analyticsService.analyzeAnomalyImpact(
                anomalyId,
                event.getAffectedSystems(),
                event.getBusinessContext()
            );
            
            // Generate anomaly insights
            insightsService.generateAnomalyInsights(
                anomalyId,
                anomalyResult,
                impact
            );
            
            // Auto-investigate if configured
            if (event.isAutoInvestigationEnabled()) {
                analyticsService.initiateAutoInvestigation(
                    anomalyId,
                    event.getInvestigationRules()
                );
            }
        }
        
        // Update anomaly detection models
        analyticsService.updateAnomalyModels(
            anomalyType,
            event.getDataSeries(),
            anomalyResult
        );
    }

    private void handleEnhancedMonitoring(AnalyticsEvent event) {
        // Process enhanced monitoring events
        String monitoringType = event.getMonitoringType();
        
        // Apply enhanced monitoring
        analyticsService.applyEnhancedMonitoring(
            event.getEntityId(),
            monitoringType,
            event.getMonitoringLevel(),
            event.getMonitoringDuration()
        );
        
        // Configure monitoring rules
        analyticsService.configureMonitoringRules(
            event.getEntityId(),
            event.getMonitoringRules(),
            event.getAlertThresholds()
        );
        
        // Set up automated responses
        if (event.hasAutomatedResponses()) {
            analyticsService.configureAutomatedResponses(
                event.getEntityId(),
                event.getResponseTriggers(),
                event.getResponseActions()
            );
        }
        
        // Schedule monitoring review
        analyticsService.scheduleMonitoringReview(
            event.getEntityId(),
            event.getReviewInterval(),
            event.getReviewCriteria()
        );
    }

    private void handleRiskScoring(AnalyticsEvent event) {
        // Process risk scoring events
        String entityId = event.getEntityId();
        
        // Calculate risk score
        Map<String, Object> riskAssessment = analyticsService.calculateRiskScore(
            entityId,
            event.getRiskFactors(),
            event.getRiskWeights(),
            event.getRiskModel()
        );
        
        // Store risk score
        analyticsService.storeRiskScore(
            entityId,
            riskAssessment,
            event.getTimestamp()
        );
        
        // Check risk thresholds
        Double riskScore = (Double) riskAssessment.get("score");
        if (riskScore > event.getHighRiskThreshold()) {
            // High risk detected
            analyticsService.handleHighRiskEntity(
                entityId,
                riskScore,
                event.getRiskMitigationActions()
            );
        } else if (riskScore > event.getMediumRiskThreshold()) {
            // Medium risk
            analyticsService.handleMediumRiskEntity(
                entityId,
                riskScore,
                event.getMonitoringActions()
            );
        }
        
        // Update risk models
        analyticsService.updateRiskModels(
            event.getRiskModel(),
            event.getRiskFactors(),
            riskAssessment
        );
    }

    private void handleObservability(AnalyticsEvent event) {
        // Process observability events
        String observabilityType = event.getObservabilityType();
        
        switch (observabilityType) {
            case "TRACE_ANALYSIS" -> {
                analyticsService.analyzeTraces(
                    event.getTraceData(),
                    event.getLatencyThresholds(),
                    event.getErrorRates()
                );
            }
            case "METRIC_CORRELATION" -> {
                analyticsService.correlateMetrics(
                    event.getMetricSets(),
                    event.getCorrelationWindow(),
                    event.getCorrelationThreshold()
                );
            }
            case "LOG_ANALYSIS" -> {
                analyticsService.analyzeLogs(
                    event.getLogData(),
                    event.getLogPatterns(),
                    event.getAnomalyDetection()
                );
            }
            case "SERVICE_MAP" -> {
                analyticsService.updateServiceMap(
                    event.getServiceDependencies(),
                    event.getHealthStatus(),
                    event.getPerformanceMetrics()
                );
            }
        }
        
        // Generate observability insights
        insightsService.generateObservabilityInsights(
            observabilityType,
            event.getAnalysisResults(),
            event.getRecommendations()
        );
    }

    private void handleSecurityAnalytics(AnalyticsEvent event) {
        // Process security analytics
        String securityEventType = event.getSecurityEventType();
        
        // Analyze security event
        Map<String, Object> securityAnalysis = analyticsService.analyzeSecurityEvent(
            securityEventType,
            event.getSecurityData(),
            event.getThreatIntelligence()
        );
        
        // Calculate threat score
        Double threatScore = analyticsService.calculateThreatScore(
            securityAnalysis,
            event.getThreatModel()
        );
        
        // Store security metrics
        analyticsService.storeSecurityMetrics(
            securityEventType,
            threatScore,
            event.getSecurityContext(),
            event.getTimestamp()
        );
        
        // Generate security insights
        insightsService.generateSecurityInsights(
            securityEventType,
            securityAnalysis,
            event.getActionRecommendations()
        );
    }

    private void handlePerformanceAnalytics(AnalyticsEvent event) {
        // Process performance analytics
        analyticsService.analyzePerformance(
            event.getServiceName(),
            event.getPerformanceMetrics(),
            event.getPerformanceBaselines(),
            event.getPerformanceGoals()
        );
        
        // Generate performance insights
        insightsService.generatePerformanceInsights(
            event.getServiceName(),
            event.getPerformanceAnalysis(),
            event.getOptimizationSuggestions()
        );
    }

    private void handleBusinessIntelligence(AnalyticsEvent event) {
        // Process business intelligence events
        dataProcessingService.processBiEvent(
            event.getBiEventType(),
            event.getBusinessData(),
            event.getAnalyticalModels()
        );
    }

    private void handleDataQuality(AnalyticsEvent event) {
        // Process data quality events
        analyticsService.assessDataQuality(
            event.getDataSource(),
            event.getQualityMetrics(),
            event.getQualityRules()
        );
    }

    private void handleMlModelEvents(AnalyticsEvent event) {
        // Process ML model events
        analyticsService.processMlModelEvent(
            event.getModelId(),
            event.getModelMetrics(),
            event.getModelPerformance()
        );
    }

    private void handlePredictiveAnalytics(AnalyticsEvent event) {
        // Process predictive analytics
        analyticsService.processPredictiveAnalytics(
            event.getPredictionType(),
            event.getPredictionData(),
            event.getPredictionModel()
        );
    }

    private void handleUsageAnalytics(AnalyticsEvent event) {
        // Process usage analytics
        analyticsService.processUsageAnalytics(
            event.getUsageType(),
            event.getUsageMetrics(),
            event.getUserSegments()
        );
    }

    private void updateAnalyticsMetadata(AnalyticsEvent event) {
        // Update analytics metadata
        analyticsService.updateMetadata(
            event.getEventType(),
            event.getSource(),
            event.getProcessingTime(),
            event.getDataVolume()
        );
    }
}