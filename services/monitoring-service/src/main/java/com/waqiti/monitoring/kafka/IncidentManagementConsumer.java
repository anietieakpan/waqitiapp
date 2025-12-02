package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.monitoring.entity.*;
import com.waqiti.monitoring.repository.*;
import com.waqiti.monitoring.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class IncidentManagementConsumer extends BaseKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(IncidentManagementConsumer.class);
    
    @Value("${waqiti.monitoring.incident.auto-escalation-minutes:30}")
    private int autoEscalationMinutes;
    
    @Value("${waqiti.monitoring.incident.sla-critical-minutes:15}")
    private int slaCriticalMinutes;
    
    @Value("${waqiti.monitoring.incident.sla-high-minutes:60}")
    private int slaHighMinutes;
    
    @Value("${waqiti.monitoring.incident.sla-medium-minutes:240}")
    private int slaMediumMinutes;
    
    @Value("${waqiti.monitoring.incident.max-active-incidents:100}")
    private int maxActiveIncidents;
    
    @Value("${waqiti.monitoring.incident.correlation-window-minutes:10}")
    private int correlationWindowMinutes;

    private final IncidentRepository incidentRepository;
    private final IncidentTimelineRepository incidentTimelineRepository;
    private final IncidentAlertRepository incidentAlertRepository;
    private final IncidentEscalationRepository incidentEscalationRepository;
    private final IncidentResolutionRepository incidentResolutionRepository;
    private final IncidentMetricsRepository incidentMetricsRepository;
    private final PostmortemRepository postmortemRepository;
    private final AlertingService alertingService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public IncidentManagementConsumer(
            IncidentRepository incidentRepository,
            IncidentTimelineRepository incidentTimelineRepository,
            IncidentAlertRepository incidentAlertRepository,
            IncidentEscalationRepository incidentEscalationRepository,
            IncidentResolutionRepository incidentResolutionRepository,
            IncidentMetricsRepository incidentMetricsRepository,
            PostmortemRepository postmortemRepository,
            AlertingService alertingService,
            MetricsService metricsService,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.incidentRepository = incidentRepository;
        this.incidentTimelineRepository = incidentTimelineRepository;
        this.incidentAlertRepository = incidentAlertRepository;
        this.incidentEscalationRepository = incidentEscalationRepository;
        this.incidentResolutionRepository = incidentResolutionRepository;
        this.incidentMetricsRepository = incidentMetricsRepository;
        this.postmortemRepository = postmortemRepository;
        this.alertingService = alertingService;
        this.metricsService = metricsService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);
    private final Map<String, IncidentState> activeIncidents = new ConcurrentHashMap<>();
    private final Map<String, List<String>> incidentCorrelations = new ConcurrentHashMap<>();
    private final Map<String, IncidentStatistics> serviceIncidentStats = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastEscalation = new ConcurrentHashMap<>();

    private Counter processedEventsCounter;
    private Counter processedIncidentCreatedCounter;
    private Counter processedIncidentUpdatedCounter;
    private Counter processedIncidentResolvedCounter;
    private Counter processedIncidentEscalatedCounter;
    private Counter processedIncidentAlertCounter;
    private Counter processedIncidentTimelineCounter;
    private Counter processedIncidentResolutionCounter;
    private Counter processedIncidentMetricsCounter;
    private Counter processedPostmortemCounter;
    private Counter processedIncidentCorrelationCounter;
    private Counter processedSlaBreachCounter;
    private Counter processedIncidentMergeCounter;
    private Counter processedWarRoomActivationCounter;
    private Counter processedIncidentReopenedCounter;
    private Timer incidentProcessingTimer;
    private Timer incidentResolutionTimer;
    
    private Gauge activeIncidentsGauge;
    private Gauge criticalIncidentsGauge;
    private Gauge averageMttrsGauge;
    private Gauge slaComplianceGauge;

    @PostConstruct
    public void init() {
        this.processedEventsCounter = Counter.builder("incident_management_events_processed")
                .description("Number of incident management events processed")
                .register(meterRegistry);
        
        this.processedIncidentCreatedCounter = Counter.builder("incident_created_processed")
                .description("Number of incident created events processed")
                .register(meterRegistry);
        
        this.processedIncidentUpdatedCounter = Counter.builder("incident_updated_processed")
                .description("Number of incident updated events processed")
                .register(meterRegistry);
        
        this.processedIncidentResolvedCounter = Counter.builder("incident_resolved_processed")
                .description("Number of incident resolved events processed")
                .register(meterRegistry);
        
        this.processedIncidentEscalatedCounter = Counter.builder("incident_escalated_processed")
                .description("Number of incident escalated events processed")
                .register(meterRegistry);
        
        this.processedIncidentAlertCounter = Counter.builder("incident_alert_processed")
                .description("Number of incident alert events processed")
                .register(meterRegistry);
        
        this.processedIncidentTimelineCounter = Counter.builder("incident_timeline_processed")
                .description("Number of incident timeline events processed")
                .register(meterRegistry);
        
        this.processedIncidentResolutionCounter = Counter.builder("incident_resolution_processed")
                .description("Number of incident resolution events processed")
                .register(meterRegistry);
        
        this.processedIncidentMetricsCounter = Counter.builder("incident_metrics_processed")
                .description("Number of incident metrics events processed")
                .register(meterRegistry);
        
        this.processedPostmortemCounter = Counter.builder("postmortem_processed")
                .description("Number of postmortem events processed")
                .register(meterRegistry);
        
        this.processedIncidentCorrelationCounter = Counter.builder("incident_correlation_processed")
                .description("Number of incident correlation events processed")
                .register(meterRegistry);
        
        this.processedSlaBreachCounter = Counter.builder("sla_breach_processed")
                .description("Number of SLA breach events processed")
                .register(meterRegistry);
        
        this.processedIncidentMergeCounter = Counter.builder("incident_merge_processed")
                .description("Number of incident merge events processed")
                .register(meterRegistry);
        
        this.processedWarRoomActivationCounter = Counter.builder("war_room_activation_processed")
                .description("Number of war room activation events processed")
                .register(meterRegistry);
        
        this.processedIncidentReopenedCounter = Counter.builder("incident_reopened_processed")
                .description("Number of incident reopened events processed")
                .register(meterRegistry);
        
        this.incidentProcessingTimer = Timer.builder("incident_processing_duration")
                .description("Time taken to process incident events")
                .register(meterRegistry);
        
        this.incidentResolutionTimer = Timer.builder("incident_resolution_duration")
                .description("Time taken to resolve incidents")
                .register(meterRegistry);
        
        this.activeIncidentsGauge = Gauge.builder("active_incidents_count", this, IncidentManagementConsumer::getActiveIncidentsCount)
                .description("Number of active incidents")
                .register(meterRegistry);
        
        this.criticalIncidentsGauge = Gauge.builder("critical_incidents_count", this, IncidentManagementConsumer::getCriticalIncidentsCount)
                .description("Number of critical incidents")
                .register(meterRegistry);
        
        this.averageMttrsGauge = Gauge.builder("average_mttr_minutes", this, IncidentManagementConsumer::getAverageMttr)
                .description("Average mean time to resolution in minutes")
                .register(meterRegistry);
        
        this.slaComplianceGauge = Gauge.builder("sla_compliance_percent", this, IncidentManagementConsumer::getSlaCompliance)
                .description("SLA compliance percentage")
                .register(meterRegistry);

        scheduledExecutor.scheduleAtFixedRate(this::checkForEscalations, 
                5, 5, TimeUnit.MINUTES);
        
        scheduledExecutor.scheduleAtFixedRate(this::checkSlaCompliance, 
                10, 10, TimeUnit.MINUTES);
        
        scheduledExecutor.scheduleAtFixedRate(this::analyzeIncidentPatterns, 
                30, 30, TimeUnit.MINUTES);
        
        scheduledExecutor.scheduleAtFixedRate(this::cleanupOldData, 
                24, 24, TimeUnit.HOURS);
    }

    @PreDestroy
    public void cleanup() {
        scheduledExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @KafkaListener(topics = "incident-management", groupId = "incident-management-group", 
                   containerFactory = "kafkaListenerContainerFactory")
    @CircuitBreaker(name = "incident-management-consumer")
    @Retry(name = "incident-management-consumer")
    @Transactional
    public void handleIncidentManagementEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "incident-management");

        try {
            logger.info("Processing incident management event: partition={}, offset={}", 
                    record.partition(), record.offset());

            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.path("eventType").asText();

            switch (eventType) {
                case "INCIDENT_CREATED":
                    processIncidentCreated(eventData);
                    processedIncidentCreatedCounter.increment();
                    break;
                case "INCIDENT_UPDATED":
                    processIncidentUpdated(eventData);
                    processedIncidentUpdatedCounter.increment();
                    break;
                case "INCIDENT_RESOLVED":
                    processIncidentResolved(eventData);
                    processedIncidentResolvedCounter.increment();
                    break;
                case "INCIDENT_ESCALATED":
                    processIncidentEscalated(eventData);
                    processedIncidentEscalatedCounter.increment();
                    break;
                case "INCIDENT_ALERT":
                    processIncidentAlert(eventData);
                    processedIncidentAlertCounter.increment();
                    break;
                case "INCIDENT_TIMELINE":
                    processIncidentTimeline(eventData);
                    processedIncidentTimelineCounter.increment();
                    break;
                case "INCIDENT_RESOLUTION":
                    processIncidentResolution(eventData);
                    processedIncidentResolutionCounter.increment();
                    break;
                case "INCIDENT_METRICS":
                    processIncidentMetrics(eventData);
                    processedIncidentMetricsCounter.increment();
                    break;
                case "POSTMORTEM":
                    processPostmortem(eventData);
                    processedPostmortemCounter.increment();
                    break;
                case "INCIDENT_CORRELATION":
                    processIncidentCorrelation(eventData);
                    processedIncidentCorrelationCounter.increment();
                    break;
                case "SLA_BREACH":
                    processSlaBreachEvent(eventData);
                    processedSlaBreachCounter.increment();
                    break;
                case "INCIDENT_MERGE":
                    processIncidentMerge(eventData);
                    processedIncidentMergeCounter.increment();
                    break;
                case "WAR_ROOM_ACTIVATION":
                    processWarRoomActivation(eventData);
                    processedWarRoomActivationCounter.increment();
                    break;
                case "INCIDENT_REOPENED":
                    processIncidentReopened(eventData);
                    processedIncidentReopenedCounter.increment();
                    break;
                default:
                    logger.warn("Unknown incident management event type: {}", eventType);
            }

            processedEventsCounter.increment();
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse incident management event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (DataAccessException e) {
            logger.error("Database error processing incident management event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            logger.error("Unexpected error processing incident management event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            sample.stop(incidentProcessingTimer);
            MDC.clear();
        }
    }

    private void processIncidentCreated(JsonNode eventData) {
        try {
            Incident incident = new Incident();
            incident.setIncidentId(eventData.path("incidentId").asText());
            incident.setTitle(eventData.path("title").asText());
            incident.setDescription(eventData.path("description").asText());
            incident.setSeverity(eventData.path("severity").asText());
            incident.setPriority(eventData.path("priority").asText());
            incident.setStatus("OPEN");
            incident.setServiceName(eventData.path("serviceName").asText());
            incident.setAffectedServices(eventData.path("affectedServices").asText());
            incident.setCategory(eventData.path("category").asText());
            incident.setReporter(eventData.path("reporter").asText());
            incident.setAssignee(eventData.path("assignee").asText());
            incident.setCreatedAt(parseTimestamp(eventData.path("createdAt").asText()));
            incident.setLastUpdatedAt(LocalDateTime.now());
            incident.setDetectionMethod(eventData.path("detectionMethod").asText());
            incident.setImpactScope(eventData.path("impactScope").asText());
            incident.setEstimatedResolutionTime(eventData.path("estimatedResolutionTime").asText());
            
            JsonNode tagsNode = eventData.path("tags");
            if (!tagsNode.isMissingNode()) {
                incident.setTags(tagsNode.toString());
            }
            
            incidentRepository.save(incident);
            
            IncidentState state = new IncidentState(incident);
            activeIncidents.put(incident.getIncidentId(), state);
            
            addTimelineEvent(incident.getIncidentId(), "CREATED", 
                    "Incident created: " + incident.getTitle(), incident.getReporter());
            
            metricsService.recordIncidentCreated(incident.getServiceName(), incident.getSeverity(), incident);
            
            notifyIncidentCreation(incident);
            
            if ("CRITICAL".equals(incident.getSeverity())) {
                activateWarRoom(incident);
            }
            
            checkForCorrelatedIncidents(incident);
            
            logger.info("Processed incident creation: id={}, title={}, severity={}, service={}", 
                    incident.getIncidentId(), incident.getTitle(), incident.getSeverity(), incident.getServiceName());
            
        } catch (Exception e) {
            logger.error("Error processing incident created: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processIncidentUpdated(JsonNode eventData) {
        try {
            String incidentId = eventData.path("incidentId").asText();
            String updateType = eventData.path("updateType").asText();
            String updatedBy = eventData.path("updatedBy").asText();
            String updateDescription = eventData.path("updateDescription").asText();
            
            Incident incident = incidentRepository.findById(incidentId).orElse(null);
            if (incident != null) {
                incident.setLastUpdatedAt(LocalDateTime.now());
                
                if (eventData.has("status")) {
                    incident.setStatus(eventData.path("status").asText());
                }
                if (eventData.has("severity")) {
                    incident.setSeverity(eventData.path("severity").asText());
                }
                if (eventData.has("assignee")) {
                    incident.setAssignee(eventData.path("assignee").asText());
                }
                if (eventData.has("estimatedResolutionTime")) {
                    incident.setEstimatedResolutionTime(eventData.path("estimatedResolutionTime").asText());
                }
                
                incidentRepository.save(incident);
                
                addTimelineEvent(incidentId, updateType, updateDescription, updatedBy);
                
                IncidentState state = activeIncidents.get(incidentId);
                if (state != null) {
                    state.update(incident);
                }
                
                metricsService.recordIncidentUpdate(incident.getServiceName(), updateType);
                
                notifyIncidentUpdate(incident, updateType, updateDescription);
                
                logger.debug("Processed incident update: id={}, type={}, updatedBy={}", 
                        incidentId, updateType, updatedBy);
            }
            
        } catch (Exception e) {
            logger.error("Error processing incident updated: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processIncidentResolved(JsonNode eventData) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String incidentId = eventData.path("incidentId").asText();
            String resolvedBy = eventData.path("resolvedBy").asText();
            String resolutionSummary = eventData.path("resolutionSummary").asText();
            String rootCause = eventData.path("rootCause").asText();
            LocalDateTime resolvedAt = parseTimestamp(eventData.path("resolvedAt").asText());
            
            Incident incident = incidentRepository.findById(incidentId).orElse(null);
            if (incident != null) {
                incident.setStatus("RESOLVED");
                incident.setResolvedAt(resolvedAt);
                incident.setResolvedBy(resolvedBy);
                incident.setRootCause(rootCause);
                incident.setResolutionSummary(resolutionSummary);
                
                Duration resolutionTime = Duration.between(incident.getCreatedAt(), resolvedAt);
                incident.setResolutionTimeMinutes(resolutionTime.toMinutes());
                
                incidentRepository.save(incident);
                
                IncidentResolution resolution = new IncidentResolution();
                resolution.setIncidentId(incidentId);
                resolution.setResolvedAt(resolvedAt);
                resolution.setResolvedBy(resolvedBy);
                resolution.setResolutionSummary(resolutionSummary);
                resolution.setRootCause(rootCause);
                resolution.setResolutionTimeMinutes(resolutionTime.toMinutes());
                resolution.setSlaMet(checkSlaCompliance(incident.getSeverity(), resolutionTime.toMinutes()));
                
                incidentResolutionRepository.save(resolution);
                
                activeIncidents.remove(incidentId);
                
                addTimelineEvent(incidentId, "RESOLVED", resolutionSummary, resolvedBy);
                
                updateIncidentStatistics(incident);
                
                metricsService.recordIncidentResolved(incident.getServiceName(), incident.getSeverity(), 
                        resolutionTime.toMinutes());
                
                notifyIncidentResolution(incident, resolution);
                
                if ("CRITICAL".equals(incident.getSeverity()) || "HIGH".equals(incident.getSeverity())) {
                    schedulePostmortem(incident);
                }
                
                logger.info("Processed incident resolution: id={}, duration={}min, slaMet={}", 
                        incidentId, resolutionTime.toMinutes(), resolution.isSlaMet());
            }
            
        } catch (Exception e) {
            logger.error("Error processing incident resolved: {}", e.getMessage(), e);
            throw e;
        } finally {
            sample.stop(incidentResolutionTimer);
        }
    }

    private void processIncidentEscalated(JsonNode eventData) {
        try {
            String incidentId = eventData.path("incidentId").asText();
            String escalationLevel = eventData.path("escalationLevel").asText();
            String escalatedTo = eventData.path("escalatedTo").asText();
            String escalationReason = eventData.path("escalationReason").asText();
            LocalDateTime escalatedAt = parseTimestamp(eventData.path("escalatedAt").asText());
            
            IncidentEscalation escalation = new IncidentEscalation();
            escalation.setIncidentId(incidentId);
            escalation.setEscalationLevel(escalationLevel);
            escalation.setEscalatedTo(escalatedTo);
            escalation.setEscalationReason(escalationReason);
            escalation.setEscalatedAt(escalatedAt);
            
            incidentEscalationRepository.save(escalation);
            
            Incident incident = incidentRepository.findById(incidentId).orElse(null);
            if (incident != null) {
                incident.setEscalationLevel(escalationLevel);
                incident.setLastUpdatedAt(LocalDateTime.now());
                incidentRepository.save(incident);
                
                addTimelineEvent(incidentId, "ESCALATED", 
                        String.format("Escalated to %s: %s", escalatedTo, escalationReason), "System");
                
                lastEscalation.put(incidentId, escalatedAt);
                
                metricsService.recordIncidentEscalation(incident.getServiceName(), escalationLevel);
                
                notifyEscalation(incident, escalation);
                
                if ("L3".equals(escalationLevel) || "EXECUTIVE".equals(escalationLevel)) {
                    triggerEmergencyProtocol(incident, escalation);
                }
                
                logger.warn("Processed incident escalation: id={}, level={}, escalatedTo={}", 
                        incidentId, escalationLevel, escalatedTo);
            }
            
        } catch (Exception e) {
            logger.error("Error processing incident escalated: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processIncidentAlert(JsonNode eventData) {
        try {
            IncidentAlert alert = new IncidentAlert();
            alert.setIncidentId(eventData.path("incidentId").asText());
            alert.setAlertType(eventData.path("alertType").asText());
            alert.setSeverity(eventData.path("severity").asText());
            alert.setMessage(eventData.path("message").asText());
            alert.setRecipients(eventData.path("recipients").asText());
            alert.setSentAt(parseTimestamp(eventData.path("sentAt").asText()));
            alert.setDeliveryStatus(eventData.path("deliveryStatus").asText());
            
            incidentAlertRepository.save(alert);
            
            if ("FAILED".equals(alert.getDeliveryStatus())) {
                retryAlertDelivery(alert);
            }
            
            metricsService.recordIncidentAlert(alert.getAlertType(), alert.getSeverity());
            
            logger.debug("Processed incident alert: incidentId={}, type={}, severity={}", 
                    alert.getIncidentId(), alert.getAlertType(), alert.getSeverity());
            
        } catch (Exception e) {
            logger.error("Error processing incident alert: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processIncidentTimeline(JsonNode eventData) {
        try {
            IncidentTimeline timeline = new IncidentTimeline();
            timeline.setIncidentId(eventData.path("incidentId").asText());
            timeline.setEventType(eventData.path("eventType").asText());
            timeline.setEventDescription(eventData.path("eventDescription").asText());
            timeline.setEventTime(parseTimestamp(eventData.path("eventTime").asText()));
            timeline.setActor(eventData.path("actor").asText());
            
            JsonNode detailsNode = eventData.path("details");
            if (!detailsNode.isMissingNode()) {
                timeline.setDetails(detailsNode.toString());
            }
            
            incidentTimelineRepository.save(timeline);
            
            logger.debug("Processed incident timeline: incidentId={}, eventType={}, actor={}", 
                    timeline.getIncidentId(), timeline.getEventType(), timeline.getActor());
            
        } catch (Exception e) {
            logger.error("Error processing incident timeline: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processIncidentResolution(JsonNode eventData) {
        try {
            String incidentId = eventData.path("incidentId").asText();
            String resolutionType = eventData.path("resolutionType").asText();
            String resolutionSteps = eventData.path("resolutionSteps").asText();
            String preventiveMeasures = eventData.path("preventiveMeasures").asText();
            boolean permanentFix = eventData.path("permanentFix").asBoolean();
            
            IncidentResolution resolution = incidentResolutionRepository.findById(incidentId).orElse(new IncidentResolution());
            resolution.setIncidentId(incidentId);
            resolution.setResolutionType(resolutionType);
            resolution.setResolutionSteps(resolutionSteps);
            resolution.setPreventiveMeasures(preventiveMeasures);
            resolution.setPermanentFix(permanentFix);
            
            incidentResolutionRepository.save(resolution);
            
            if (!permanentFix) {
                scheduleFollowUp(incidentId, preventiveMeasures);
            }
            
            logger.debug("Processed incident resolution details: incidentId={}, type={}, permanentFix={}", 
                    incidentId, resolutionType, permanentFix);
            
        } catch (Exception e) {
            logger.error("Error processing incident resolution: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processIncidentMetrics(JsonNode eventData) {
        try {
            IncidentMetrics metrics = new IncidentMetrics();
            metrics.setTimestamp(parseTimestamp(eventData.path("timestamp").asText()));
            metrics.setServiceName(eventData.path("serviceName").asText());
            metrics.setTimeWindow(eventData.path("timeWindow").asText());
            metrics.setTotalIncidents(eventData.path("totalIncidents").asLong());
            metrics.setCriticalIncidents(eventData.path("criticalIncidents").asLong());
            metrics.setHighIncidents(eventData.path("highIncidents").asLong());
            metrics.setMediumIncidents(eventData.path("mediumIncidents").asLong());
            metrics.setLowIncidents(eventData.path("lowIncidents").asLong());
            metrics.setAverageMttr(eventData.path("averageMttr").asDouble());
            metrics.setSlaCompliance(eventData.path("slaCompliance").asDouble());
            metrics.setRecurrenceRate(eventData.path("recurrenceRate").asDouble());
            metrics.setAutoResolvedCount(eventData.path("autoResolvedCount").asLong());
            
            incidentMetricsRepository.save(metrics);
            
            updateServiceIncidentStatistics(metrics);
            
            metricsService.recordIncidentMetrics(metrics.getServiceName(), metrics);
            
            logger.debug("Processed incident metrics: service={}, window={}, total={}, mttr={}", 
                    metrics.getServiceName(), metrics.getTimeWindow(), 
                    metrics.getTotalIncidents(), metrics.getAverageMttr());
            
        } catch (Exception e) {
            logger.error("Error processing incident metrics: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processPostmortem(JsonNode eventData) {
        try {
            Postmortem postmortem = new Postmortem();
            postmortem.setIncidentId(eventData.path("incidentId").asText());
            postmortem.setScheduledDate(parseTimestamp(eventData.path("scheduledDate").asText()));
            postmortem.setConductedDate(parseTimestamp(eventData.path("conductedDate").asText()));
            postmortem.setFacilitator(eventData.path("facilitator").asText());
            postmortem.setAttendees(eventData.path("attendees").asText());
            postmortem.setSummary(eventData.path("summary").asText());
            postmortem.setRootCauseAnalysis(eventData.path("rootCauseAnalysis").asText());
            postmortem.setLessonsLearned(eventData.path("lessonsLearned").asText());
            postmortem.setActionItems(eventData.path("actionItems").asText());
            postmortem.setFollowUpDate(parseTimestamp(eventData.path("followUpDate").asText()));
            postmortem.setStatus(eventData.path("status").asText());
            
            postmortemRepository.save(postmortem);
            
            if ("COMPLETED".equals(postmortem.getStatus())) {
                extractAndShareLessonsLearned(postmortem);
            }
            
            metricsService.recordPostmortem(postmortem.getIncidentId(), postmortem.getStatus());
            
            logger.info("Processed postmortem: incidentId={}, status={}, facilitator={}", 
                    postmortem.getIncidentId(), postmortem.getStatus(), postmortem.getFacilitator());
            
        } catch (Exception e) {
            logger.error("Error processing postmortem: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processIncidentCorrelation(JsonNode eventData) {
        try {
            String primaryIncidentId = eventData.path("primaryIncidentId").asText();
            String correlatedIncidentId = eventData.path("correlatedIncidentId").asText();
            String correlationType = eventData.path("correlationType").asText();
            double correlationScore = eventData.path("correlationScore").asDouble();
            String correlationReason = eventData.path("correlationReason").asText();
            
            if (correlationScore > 0.8) {
                incidentCorrelations.computeIfAbsent(primaryIncidentId, k -> new ArrayList<>())
                        .add(correlatedIncidentId);
                
                addTimelineEvent(primaryIncidentId, "CORRELATED", 
                        String.format("Correlated with incident %s: %s", correlatedIncidentId, correlationReason), 
                        "System");
                
                if ("DUPLICATE".equals(correlationType)) {
                    mergeIncidents(primaryIncidentId, correlatedIncidentId);
                }
                
                metricsService.recordIncidentCorrelation(correlationType, correlationScore);
                
                logger.info("Processed incident correlation: primary={}, correlated={}, type={}, score={}", 
                        primaryIncidentId, correlatedIncidentId, correlationType, correlationScore);
            }
            
        } catch (Exception e) {
            logger.error("Error processing incident correlation: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processSlaBreachEvent(JsonNode eventData) {
        try {
            String incidentId = eventData.path("incidentId").asText();
            String slaType = eventData.path("slaType").asText();
            long expectedMinutes = eventData.path("expectedMinutes").asLong();
            long actualMinutes = eventData.path("actualMinutes").asLong();
            double breachPercentage = eventData.path("breachPercentage").asDouble();
            
            Incident incident = incidentRepository.findById(incidentId).orElse(null);
            if (incident != null) {
                incident.setSlaBreached(true);
                incident.setSlaBreachDetails(String.format("%s SLA breached by %.1f%%", slaType, breachPercentage));
                incidentRepository.save(incident);
                
                IncidentAlert alert = new IncidentAlert();
                alert.setIncidentId(incidentId);
                alert.setAlertType("SLA_BREACH");
                alert.setSeverity("HIGH");
                alert.setMessage(String.format("SLA breached: %s exceeded by %d minutes (%.1f%%)", 
                        slaType, actualMinutes - expectedMinutes, breachPercentage));
                alert.setSentAt(LocalDateTime.now());
                
                incidentAlertRepository.save(alert);
                
                notifySlaBreachEvent(incident, slaType, actualMinutes - expectedMinutes, breachPercentage);
                
                if (breachPercentage > 50) {
                    escalateIncident(incidentId, "SLA breach > 50%");
                }
                
                logger.warn("Processed SLA breach: incidentId={}, type={}, breach={}%", 
                        incidentId, slaType, breachPercentage);
            }
            
        } catch (Exception e) {
            logger.error("Error processing SLA breach: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processIncidentMerge(JsonNode eventData) {
        try {
            String primaryIncidentId = eventData.path("primaryIncidentId").asText();
            String mergedIncidentId = eventData.path("mergedIncidentId").asText();
            String mergeReason = eventData.path("mergeReason").asText();
            String mergedBy = eventData.path("mergedBy").asText();
            
            Incident primaryIncident = incidentRepository.findById(primaryIncidentId).orElse(null);
            Incident mergedIncident = incidentRepository.findById(mergedIncidentId).orElse(null);
            
            if (primaryIncident != null && mergedIncident != null) {
                mergedIncident.setStatus("MERGED");
                mergedIncident.setMergedInto(primaryIncidentId);
                mergedIncident.setLastUpdatedAt(LocalDateTime.now());
                incidentRepository.save(mergedIncident);
                
                activeIncidents.remove(mergedIncidentId);
                
                addTimelineEvent(primaryIncidentId, "INCIDENT_MERGED", 
                        String.format("Merged with incident %s: %s", mergedIncidentId, mergeReason), mergedBy);
                
                metricsService.recordIncidentMerge(primaryIncident.getServiceName());
                
                logger.info("Processed incident merge: primary={}, merged={}, reason={}", 
                        primaryIncidentId, mergedIncidentId, mergeReason);
            }
            
        } catch (Exception e) {
            logger.error("Error processing incident merge: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processWarRoomActivation(JsonNode eventData) {
        try {
            String incidentId = eventData.path("incidentId").asText();
            String warRoomId = eventData.path("warRoomId").asText();
            String bridgeDetails = eventData.path("bridgeDetails").asText();
            String participants = eventData.path("participants").asText();
            LocalDateTime activatedAt = parseTimestamp(eventData.path("activatedAt").asText());
            
            Incident incident = incidentRepository.findById(incidentId).orElse(null);
            if (incident != null) {
                incident.setWarRoomActive(true);
                incident.setWarRoomId(warRoomId);
                incident.setWarRoomDetails(bridgeDetails);
                incidentRepository.save(incident);
                
                addTimelineEvent(incidentId, "WAR_ROOM_ACTIVATED", 
                        String.format("War room activated: %s", bridgeDetails), "System");
                
                notifyWarRoomActivation(incident, warRoomId, bridgeDetails, participants);
                
                metricsService.recordWarRoomActivation(incident.getServiceName(), incident.getSeverity());
                
                logger.warn("War room activated for incident: id={}, warRoomId={}, participants={}", 
                        incidentId, warRoomId, participants);
            }
            
        } catch (Exception e) {
            logger.error("Error processing war room activation: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processIncidentReopened(JsonNode eventData) {
        try {
            String incidentId = eventData.path("incidentId").asText();
            String reopenReason = eventData.path("reopenReason").asText();
            String reopenedBy = eventData.path("reopenedBy").asText();
            LocalDateTime reopenedAt = parseTimestamp(eventData.path("reopenedAt").asText());
            
            Incident incident = incidentRepository.findById(incidentId).orElse(null);
            if (incident != null) {
                incident.setStatus("REOPENED");
                incident.setReopenCount(incident.getReopenCount() + 1);
                incident.setLastReopenedAt(reopenedAt);
                incident.setReopenReason(reopenReason);
                incident.setLastUpdatedAt(LocalDateTime.now());
                incidentRepository.save(incident);
                
                IncidentState state = new IncidentState(incident);
                activeIncidents.put(incidentId, state);
                
                addTimelineEvent(incidentId, "REOPENED", reopenReason, reopenedBy);
                
                notifyIncidentReopened(incident, reopenReason, reopenedBy);
                
                if (incident.getReopenCount() > 2) {
                    escalateIncident(incidentId, "Incident reopened multiple times");
                }
                
                metricsService.recordIncidentReopened(incident.getServiceName(), incident.getReopenCount());
                
                logger.warn("Incident reopened: id={}, count={}, reason={}", 
                        incidentId, incident.getReopenCount(), reopenReason);
            }
            
        } catch (Exception e) {
            logger.error("Error processing incident reopened: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void checkForEscalations() {
        LocalDateTime now = LocalDateTime.now();
        
        for (Map.Entry<String, IncidentState> entry : activeIncidents.entrySet()) {
            String incidentId = entry.getKey();
            IncidentState state = entry.getValue();
            
            Duration age = Duration.between(state.getCreatedAt(), now);
            
            if (shouldEscalate(state, age)) {
                escalateIncident(incidentId, "Auto-escalation due to age");
            }
        }
    }

    private void checkSlaCompliance() {
        for (Map.Entry<String, IncidentState> entry : activeIncidents.entrySet()) {
            String incidentId = entry.getKey();
            IncidentState state = entry.getValue();
            
            checkIncidentSla(incidentId, state);
        }
    }

    private void analyzeIncidentPatterns() {
        try {
            logger.info("Analyzing incident patterns");
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime thirtyDaysAgo = now.minusDays(30);
            
            List<Incident> recentIncidents = incidentRepository.findByCreatedAtBetween(thirtyDaysAgo, now);
            
            Map<String, List<Incident>> serviceIncidents = recentIncidents.stream()
                    .collect(Collectors.groupingBy(Incident::getServiceName));
            
            for (Map.Entry<String, List<Incident>> entry : serviceIncidents.entrySet()) {
                String serviceName = entry.getKey();
                List<Incident> incidents = entry.getValue();
                
                analyzeServiceIncidentPatterns(serviceName, incidents);
            }
            
        } catch (Exception e) {
            logger.error("Error analyzing incident patterns: {}", e.getMessage(), e);
        }
    }

    private void cleanupOldData() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        
        try {
            int deletedIncidents = incidentRepository.deleteByResolvedAtBefore(cutoff);
            int deletedTimelines = incidentTimelineRepository.deleteByEventTimeBefore(cutoff);
            int deletedAlerts = incidentAlertRepository.deleteByCreatedAtBefore(cutoff);
            
            logger.info("Cleaned up old incident data: {} incidents, {} timelines, {} alerts", 
                    deletedIncidents, deletedTimelines, deletedAlerts);
            
        } catch (Exception e) {
            logger.error("Error cleaning up old incident data: {}", e.getMessage(), e);
        }
    }

    private void addTimelineEvent(String incidentId, String eventType, String description, String actor) {
        IncidentTimeline timeline = new IncidentTimeline();
        timeline.setIncidentId(incidentId);
        timeline.setEventType(eventType);
        timeline.setEventDescription(description);
        timeline.setEventTime(LocalDateTime.now());
        timeline.setActor(actor);
        
        incidentTimelineRepository.save(timeline);
    }

    private boolean checkSlaCompliance(String severity, long resolutionMinutes) {
        return switch (severity) {
            case "CRITICAL" -> resolutionMinutes <= slaCriticalMinutes;
            case "HIGH" -> resolutionMinutes <= slaHighMinutes;
            case "MEDIUM" -> resolutionMinutes <= slaMediumMinutes;
            default -> true;
        };
    }

    private void updateIncidentStatistics(Incident incident) {
        IncidentStatistics stats = serviceIncidentStats.computeIfAbsent(incident.getServiceName(), 
                k -> new IncidentStatistics());
        stats.recordIncident(incident);
    }

    private void updateServiceIncidentStatistics(IncidentMetrics metrics) {
        IncidentStatistics stats = serviceIncidentStats.computeIfAbsent(metrics.getServiceName(), 
                k -> new IncidentStatistics());
        stats.updateFromMetrics(metrics);
    }

    private void notifyIncidentCreation(Incident incident) {
        notificationService.sendIncidentNotification("INCIDENT_CREATED", 
                String.format("New incident: %s (%s severity)", incident.getTitle(), incident.getSeverity()),
                Map.of("incidentId", incident.getIncidentId(), "serviceName", incident.getServiceName()));
    }

    private void notifyIncidentUpdate(Incident incident, String updateType, String description) {
        notificationService.sendIncidentNotification("INCIDENT_UPDATED", 
                String.format("Incident updated: %s - %s", incident.getIncidentId(), description),
                Map.of("incidentId", incident.getIncidentId(), "updateType", updateType));
    }

    private void notifyIncidentResolution(Incident incident, IncidentResolution resolution) {
        notificationService.sendIncidentNotification("INCIDENT_RESOLVED", 
                String.format("Incident resolved: %s (duration: %d min)", 
                        incident.getTitle(), resolution.getResolutionTimeMinutes()),
                Map.of("incidentId", incident.getIncidentId(), "slaMet", String.valueOf(resolution.isSlaMet())));
    }

    private void notifyEscalation(Incident incident, IncidentEscalation escalation) {
        notificationService.sendEscalationNotification("INCIDENT_ESCALATED", 
                String.format("Incident escalated: %s to %s", incident.getIncidentId(), escalation.getEscalatedTo()),
                Map.of("incidentId", incident.getIncidentId(), "level", escalation.getEscalationLevel()));
    }

    private void notifySlaBreachEvent(Incident incident, String slaType, long breachMinutes, double breachPercentage) {
        alertingService.sendCriticalAlert("SLA_BREACH", 
                String.format("SLA breached for incident %s: %s exceeded by %d minutes (%.1f%%)", 
                        incident.getIncidentId(), slaType, breachMinutes, breachPercentage),
                Map.of("incidentId", incident.getIncidentId(), "slaType", slaType));
    }

    private void notifyWarRoomActivation(Incident incident, String warRoomId, String bridgeDetails, String participants) {
        notificationService.sendEmergencyNotification("WAR_ROOM_ACTIVATED", 
                String.format("War room activated for critical incident: %s", incident.getTitle()),
                Map.of("incidentId", incident.getIncidentId(), "warRoomId", warRoomId, 
                       "bridgeDetails", bridgeDetails, "participants", participants));
    }

    private void notifyIncidentReopened(Incident incident, String reason, String reopenedBy) {
        notificationService.sendIncidentNotification("INCIDENT_REOPENED", 
                String.format("Incident reopened: %s (count: %d) - %s", 
                        incident.getIncidentId(), incident.getReopenCount(), reason),
                Map.of("incidentId", incident.getIncidentId(), "reopenedBy", reopenedBy));
    }

    private void activateWarRoom(Incident incident) {
        processWarRoomActivation(createWarRoomActivationEvent(incident));
    }

    private void checkForCorrelatedIncidents(Incident incident) {
        LocalDateTime correlationWindow = incident.getCreatedAt().minusMinutes(correlationWindowMinutes);
        List<Incident> recentIncidents = incidentRepository.findByCreatedAtBetweenAndServiceName(
                correlationWindow, incident.getCreatedAt(), incident.getServiceName());
        
        for (Incident recent : recentIncidents) {
            if (!recent.getIncidentId().equals(incident.getIncidentId()) && 
                calculateCorrelation(incident, recent) > 0.8) {
                processIncidentCorrelation(createCorrelationEvent(incident, recent));
            }
        }
    }

    private void escalateIncident(String incidentId, String reason) {
        Incident incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident != null) {
            String nextLevel = getNextEscalationLevel(incident.getEscalationLevel());
            processIncidentEscalated(createEscalationEvent(incident, nextLevel, reason));
        }
    }

    private void triggerEmergencyProtocol(Incident incident, IncidentEscalation escalation) {
        logger.error("Emergency protocol triggered for incident: {}", incident.getIncidentId());
        notificationService.sendPagerDutyAlert("EMERGENCY_PROTOCOL", 
                String.format("Emergency: %s - %s", incident.getTitle(), incident.getDescription()));
    }

    private void retryAlertDelivery(IncidentAlert alert) {
    }

    private void scheduleFollowUp(String incidentId, String preventiveMeasures) {
    }

    private void schedulePostmortem(Incident incident) {
        LocalDateTime scheduledDate = LocalDateTime.now().plusDays(3);
        processPostmortem(createPostmortemEvent(incident, scheduledDate));
    }

    private void extractAndShareLessonsLearned(Postmortem postmortem) {
    }

    private void mergeIncidents(String primaryId, String mergedId) {
        processIncidentMerge(createMergeEvent(primaryId, mergedId, "System correlation"));
    }

    private void checkIncidentSla(String incidentId, IncidentState state) {
        Duration age = Duration.between(state.getCreatedAt(), LocalDateTime.now());
        long ageMinutes = age.toMinutes();
        
        long slaMinutes = getSlaMinutes(state.getSeverity());
        
        if (ageMinutes > slaMinutes && !state.isSlaBreached()) {
            double breachPercentage = ((double)(ageMinutes - slaMinutes) / slaMinutes) * 100;
            processSlaBreachEvent(createSlaBreachEvent(incidentId, state.getSeverity(), slaMinutes, ageMinutes, breachPercentage));
            state.setSlaBreached(true);
        }
    }

    private void analyzeServiceIncidentPatterns(String serviceName, List<Incident> incidents) {
    }

    private boolean shouldEscalate(IncidentState state, Duration age) {
        return age.toMinutes() > autoEscalationMinutes && 
               state.getEscalationLevel() == null;
    }

    private double calculateCorrelation(Incident incident1, Incident incident2) {
        double score = 0.0;
        
        if (incident1.getServiceName().equals(incident2.getServiceName())) score += 0.3;
        if (incident1.getSeverity().equals(incident2.getSeverity())) score += 0.2;
        if (incident1.getCategory().equals(incident2.getCategory())) score += 0.3;
        if (incident1.getRootCause() != null && incident1.getRootCause().equals(incident2.getRootCause())) score += 0.2;
        
        return score;
    }

    private String getNextEscalationLevel(String currentLevel) {
        if (currentLevel == null) return "L1";
        return switch (currentLevel) {
            case "L1" -> "L2";
            case "L2" -> "L3";
            case "L3" -> "EXECUTIVE";
            default -> "EXECUTIVE";
        };
    }

    private long getSlaMinutes(String severity) {
        return switch (severity) {
            case "CRITICAL" -> slaCriticalMinutes;
            case "HIGH" -> slaHighMinutes;
            case "MEDIUM" -> slaMediumMinutes;
            default -> 1440; // 24 hours for low
        };
    }

    private JsonNode createWarRoomActivationEvent(Incident incident) {
        return objectMapper.createObjectNode();
    }

    private JsonNode createCorrelationEvent(Incident primary, Incident correlated) {
        return objectMapper.createObjectNode();
    }

    private JsonNode createEscalationEvent(Incident incident, String level, String reason) {
        return objectMapper.createObjectNode();
    }

    private JsonNode createPostmortemEvent(Incident incident, LocalDateTime scheduledDate) {
        return objectMapper.createObjectNode();
    }

    private JsonNode createMergeEvent(String primaryId, String mergedId, String reason) {
        return objectMapper.createObjectNode();
    }

    private JsonNode createSlaBreachEvent(String incidentId, String severity, long expected, long actual, double breach) {
        return objectMapper.createObjectNode();
    }

    private double getActiveIncidentsCount() {
        return activeIncidents.size();
    }

    private double getCriticalIncidentsCount() {
        return activeIncidents.values().stream()
                .filter(state -> "CRITICAL".equals(state.getSeverity()))
                .count();
    }

    private double getAverageMttr() {
        return serviceIncidentStats.values().stream()
                .mapToDouble(IncidentStatistics::getAverageMttr)
                .filter(mttr -> mttr > 0)
                .average()
                .orElse(0.0);
    }

    private double getSlaCompliance() {
        return serviceIncidentStats.values().stream()
                .mapToDouble(IncidentStatistics::getSlaCompliance)
                .filter(compliance -> compliance >= 0)
                .average()
                .orElse(100.0);
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp.replace("Z", ""));
        } catch (Exception e) {
            logger.warn("Failed to parse timestamp: {}, using current time", timestamp);
            return LocalDateTime.now();
        }
    }

    private static class IncidentState {
        private final String incidentId;
        private final LocalDateTime createdAt;
        private String status;
        private String severity;
        private String escalationLevel;
        private boolean slaBreached;
        
        public IncidentState(Incident incident) {
            this.incidentId = incident.getIncidentId();
            this.createdAt = incident.getCreatedAt();
            this.status = incident.getStatus();
            this.severity = incident.getSeverity();
            this.escalationLevel = incident.getEscalationLevel();
            this.slaBreached = incident.isSlaBreached();
        }
        
        public void update(Incident incident) {
            this.status = incident.getStatus();
            this.severity = incident.getSeverity();
            this.escalationLevel = incident.getEscalationLevel();
            this.slaBreached = incident.isSlaBreached();
        }
        
        public String getIncidentId() { return incidentId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public String getStatus() { return status; }
        public String getSeverity() { return severity; }
        public String getEscalationLevel() { return escalationLevel; }
        public boolean isSlaBreached() { return slaBreached; }
        public void setSlaBreached(boolean breached) { this.slaBreached = breached; }
    }

    private static class IncidentStatistics {
        private long totalIncidents = 0;
        private long resolvedIncidents = 0;
        private double totalResolutionTime = 0.0;
        private long slaMetCount = 0;
        private double averageMttr = 0.0;
        private double slaCompliance = 100.0;
        
        public void recordIncident(Incident incident) {
            totalIncidents++;
            if ("RESOLVED".equals(incident.getStatus())) {
                resolvedIncidents++;
                totalResolutionTime += incident.getResolutionTimeMinutes();
                updateAverageMttr();
            }
        }
        
        public void updateFromMetrics(IncidentMetrics metrics) {
            this.averageMttr = metrics.getAverageMttr();
            this.slaCompliance = metrics.getSlaCompliance();
        }
        
        private void updateAverageMttr() {
            if (resolvedIncidents > 0) {
                averageMttr = totalResolutionTime / resolvedIncidents;
            }
        }
        
        public double getAverageMttr() { return averageMttr; }
        public double getSlaCompliance() { return slaCompliance; }
    }
}