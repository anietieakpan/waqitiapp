package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.SystemEvent;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.MetricsCollectionService;
import com.waqiti.monitoring.service.IncidentManagementService;
import com.waqiti.monitoring.service.SystemHealthTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for system events from all microservices
 * 
 * Handles system-level events by:
 * - Tracking system health and performance metrics
 * - Triggering alerts for critical system issues
 * - Managing incident escalation workflows
 * - Collecting operational metrics for dashboards
 * - Coordinating system-wide monitoring responses
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemEventConsumer {

    private final MetricsCollectionService metricsService;
    private final AlertingService alertingService;
    private final IncidentManagementService incidentService;
    private final SystemHealthTrackingService healthTrackingService;

    @KafkaListener(
        topics = "system-events",
        groupId = "monitoring-system-events-group",
        containerFactory = "monitoringKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleSystemEvent(
            @Payload SystemEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.OFFSET) Long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.debug("Processing system event: {} from component: {} (severity: {})",
                    event.getEventId(), event.getComponentName(), event.getSeverity());
            
            // Collect system metrics
            collectSystemMetrics(event);
            
            // Update system health tracking
            updateHealthTracking(event);
            
            // Handle based on event severity and type
            switch (event.getSeverity()) {
                case EMERGENCY:
                case CRITICAL:
                    handleCriticalEvent(event);
                    break;
                
                case ERROR:
                    handleErrorEvent(event);
                    break;
                
                case WARNING:
                    handleWarningEvent(event);
                    break;
                
                case INFO:
                    handleInfoEvent(event);
                    break;
                
                case DEBUG:
                    handleDebugEvent(event);
                    break;
                
                default:
                    log.warn("Unknown severity level: {} for event: {}", 
                            event.getSeverity(), event.getEventId());
            }
            
            // Handle specific event types
            handleSpecificEventTypes(event);
            
            // Check for recovery events
            if (event.isRecovery()) {
                handleRecoveryEvent(event);
            }
            
            acknowledgment.acknowledge();
            log.trace("Successfully processed system event: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Error processing system event: {} - Error: {}", 
                    event.getEventId(), e.getMessage(), e);
            
            // Don't acknowledge to trigger retry for critical events
            if (event.isCritical()) {
                throw new RuntimeException("Failed to process critical system event", e);
            }
        }
    }

    private void collectSystemMetrics(SystemEvent event) {
        try {
            // Performance metrics
            if (event.getCpuUsage() != null) {
                metricsService.recordCpuUsage(event.getComponentName(), 
                        event.getInstanceId(), event.getCpuUsage());
            }
            
            if (event.getMemoryUsage() != null) {
                metricsService.recordMemoryUsage(event.getComponentName(), 
                        event.getInstanceId(), event.getMemoryUsage());
            }
            
            if (event.getDiskUsage() != null) {
                metricsService.recordDiskUsage(event.getComponentName(), 
                        event.getInstanceId(), event.getDiskUsage());
            }
            
            if (event.getAverageResponseTimeMs() != null) {
                metricsService.recordResponseTime(event.getComponentName(),
                        event.getAverageResponseTimeMs());
            }
            
            if (event.getRequestsPerSecond() != null) {
                metricsService.recordRequestRate(event.getComponentName(),
                        event.getRequestsPerSecond());
            }
            
            // Database metrics
            if (event.getActiveDbConnections() != null) {
                metricsService.recordDatabaseConnections(event.getComponentName(),
                        event.getActiveDbConnections(), event.getConnectionPoolSize());
            }
            
            if (event.getSlowQueryCount() != null) {
                metricsService.recordSlowQueryCount(event.getComponentName(),
                        event.getSlowQueryCount());
            }
            
            // Cache metrics
            if (event.getCacheHitRate() != null) {
                metricsService.recordCacheMetrics(event.getComponentName(),
                        event.getCacheHitRate(), event.getCacheEvictions());
            }
            
            // Queue metrics
            if (event.getMessagesInQueue() != null) {
                metricsService.recordQueueDepth(event.getQueueName(),
                        event.getMessagesInQueue(), event.getConsumerLag());
            }
            
            // Error metrics
            if (event.getErrorRate() != null) {
                metricsService.recordErrorRate(event.getComponentName(),
                        event.getErrorRate());
            }
            
        } catch (Exception e) {
            log.warn("Failed to collect metrics for system event: {}", event.getEventId(), e);
        }
    }

    private void updateHealthTracking(SystemEvent event) {
        try {
            healthTrackingService.updateComponentHealth(
                    event.getComponentId(),
                    event.getComponentName(),
                    event.getCurrentHealth(),
                    event.getCurrentStatus(),
                    event.getHealthChecks()
            );
            
            // Track uptime
            if (event.getUptimeSeconds() != null) {
                healthTrackingService.updateUptime(event.getComponentName(),
                        event.getUptimeSeconds());
            }
            
        } catch (Exception e) {
            log.warn("Failed to update health tracking for event: {}", event.getEventId(), e);
        }
    }

    private void handleCriticalEvent(SystemEvent event) {
        log.error("CRITICAL system event: {} - Component: {} - Description: {}",
                event.getEventId(), event.getComponentName(), event.getEventDescription());
        
        try {
            // Trigger immediate critical alert
            alertingService.triggerCriticalAlert(
                    event.getComponentName(),
                    event.getEventDescription(),
                    event.getSystemEventType().toString(),
                    event.getMetadata()
            );
            
            // Create high-priority incident if none exists
            if (!event.isIncidentCreated()) {
                String incidentId = incidentService.createCriticalIncident(
                        event.getComponentName(),
                        event.getEventDescription(),
                        event.getSystemEventType(),
                        event.getMetadata()
                );
                
                // Auto-assign to on-call engineer
                incidentService.assignToOnCallEngineer(incidentId, event.getComponentType());
                
                // Update runbook if available
                if (event.getRunbookUrl() != null) {
                    incidentService.attachRunbook(incidentId, event.getRunbookUrl());
                }
            }
            
            // Handle specific critical events
            switch (event.getSystemEventType()) {
                case SERVICE_CRASHED:
                    handleServiceCrash(event);
                    break;
                
                case DATABASE_CONNECTION_LOST:
                    handleDatabaseFailure(event);
                    break;
                
                case SECURITY_THREAT_DETECTED:
                    handleSecurityThreat(event);
                    break;
                
                case CONNECTION_POOL_EXHAUSTED:
                    handleConnectionPoolExhaustion(event);
                    break;
                
                default:
                    log.info("Handled generic critical event: {}", event.getSystemEventType());
            }
            
        } catch (Exception e) {
            log.error("Failed to handle critical system event: {}", event.getEventId(), e);
            throw e;
        }
    }

    private void handleErrorEvent(SystemEvent event) {
        log.warn("ERROR system event: {} - Component: {} - Description: {}",
                event.getEventId(), event.getComponentName(), event.getEventDescription());
        
        try {
            // Trigger error alert with appropriate escalation
            alertingService.triggerErrorAlert(
                    event.getComponentName(),
                    event.getEventDescription(),
                    event.getErrorType(),
                    event.getErrorCount()
            );
            
            // Check if error rate threshold exceeded
            if (event.getErrorRate() != null && event.getErrorRate() > 0.05) { // 5% threshold
                alertingService.triggerHighErrorRateAlert(
                        event.getComponentName(), event.getErrorRate());
            }
            
            // Track error patterns
            incidentService.trackErrorPattern(
                    event.getComponentName(),
                    event.getErrorType(),
                    event.getErrorMessage()
            );
            
        } catch (Exception e) {
            log.warn("Failed to handle error system event: {}", event.getEventId(), e);
        }
    }

    private void handleWarningEvent(SystemEvent event) {
        log.info("WARNING system event: {} - Component: {} - Description: {}",
                event.getEventId(), event.getComponentName(), event.getEventDescription());
        
        try {
            // Trigger warning alert
            alertingService.triggerWarningAlert(
                    event.getComponentName(),
                    event.getEventDescription(),
                    event.getSystemEventType().toString()
            );
            
            // Handle specific warning types
            switch (event.getSystemEventType()) {
                case HIGH_CPU_USAGE:
                case HIGH_MEMORY_USAGE:
                case HIGH_DISK_USAGE:
                    handleResourceWarning(event);
                    break;
                
                case SLOW_RESPONSE:
                    handlePerformanceWarning(event);
                    break;
                
                case QUEUE_DEPTH_HIGH:
                    handleQueueWarning(event);
                    break;
                
                default:
                    log.debug("Handled generic warning event: {}", event.getSystemEventType());
            }
            
        } catch (Exception e) {
            log.warn("Failed to handle warning system event: {}", event.getEventId(), e);
        }
    }

    private void handleInfoEvent(SystemEvent event) {
        log.info("INFO system event: {} - Component: {} - Type: {}",
                event.getEventId(), event.getComponentName(), event.getSystemEventType());
        
        // Track operational events
        metricsService.recordOperationalEvent(
                event.getComponentName(),
                event.getSystemEventType().toString()
        );
        
        // Handle deployments and scaling
        if (event.getSystemEventType() == SystemEvent.SystemEventType.DEPLOYMENT_COMPLETED) {
            handleDeploymentCompletion(event);
        } else if (event.getSystemEventType() == SystemEvent.SystemEventType.AUTO_SCALING_TRIGGERED) {
            handleAutoScaling(event);
        }
    }

    private void handleDebugEvent(SystemEvent event) {
        log.debug("DEBUG system event: {} - Component: {} - Type: {}",
                event.getEventId(), event.getComponentName(), event.getSystemEventType());
        
        // Only collect metrics for debug events, no alerting
        metricsService.recordDebugMetric(
                event.getComponentName(),
                event.getSystemEventType().toString(),
                event.getMetadata()
        );
    }

    private void handleSpecificEventTypes(SystemEvent event) {
        // Circuit breaker events
        if (event.getCircuitBreakerState() != null) {
            handleCircuitBreakerEvent(event);
        }
        
        // Deployment events
        if (event.getDeploymentStatus() != null) {
            handleDeploymentEvent(event);
        }
        
        // Backup events
        if (event.getBackupStatus() != null) {
            handleBackupEvent(event);
        }
    }

    private void handleRecoveryEvent(SystemEvent event) {
        log.info("RECOVERY event detected: {} - Component: {} recovered",
                event.getEventId(), event.getComponentName());
        
        try {
            // Send recovery notification
            alertingService.sendRecoveryNotification(
                    event.getComponentName(),
                    event.getPreviousHealth(),
                    event.getCurrentHealth()
            );
            
            // Close related incidents
            incidentService.resolveIncidentsForComponent(
                    event.getComponentName(),
                    "Component recovered: " + event.getEventDescription()
            );
            
            // Update health tracking
            healthTrackingService.recordRecovery(
                    event.getComponentName(),
                    event.getTimestamp()
            );
            
        } catch (Exception e) {
            log.warn("Failed to handle recovery event: {}", event.getEventId(), e);
        }
    }

    private void handleServiceCrash(SystemEvent event) {
        alertingService.triggerServiceCrashAlert(event.getComponentName(),
                event.getInstanceId(), event.getStackTrace());
        
        // Trigger auto-restart if configured
        incidentService.triggerAutoRestart(event.getComponentName(), event.getInstanceId());
    }

    private void handleDatabaseFailure(SystemEvent event) {
        alertingService.triggerDatabaseFailureAlert(event.getDatabaseHost(),
                event.getComponentName());
        
        // Switch to read replica if available
        incidentService.initiateFailoverProcedure(event.getDatabaseHost());
    }

    private void handleSecurityThreat(SystemEvent event) {
        alertingService.triggerSecurityAlert(event.getSecurityEventType(),
                event.getSecurityThreatLevel(), event.getAttackVector());
        
        // Auto-block if necessary
        if ("HIGH".equals(event.getSecurityThreatLevel())) {
            incidentService.activateSecurityMeasures(event.getIpAddress(),
                    event.getAttackVector());
        }
    }

    private void handleConnectionPoolExhaustion(SystemEvent event) {
        alertingService.triggerConnectionPoolAlert(event.getComponentName(),
                event.getActiveDbConnections(), event.getConnectionPoolSize());
        
        // Scale connection pool if possible
        incidentService.scaleConnectionPool(event.getComponentName());
    }

    private void handleResourceWarning(SystemEvent event) {
        // Check if auto-scaling is enabled
        incidentService.checkAutoScalingConfig(event.getComponentName(),
                event.getSystemEventType());
    }

    private void handlePerformanceWarning(SystemEvent event) {
        metricsService.recordPerformanceDegradation(event.getComponentName(),
                event.getAverageResponseTimeMs(), event.getP99ResponseTimeMs());
    }

    private void handleQueueWarning(SystemEvent event) {
        alertingService.triggerQueueDepthAlert(event.getQueueName(),
                event.getMessagesInQueue(), event.getConsumerLag());
    }

    private void handleDeploymentCompletion(SystemEvent event) {
        metricsService.recordSuccessfulDeployment(event.getComponentName(),
                event.getApplicationVersion(), event.getDeploymentType());
    }

    private void handleAutoScaling(SystemEvent event) {
        metricsService.recordScalingEvent(event.getComponentName(),
                event.getCurrentInstances(), event.getTargetInstances(),
                event.getScalingTrigger());
    }

    private void handleCircuitBreakerEvent(SystemEvent event) {
        metricsService.recordCircuitBreakerState(event.getComponentName(),
                event.getCircuitBreakerName(), event.getCircuitBreakerState());
        
        if (event.getCircuitBreakerState() == SystemEvent.CircuitBreakerState.OPEN) {
            alertingService.triggerCircuitBreakerAlert(event.getComponentName(),
                    event.getCircuitBreakerName(), event.getFailureRate());
        }
    }

    private void handleDeploymentEvent(SystemEvent event) {
        if (event.getDeploymentStatus() == SystemEvent.DeploymentStatus.FAILED) {
            alertingService.triggerDeploymentFailureAlert(event.getComponentName(),
                    event.getDeploymentId(), event.getGitCommitHash());
        }
    }

    private void handleBackupEvent(SystemEvent event) {
        if (event.getBackupStatus() == SystemEvent.BackupStatus.FAILED) {
            alertingService.triggerBackupFailureAlert(event.getComponentName(),
                    event.getBackupId(), event.getBackupType());
        }
    }
}