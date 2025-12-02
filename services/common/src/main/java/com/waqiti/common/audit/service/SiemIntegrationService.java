package com.waqiti.common.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.domain.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for integrating audit events with external SIEM systems
 * 
 * Provides integration with various SIEM systems including:
 * - Splunk
 * - ELK Stack (Elasticsearch, Logstash, Kibana)
 * - Datadog
 * - AWS CloudWatch
 * - Azure Sentinel
 * - Generic syslog endpoints
 * 
 * FEATURES:
 * - Multiple SIEM format support
 * - Async delivery for performance
 * - Retry mechanisms for reliability
 * - Event enrichment for better analysis
 * - Compliance-specific formatting
 * - Real-time alerting for critical events
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SiemIntegrationService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final IncidentAlertingService incidentAlertingService;
    
    @Value("${waqiti.siem.enabled:true}")
    private boolean siemEnabled;
    
    @Value("${waqiti.siem.splunk.enabled:false}")
    private boolean splunkEnabled;
    
    @Value("${waqiti.siem.splunk.url:}")
    private String splunkUrl;
    
    @Value("${waqiti.siem.splunk.token:}")
    private String splunkToken;
    
    @Value("${waqiti.siem.elk.enabled:false}")
    private boolean elkEnabled;
    
    @Value("${waqiti.siem.elk.url:}")
    private String elkUrl;
    
    @Value("${waqiti.siem.datadog.enabled:false}")
    private boolean datadogEnabled;
    
    @Value("${waqiti.siem.datadog.api.key:}")
    private String datadogApiKey;
    
    @Value("${waqiti.siem.aws.cloudwatch.enabled:false}")
    private boolean cloudwatchEnabled;
    
    @Value("${waqiti.siem.azure.sentinel.enabled:false}")
    private boolean sentinelEnabled;
    
    @Value("${waqiti.siem.syslog.enabled:false}")
    private boolean syslogEnabled;
    
    @Value("${waqiti.siem.syslog.host:localhost}")
    private String syslogHost;
    
    @Value("${waqiti.siem.syslog.port:514}")
    private int syslogPort;
    
    /**
     * Send audit event to configured SIEM systems
     */
    @Async("siemExecutor")
    public CompletableFuture<Void> sendAuditEvent(AuditLog auditLog) {
        if (!siemEnabled) {
            log.debug("SIEM integration is disabled");
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            // Send to Kafka for internal processing
            sendToKafka(auditLog);
            
            // Send to external SIEM systems
            if (splunkEnabled) {
                sendToSplunk(auditLog);
            }
            
            if (elkEnabled) {
                sendToElasticSearch(auditLog);
            }
            
            if (datadogEnabled) {
                sendToDatadog(auditLog);
            }
            
            if (cloudwatchEnabled) {
                sendToCloudWatch(auditLog);
            }
            
            if (sentinelEnabled) {
                sendToAzureSentinel(auditLog);
            }
            
            if (syslogEnabled) {
                sendToSyslog(auditLog);
            }
            
            // Send critical alerts immediately
            if (isCriticalEvent(auditLog)) {
                sendCriticalAlert(auditLog);
            }
            
            log.debug("Audit event sent to SIEM systems: {}", auditLog.getId());
            
        } catch (Exception e) {
            log.error("Failed to send audit event to SIEM systems: {}", auditLog.getId(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Send to Kafka for internal event streaming
     */
    private void sendToKafka(AuditLog auditLog) {
        try {
            // Send to different topics based on event characteristics
            String topic = determineKafkaTopic(auditLog);
            kafkaTemplate.send(topic, auditLog.getId().toString(), auditLog);
            
            log.debug("Audit event sent to Kafka topic: {} for event: {}", topic, auditLog.getId());
        } catch (Exception e) {
            log.error("Failed to send audit event to Kafka: {}", auditLog.getId(), e);
        }
    }
    
    /**
     * Send to Splunk HTTP Event Collector
     */
    private void sendToSplunk(AuditLog auditLog) {
        try {
            Map<String, Object> splunkEvent = createSplunkEvent(auditLog);
            
            // Splunk HTTP Event Collector (HEC) integration
            if (splunkUrl == null || splunkUrl.isEmpty() || splunkToken == null || splunkToken.isEmpty()) {
                log.warn("Splunk URL or token not configured");
                return;
            }
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Splunk " + splunkToken);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            
            org.springframework.http.HttpEntity<Map<String, Object>> request = 
                new org.springframework.http.HttpEntity<>(splunkEvent, headers);
            
            restTemplate.postForEntity(splunkUrl + "/services/collector/event", request, String.class);
            
            log.debug("Audit event sent to Splunk: {}", auditLog.getId());
            
        } catch (Exception e) {
            log.error("Failed to send audit event to Splunk: {}", auditLog.getId(), e);
        }
    }
    
    /**
     * Send to Elasticsearch
     */
    private void sendToElasticSearch(AuditLog auditLog) {
        try {
            Map<String, Object> esDocument = createElasticsearchDocument(auditLog);
            
            // Elasticsearch REST API integration
            if (elkUrl == null || elkUrl.isEmpty()) {
                log.warn("Elasticsearch URL not configured");
                return;
            }
            
            // Index name with date rotation (audit-logs-YYYY-MM-DD)
            String indexName = "audit-logs-" + java.time.LocalDate.now();
            String esEndpoint = elkUrl + "/" + indexName + "/_doc/" + auditLog.getId();
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            
            org.springframework.http.HttpEntity<Map<String, Object>> request = 
                new org.springframework.http.HttpEntity<>(esDocument, headers);
            
            restTemplate.put(esEndpoint, request);
            
            log.debug("Audit event sent to Elasticsearch: {}", auditLog.getId());
            
        } catch (Exception e) {
            log.error("Failed to send audit event to Elasticsearch: {}", auditLog.getId(), e);
        }
    }
    
    /**
     * Send to Datadog
     */
    private void sendToDatadog(AuditLog auditLog) {
        try {
            Map<String, Object> datadogEvent = createDatadogEvent(auditLog);
            
            // Datadog Logs API integration
            if (datadogApiKey == null || datadogApiKey.isEmpty()) {
                log.warn("Datadog API key not configured");
                return;
            }
            
            String datadogUrl = "https://http-intake.logs.datadoghq.com/v1/input";
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("DD-API-KEY", datadogApiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            
            org.springframework.http.HttpEntity<Map<String, Object>> request = 
                new org.springframework.http.HttpEntity<>(datadogEvent, headers);
            
            restTemplate.postForEntity(datadogUrl, request, String.class);
            
            log.debug("Audit event sent to Datadog: {}", auditLog.getId());
            
        } catch (Exception e) {
            log.error("Failed to send audit event to Datadog: {}", auditLog.getId(), e);
        }
    }
    
    /**
     * Send to AWS CloudWatch
     */
    private void sendToCloudWatch(AuditLog auditLog) {
        try {
            // AWS CloudWatch Logs integration - now enabled with CloudWatch SDK dependency
            software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient cloudWatchClient = 
                software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient.builder()
                    .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                    .build();
            
            String logGroupName = "/waqiti/audit-logs";
            String logStreamName = "audit-" + java.time.LocalDate.now();
            
            // Ensure log group exists
            try {
                cloudWatchClient.describeLogGroups(builder -> builder.logGroupNamePrefix(logGroupName));
            } catch (software.amazon.awssdk.services.cloudwatchlogs.model.ResourceNotFoundException e) {
                // Create log group if it doesn't exist
                cloudWatchClient.createLogGroup(builder -> builder.logGroupName(logGroupName));
            }
            
            // Ensure log stream exists
            try {
                cloudWatchClient.describeLogStreams(builder -> 
                    builder.logGroupName(logGroupName).logStreamNamePrefix(logStreamName));
            } catch (software.amazon.awssdk.services.cloudwatchlogs.model.ResourceNotFoundException e) {
                // Create log stream if it doesn't exist
                cloudWatchClient.createLogStream(builder -> 
                    builder.logGroupName(logGroupName).logStreamName(logStreamName));
            }
            
            // Create enriched log event with metadata
            Map<String, Object> enrichedEvent = new HashMap<>();
            enrichedEvent.put("auditLog", auditLog);
            enrichedEvent.put("environment", System.getProperty("spring.profiles.active", "unknown"));
            enrichedEvent.put("service", "waqiti-fintech");
            enrichedEvent.put("version", "1.0.0");
            enrichedEvent.put("source", "siem-integration-service");
            
            // Create log event
            software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent logEvent = 
                software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent.builder()
                    .timestamp(System.currentTimeMillis())
                    .message(objectMapper.writeValueAsString(enrichedEvent))
                    .build();
            
            // Put log events
            software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest request = 
                software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest.builder()
                    .logGroupName(logGroupName)
                    .logStreamName(logStreamName)
                    .logEvents(logEvent)
                    .build();
            
            cloudWatchClient.putLogEvents(request);
            cloudWatchClient.close();
            
            log.debug("Successfully sent audit event to CloudWatch: {}", auditLog.getId());
            
        } catch (Exception e) {
            log.error("Failed to send audit event to CloudWatch: {}", auditLog.getId(), e);
            // Don't rethrow - this is a best-effort SIEM integration
        }
    }
    
    /**
     * Send to Azure Sentinel
     */
    private void sendToAzureSentinel(AuditLog auditLog) {
        try {
            Map<String, Object> sentinelEvent = createSentinelEvent(auditLog);
            
            // Azure Sentinel (Log Analytics) HTTP Data Collector API
            String workspaceId = System.getenv("AZURE_SENTINEL_WORKSPACE_ID");
            String sharedKey = System.getenv("AZURE_SENTINEL_SHARED_KEY");
            
            if (workspaceId == null || sharedKey == null) {
                log.warn("Azure Sentinel workspace ID or shared key not configured");
                return;
            }
            
            String sentinelUrl = String.format("https://%s.ods.opinsights.azure.com/api/logs?api-version=2016-04-01", workspaceId);
            String logType = "WaqitiAuditLog";
            
            // Create HTTP signature for Azure Sentinel
            String dateString = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
            String jsonBody = objectMapper.writeValueAsString(java.util.List.of(sentinelEvent));
            String signature = buildAzureSentinelSignature(sharedKey, dateString, jsonBody.length(), workspaceId);
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", signature);
            headers.set("Log-Type", logType);
            headers.set("x-ms-date", dateString);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            
            org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(jsonBody, headers);
            
            restTemplate.postForEntity(sentinelUrl, request, String.class);
            
            log.debug("Audit event sent to Azure Sentinel: {}", auditLog.getId());
            
        } catch (Exception e) {
            log.error("Failed to send audit event to Azure Sentinel: {}", auditLog.getId(), e);
        }
    }
    
    /**
     * Send to Syslog
     */
    private void sendToSyslog(AuditLog auditLog) {
        try {
            String syslogMessage = createSyslogMessage(auditLog);
            
            // Syslog RFC 5424 over UDP integration
            java.net.InetAddress address = java.net.InetAddress.getByName(syslogHost);
            byte[] messageBytes = syslogMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
            java.net.DatagramPacket packet = new java.net.DatagramPacket(
                messageBytes, messageBytes.length, address, syslogPort
            );
            
            java.net.DatagramSocket socket = new java.net.DatagramSocket();
            socket.send(packet);
            socket.close();
            
            log.debug("Audit event sent to Syslog: {}", auditLog.getId());
            
        } catch (Exception e) {
            log.error("Failed to send audit event to Syslog: {}", auditLog.getId(), e);
        }
    }
    
    /**
     * Determine Kafka topic based on event characteristics
     */
    private String determineKafkaTopic(AuditLog auditLog) {
        // Route to different topics based on event category and severity
        if (auditLog.getSeverity() == AuditLog.Severity.CRITICAL || 
            auditLog.getSeverity() == AuditLog.Severity.EMERGENCY) {
            return "audit-events-critical";
        }
        
        switch (auditLog.getEventCategory()) {
            case FINANCIAL:
                return "audit-events-financial";
            case SECURITY:
                return "audit-events-security";
            case DATA_ACCESS:
                return "audit-events-data-access";
            case FRAUD:
                return "audit-events-fraud";
            case COMPLIANCE:
                return "audit-events-compliance";
            default:
                return "audit-events-general";
        }
    }
    
    /**
     * Create Splunk-formatted event
     */
    private Map<String, Object> createSplunkEvent(AuditLog auditLog) {
        Map<String, Object> event = new HashMap<>();
        
        // Splunk event structure
        event.put("time", auditLog.getTimestamp().getEpochSecond());
        event.put("host", "waqiti-platform");
        event.put("source", "waqiti-audit");
        event.put("sourcetype", "audit_log");
        event.put("index", "waqiti_audit");
        
        // Event data
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("event_id", auditLog.getId());
        eventData.put("event_type", auditLog.getEventType());
        eventData.put("event_category", auditLog.getEventCategory());
        eventData.put("severity", auditLog.getSeverity());
        eventData.put("user_id", auditLog.getUserId());
        eventData.put("username", auditLog.getUsername());
        eventData.put("action", auditLog.getAction());
        eventData.put("description", auditLog.getDescription());
        eventData.put("ip_address", auditLog.getIpAddress());
        eventData.put("result", auditLog.getResult());
        eventData.put("compliance_flags", auditLog.getComplianceDescription());
        
        if (auditLog.getMetadata() != null) {
            eventData.put("metadata", auditLog.getMetadata());
        }
        
        event.put("event", eventData);
        
        return event;
    }
    
    /**
     * Create Elasticsearch document
     */
    private Map<String, Object> createElasticsearchDocument(AuditLog auditLog) {
        Map<String, Object> document = new HashMap<>();
        
        // Map all audit log fields
        document.put("@timestamp", auditLog.getTimestamp());
        document.put("event_id", auditLog.getId());
        document.put("sequence_number", auditLog.getSequenceNumber());
        document.put("event_type", auditLog.getEventType());
        document.put("event_category", auditLog.getEventCategory());
        document.put("severity", auditLog.getSeverity());
        document.put("user", Map.of(
            "id", auditLog.getUserId(),
            "name", auditLog.getUsername()
        ));
        document.put("action", auditLog.getAction());
        document.put("description", auditLog.getDescription());
        document.put("source", Map.of(
            "ip", auditLog.getIpAddress(),
            "user_agent", auditLog.getUserAgent()
        ));
        document.put("session_id", auditLog.getSessionId());
        document.put("correlation_id", auditLog.getCorrelationId());
        document.put("result", auditLog.getResult());
        document.put("compliance", Map.of(
            "pci_relevant", auditLog.getPciRelevant(),
            "gdpr_relevant", auditLog.getGdprRelevant(),
            "sox_relevant", auditLog.getSoxRelevant(),
            "soc2_relevant", auditLog.getSoc2Relevant()
        ));
        
        if (auditLog.getEntityType() != null) {
            document.put("entity", Map.of(
                "type", auditLog.getEntityType(),
                "id", auditLog.getEntityId()
            ));
        }
        
        if (auditLog.getRiskScore() != null && auditLog.getRiskScore() > 0) {
            document.put("risk_score", auditLog.getRiskScore());
        }
        
        return document;
    }
    
    /**
     * Create Datadog event
     */
    private Map<String, Object> createDatadogEvent(AuditLog auditLog) {
        Map<String, Object> event = new HashMap<>();
        
        event.put("title", "Audit Event: " + auditLog.getEventType());
        event.put("text", auditLog.getDescription());
        event.put("date_happened", auditLog.getTimestamp().getEpochSecond());
        event.put("priority", mapSeverityToDatadogPriority(auditLog.getSeverity()));
        event.put("alert_type", mapSeverityToDatadogAlertType(auditLog.getSeverity()));
        event.put("source_type_name", "waqiti-audit");
        
        // Tags for filtering and alerting
        event.put("tags", new String[]{
            "service:waqiti",
            "category:" + auditLog.getEventCategory().name().toLowerCase(),
            "severity:" + auditLog.getSeverity().name().toLowerCase(),
            "user:" + auditLog.getUserId(),
            "result:" + auditLog.getResult().name().toLowerCase()
        });
        
        return event;
    }
    
    /**
     * Create Azure Sentinel event
     */
    private Map<String, Object> createSentinelEvent(AuditLog auditLog) {
        Map<String, Object> event = new HashMap<>();
        
        // Common Event Format (CEF) for Sentinel
        event.put("TimeGenerated", auditLog.getTimestamp());
        event.put("EventID", auditLog.getId());
        event.put("EventType", auditLog.getEventType());
        event.put("EventCategory", auditLog.getEventCategory());
        event.put("Severity", auditLog.getSeverity());
        event.put("UserID", auditLog.getUserId());
        event.put("UserName", auditLog.getUsername());
        event.put("Action", auditLog.getAction());
        event.put("Description", auditLog.getDescription());
        event.put("SourceIP", auditLog.getIpAddress());
        event.put("UserAgent", auditLog.getUserAgent());
        event.put("Result", auditLog.getResult());
        event.put("ComplianceRelevant", auditLog.getComplianceDescription());
        
        return event;
    }
    
    /**
     * Create syslog message (RFC 5424 format)
     */
    private String createSyslogMessage(AuditLog auditLog) {
        int priority = mapSeverityToSyslogPriority(auditLog.getSeverity());
        String timestamp = auditLog.getTimestamp().toString();
        String hostname = "waqiti-platform";
        String appName = "waqiti-audit";
        String procId = "-";
        String msgId = auditLog.getEventType().name();
        
        String structuredData = String.format(
            "[audit eventId=\"%s\" category=\"%s\" severity=\"%s\" userId=\"%s\" result=\"%s\"]",
            auditLog.getId(),
            auditLog.getEventCategory(),
            auditLog.getSeverity(),
            auditLog.getUserId(),
            auditLog.getResult()
        );
        
        String message = auditLog.getDescription();
        
        return String.format("<%d>1 %s %s %s %s %s %s %s",
            priority, timestamp, hostname, appName, procId, msgId, structuredData, message);
    }
    
    /**
     * Check if event is critical and requires immediate attention
     */
    private boolean isCriticalEvent(AuditLog auditLog) {
        return auditLog.getSeverity() == AuditLog.Severity.CRITICAL ||
               auditLog.getSeverity() == AuditLog.Severity.EMERGENCY ||
               auditLog.getRequiresNotification() ||
               auditLog.getInvestigationRequired() ||
               (auditLog.getRiskScore() != null && auditLog.getRiskScore() > 80);
    }
    
    /**
     * Send critical alert for immediate attention
     */
    public void sendCriticalAlert(AuditLog auditLog) {
        try {
            // Send to critical alerts topic
            kafkaTemplate.send("audit-alerts-critical", auditLog.getId().toString(), auditLog);
            
            // Send incident alert through all configured channels
            incidentAlertingService.sendIncidentAlert(auditLog);
            
            log.warn("CRITICAL AUDIT ALERT: {} - {}", auditLog.getEventType(), auditLog.getDescription());
            
        } catch (Exception e) {
            log.error("Failed to send critical alert for audit event: {}", auditLog.getId(), e);
        }
    }
    
    /**
     * Map severity to Datadog priority
     */
    private String mapSeverityToDatadogPriority(AuditLog.Severity severity) {
        switch (severity) {
            case EMERGENCY:
            case CRITICAL:
                return "high";
            case HIGH:
            case MEDIUM:
                return "normal";
            default:
                return "low";
        }
    }
    
    /**
     * Map severity to Datadog alert type
     */
    private String mapSeverityToDatadogAlertType(AuditLog.Severity severity) {
        switch (severity) {
            case EMERGENCY:
            case CRITICAL:
                return "error";
            case HIGH:
                return "warning";
            default:
                return "info";
        }
    }
    
    /**
     * Map severity to syslog priority
     */
    private int mapSeverityToSyslogPriority(AuditLog.Severity severity) {
        // Facility: local use 0 (16), Severity: mapped below
        int facility = 16;
        int severityCode;
        
        switch (severity) {
            case EMERGENCY:
                severityCode = 0; // Emergency
                break;
            case CRITICAL:
                severityCode = 2; // Critical
                break;
            case HIGH:
                severityCode = 3; // Error
                break;
            case MEDIUM:
                severityCode = 4; // Warning
                break;
            case LOW:
                severityCode = 5; // Notice
                break;
            default:
                severityCode = 6; // Informational
                break;
        }
        
        return facility * 8 + severityCode;
    }
    
    /**
     * Build Azure Sentinel authentication signature
     */
    private String buildAzureSentinelSignature(String sharedKey, String date, int contentLength, String workspaceId) {
        try {
            String stringToHash = "POST\n" + contentLength + "\napplication/json\nx-ms-date:" + date + "\n/api/logs";
            
            byte[] decodedKey = java.util.Base64.getDecoder().decode(sharedKey);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(decodedKey, "HmacSHA256");
            mac.init(secretKey);
            
            byte[] hash = mac.doFinal(stringToHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String encodedHash = java.util.Base64.getEncoder().encodeToString(hash);
            
            return "SharedKey " + workspaceId + ":" + encodedHash;
            
        } catch (Exception e) {
            log.error("Failed to build Azure Sentinel signature", e);
            return "";
        }
    }
}