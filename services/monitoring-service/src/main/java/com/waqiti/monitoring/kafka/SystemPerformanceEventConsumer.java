package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.monitoring.service.ComprehensiveMonitoringService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.TracingService;
import com.waqiti.monitoring.alerting.SLAMonitoringService;
import com.waqiti.monitoring.entity.PerformanceMetric;
import com.waqiti.monitoring.entity.SystemAlert;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Critical Event Consumer #250: System Performance Event Consumer
 * Processes performance metrics, SLA monitoring, and system health tracking
 * Implements 12-step zero-tolerance processing for system performance monitoring
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemPerformanceEventConsumer extends BaseKafkaConsumer {

    private final ComprehensiveMonitoringService monitoringService;
    private final AlertingService alertingService;
    private final TracingService tracingService;
    private final SLAMonitoringService slaService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "system-performance-events", groupId = "system-performance-group")
    @CircuitBreaker(name = "system-performance-consumer")
    @Retry(name = "system-performance-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleSystemPerformanceEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "system-performance-event");
        
        try {
            log.info("Step 1: Processing system performance event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String serviceName = eventData.path("serviceName").asText();
            String metricType = eventData.path("metricType").asText();
            BigDecimal metricValue = new BigDecimal(eventData.path("metricValue").asText());
            String unit = eventData.path("unit").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            String instanceId = eventData.path("instanceId").asText();
            String environment = eventData.path("environment").asText();
            
            log.info("Step 2: Extracted performance details: service={}, metric={}, value={} {}, instance={}", 
                    serviceName, metricType, metricValue, unit, instanceId);
            
            // Step 3: Metric validation and baseline comparison
            log.info("Step 3: Validating metrics and comparing against performance baselines");
            PerformanceMetric performanceMetric = monitoringService.createPerformanceMetric(eventData);
            
            BigDecimal baseline = monitoringService.getPerformanceBaseline(serviceName, metricType);
            BigDecimal deviation = monitoringService.calculateDeviation(metricValue, baseline);
            
            if (monitoringService.exceedsThreshold(deviation, metricType)) {
                alertingService.flagPerformanceDegradation(serviceName, performanceMetric);
            }
            
            // Step 4: SLA compliance monitoring
            log.info("Step 4: Monitoring SLA compliance and service level objectives");
            boolean slaCompliant = slaService.checkSLACompliance(serviceName, metricType, metricValue);
            if (!slaCompliant) {
                SystemAlert slaAlert = slaService.generateSLAViolationAlert(serviceName, performanceMetric);
                alertingService.escalateSLAViolation(slaAlert);
                slaService.updateSLAStatus(serviceName, "VIOLATED");
            }
            
            slaService.updateSLAMetrics(serviceName, performanceMetric);
            slaService.calculateAvailabilityPercentage(serviceName, timestamp);
            
            // Step 5: Performance trend analysis
            log.info("Step 5: Conducting performance trend analysis and capacity planning");
            monitoringService.updatePerformanceTrends(serviceName, performanceMetric);
            monitoringService.detectPerformanceAnomalies(serviceName, performanceMetric);
            
            if (monitoringService.detectDegradationTrend(serviceName, metricType)) {
                monitoringService.triggerCapacityPlanningAlert(serviceName, metricType);
                monitoringService.recommendScalingAction(serviceName, metricType);
            }
            
            // Step 6: Resource utilization and bottleneck identification
            log.info("Step 6: Analyzing resource utilization and identifying system bottlenecks");
            if ("CPU_UTILIZATION".equals(metricType) && metricValue.compareTo(new BigDecimal("80")) > 0) {
                monitoringService.flagCPUBottleneck(serviceName, instanceId, metricValue);
                monitoringService.recommendCPUOptimization(serviceName, instanceId);
            }
            
            if ("MEMORY_UTILIZATION".equals(metricType) && metricValue.compareTo(new BigDecimal("85")) > 0) {
                monitoringService.flagMemoryBottleneck(serviceName, instanceId, metricValue);
                monitoringService.initiateMemoryAnalysis(serviceName, instanceId);
            }
            
            monitoringService.updateResourceUtilizationMetrics(serviceName, performanceMetric);
            
            // Step 7: Distributed tracing and dependency mapping
            log.info("Step 7: Processing distributed tracing and mapping service dependencies");
            tracingService.updateDistributedTrace(serviceName, performanceMetric);
            tracingService.analyzeDependencyPerformance(serviceName, metricType, metricValue);
            
            if (tracingService.detectDependencyBottleneck(serviceName)) {
                tracingService.escalateDependencyIssue(serviceName, performanceMetric);
            }
            
            tracingService.updateServiceMap(serviceName, performanceMetric);
            
            // Step 8: Predictive analytics and forecasting
            log.info("Step 8: Performing predictive analytics and performance forecasting");
            BigDecimal predictedValue = monitoringService.predictFuturePerformance(serviceName, metricType);
            monitoringService.assessFutureCapacityNeeds(serviceName, predictedValue);
            
            if (monitoringService.predictPerformanceDegradation(serviceName, metricType)) {
                monitoringService.generateProactiveAlert(serviceName, metricType);
                monitoringService.recommendPreventiveMaintenance(serviceName);
            }
            
            // Step 9: Auto-scaling and remediation triggers
            log.info("Step 9: Triggering auto-scaling and automated remediation actions");
            if (monitoringService.shouldTriggerAutoScaling(serviceName, metricType, metricValue)) {
                monitoringService.initiateAutoScaling(serviceName, metricType, metricValue);
                monitoringService.notifyOpsTeam(serviceName, "AUTO_SCALING_TRIGGERED");
            }
            
            if (monitoringService.shouldTriggerCircuitBreaker(serviceName, metricType)) {
                monitoringService.activateCircuitBreaker(serviceName);
            }
            
            // Step 10: Business impact assessment
            log.info("Step 10: Assessing business impact and customer experience implications");
            BigDecimal businessImpact = monitoringService.calculateBusinessImpact(serviceName, performanceMetric);
            monitoringService.updateCustomerExperienceMetrics(serviceName, performanceMetric);
            
            if (businessImpact.compareTo(new BigDecimal("1000")) > 0) { // High business impact
                monitoringService.escalateToBusinessTeam(serviceName, businessImpact);
            }
            
            monitoringService.updateServiceHealthScore(serviceName, performanceMetric);
            
            // Step 11: Compliance and audit trail maintenance
            log.info("Step 11: Maintaining compliance records and comprehensive audit trails");
            monitoringService.createPerformanceAuditRecord(performanceMetric);
            monitoringService.validateComplianceRequirements(serviceName, performanceMetric);
            
            if (monitoringService.requiresRegulatoryReporting(serviceName, metricType)) {
                monitoringService.generateRegulatoryPerformanceReport(serviceName, performanceMetric);
            }
            
            monitoringService.archivePerformanceData(performanceMetric);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed system performance metric: service={}, eventId={}", serviceName, eventId);
            
        } catch (Exception e) {
            log.error("Error processing system performance event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("serviceName") || 
            !eventData.has("metricType") || !eventData.has("metricValue") ||
            !eventData.has("unit") || !eventData.has("timestamp") ||
            !eventData.has("instanceId") || !eventData.has("environment")) {
            throw new IllegalArgumentException("Invalid system performance event structure");
        }
    }
}