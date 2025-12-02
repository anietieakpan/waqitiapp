package com.waqiti.analytics.api;

import com.waqiti.analytics.service.RealTimeAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/analytics/real-time")
@RequiredArgsConstructor
@Slf4j
public class RealTimeAnalyticsController {

    private final RealTimeAnalyticsService realTimeService;

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getRealTimeMetrics() {
        log.info("Getting real-time metrics");
        
        Map<String, Object> metrics = realTimeService.getRealTimeMetrics();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public SseEmitter streamRealTimeMetrics(@RequestParam(required = false) List<String> metrics) {
        log.info("Starting real-time metrics stream for metrics: {}", metrics);
        
        return realTimeService.createMetricsStream(metrics);
    }

    @GetMapping("/transactions/live")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getLiveTransactionMetrics() {
        log.info("Getting live transaction metrics");
        
        Map<String, Object> liveMetrics = realTimeService.getLiveTransactionMetrics();
        return ResponseEntity.ok(liveMetrics);
    }

    @GetMapping(value = "/transactions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public SseEmitter streamTransactionEvents() {
        log.info("Starting transaction events stream");
        
        return realTimeService.createTransactionEventStream();
    }

    @GetMapping("/users/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getActiveUsers(
            @RequestParam(defaultValue = "5") int minutes) {
        log.info("Getting active users in last {} minutes", minutes);
        
        Map<String, Object> activeUsers = realTimeService.getActiveUsers(minutes);
        return ResponseEntity.ok(activeUsers);
    }

    @GetMapping("/system/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        log.info("Getting real-time system health metrics");
        
        Map<String, Object> systemHealth = realTimeService.getSystemHealthMetrics();
        return ResponseEntity.ok(systemHealth);
    }

    @GetMapping(value = "/system/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
    public SseEmitter streamSystemMetrics() {
        log.info("Starting system metrics stream");
        
        return realTimeService.createSystemMetricsStream();
    }

    @GetMapping("/alerts/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<List<Map<String, Object>>> getActiveAlerts(
            @RequestParam(required = false) String severity) {
        log.info("Getting active alerts with severity: {}", severity);
        
        List<Map<String, Object>> alerts = realTimeService.getActiveAlerts(severity);
        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/alerts/acknowledge/{alertId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> acknowledgeAlert(
            @PathVariable String alertId,
            @RequestBody @Valid AlertAcknowledgeRequest request) {
        log.info("Acknowledging alert: {} by {}", alertId, request.getAcknowledgedBy());
        
        realTimeService.acknowledgeAlert(alertId, request.getAcknowledgedBy(), request.getNotes());
        
        return ResponseEntity.ok(Map.of(
            "alertId", alertId,
            "status", "acknowledged",
            "acknowledgedBy", request.getAcknowledgedBy()
        ));
    }

    @GetMapping("/performance/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getCurrentPerformance() {
        log.info("Getting current performance metrics");
        
        Map<String, Object> performance = realTimeService.getCurrentPerformanceMetrics();
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/events/recent")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<List<Map<String, Object>>> getRecentEvents(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String eventType) {
        log.info("Getting recent {} events of type: {}", limit, eventType);
        
        List<Map<String, Object>> events = realTimeService.getRecentEvents(limit, eventType);
        return ResponseEntity.ok(events);
    }

    @PostMapping("/events/track")
    @PreAuthorize("hasAnyRole('SYSTEM', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> trackEvent(@RequestBody @Valid EventTrackingRequest request) {
        log.info("Tracking event: {} for user: {}", request.getEventType(), request.getUserId());
        
        String eventId = realTimeService.trackEvent(
            request.getEventType(),
            request.getUserId(),
            request.getProperties(),
            request.getTimestamp()
        );
        
        return ResponseEntity.ok(Map.of(
            "eventId", eventId,
            "status", "tracked",
            "eventType", request.getEventType()
        ));
    }

    @GetMapping("/aggregations/live")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getLiveAggregations(
            @RequestParam(required = false) List<String> metrics,
            @RequestParam(defaultValue = "1") int windowMinutes) {
        log.info("Getting live aggregations for metrics: {} with window: {} minutes", metrics, windowMinutes);
        
        Map<String, Object> aggregations = realTimeService.getLiveAggregations(metrics, windowMinutes);
        return ResponseEntity.ok(aggregations);
    }

    @PostMapping("/dashboards/create")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> createRealTimeDashboard(@RequestBody @Valid DashboardRequest request) {
        log.info("Creating real-time dashboard: {}", request.getDashboardName());
        
        String dashboardId = realTimeService.createRealTimeDashboard(
            request.getDashboardName(),
            request.getWidgets(),
            request.getRefreshInterval(),
            request.getCreatedBy()
        );
        
        return ResponseEntity.ok(Map.of(
            "dashboardId", dashboardId,
            "dashboardName", request.getDashboardName(),
            "status", "created"
        ));
    }

    @GetMapping("/dashboards/{dashboardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getRealTimeDashboard(@PathVariable String dashboardId) {
        log.info("Getting real-time dashboard: {}", dashboardId);
        
        Map<String, Object> dashboard = realTimeService.getRealTimeDashboard(dashboardId);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping(value = "/dashboards/{dashboardId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public SseEmitter streamDashboardData(@PathVariable String dashboardId) {
        log.info("Starting dashboard data stream for: {}", dashboardId);
        
        return realTimeService.createDashboardStream(dashboardId);
    }

    @GetMapping("/thresholds")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getAlertThresholds() {
        log.info("Getting alert thresholds");
        
        Map<String, Object> thresholds = realTimeService.getAlertThresholds();
        return ResponseEntity.ok(thresholds);
    }

    @PostMapping("/thresholds/update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateAlertThresholds(@RequestBody @Valid ThresholdUpdateRequest request) {
        log.info("Updating alert thresholds");
        
        realTimeService.updateAlertThresholds(request.getThresholds());
        
        return ResponseEntity.ok(Map.of(
            "status", "updated",
            "message", "Alert thresholds updated successfully"
        ));
    }

    @GetMapping("/predictions/live")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getLivePredictions(
            @RequestParam String metric,
            @RequestParam(defaultValue = "60") int forecastMinutes) {
        log.info("Getting live predictions for metric: {} for {} minutes", metric, forecastMinutes);
        
        CompletableFuture<Map<String, Object>> predictions = realTimeService.getLivePredictions(metric, forecastMinutes);
        
        return ResponseEntity.ok(Map.of(
            "status", "processing",
            "metric", metric,
            "forecastMinutes", forecastMinutes,
            "requestId", java.util.UUID.randomUUID().toString()
        ));
    }

    @GetMapping("/anomalies/live")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<List<Map<String, Object>>> getLiveAnomalies(
            @RequestParam(defaultValue = "30") int minutes) {
        log.info("Getting live anomalies in last {} minutes", minutes);

        List<Map<String, Object>> anomalies = realTimeService.getLiveAnomalies(minutes);
        return ResponseEntity.ok(anomalies);
    }

    /**
     * NEW: Stream anomaly alerts via SSE
     * Migrated from real-time-analytics-service
     */
    @GetMapping(value = "/anomalies/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public SseEmitter streamAnomalies(@RequestParam(required = false) String severity) {
        log.info("Starting anomaly stream with severity filter: {}", severity);

        return realTimeService.createAnomalyStream(severity);
    }

    /**
     * NEW: Stream alerts via SSE
     * Migrated from real-time-analytics-service
     */
    @GetMapping(value = "/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public SseEmitter streamAlerts() {
        log.info("Starting alert stream");

        return realTimeService.createAlertStream();
    }

    // ========== STREAM PROCESSING CONTROL ==========
    // Migrated from real-time-analytics-service

    /**
     * NEW: Start stream processing
     */
    @PostMapping("/streams/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> startStreamProcessing(@RequestBody @Valid StartStreamRequest request) {
        log.info("Starting stream processing for: {}", request.getStreamName());

        String streamId = realTimeService.startStreamProcessing(
            request.getStreamName(),
            request.getConfiguration()
        );

        return ResponseEntity.ok(Map.of(
            "streamId", streamId,
            "streamName", request.getStreamName(),
            "status", "started"
        ));
    }

    /**
     * NEW: Stop stream processing
     */
    @PostMapping("/streams/stop")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> stopStreamProcessing(@RequestParam String streamName) {
        log.info("Stopping stream processing for: {}", streamName);

        realTimeService.stopStreamProcessing(streamName);

        return ResponseEntity.ok(Map.of(
            "streamName", streamName,
            "status", "stopped"
        ));
    }

    /**
     * NEW: Get status of all streams
     */
    @GetMapping("/streams/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
    public ResponseEntity<Map<String, Map<String, Object>>> getStreamStatus() {
        log.info("Retrieving status of all streams");

        Map<String, Map<String, Object>> streamStatus = realTimeService.getAllStreamStatus();
        return ResponseEntity.ok(streamStatus);
    }

    // ========== DATA INGESTION ==========
    // Migrated from real-time-analytics-service

    /**
     * NEW: Ingest real-time events
     */
    @PostMapping("/ingest/events")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<Map<String, Object>> ingestEvents(@RequestBody @Valid List<Map<String, Object>> events) {
        log.info("Ingesting {} events", events.size());

        int processed = realTimeService.ingestEvents(events);

        return ResponseEntity.ok(Map.of(
            "totalEvents", events.size(),
            "processedEvents", processed,
            "status", "ingested"
        ));
    }

    /**
     * NEW: Ingest real-time metrics
     */
    @PostMapping("/ingest/metrics")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<Map<String, Object>> ingestMetrics(@RequestBody @Valid List<Map<String, Object>> metrics) {
        log.info("Ingesting {} metrics", metrics.size());

        int processed = realTimeService.ingestMetrics(metrics);

        return ResponseEntity.ok(Map.of(
            "totalMetrics", metrics.size(),
            "processedMetrics", processed,
            "status", "ingested"
        ));
    }

    // ========== LIVE QUERY INTERFACE ==========
    // Migrated from real-time-analytics-service

    /**
     * NEW: Execute live data query
     */
    @PostMapping("/query/live")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> executeLiveQuery(@RequestBody @Valid LiveQueryRequest request) {
        log.info("Executing live query: {}", request.getQueryName());

        CompletableFuture<Map<String, Object>> result = realTimeService.executeLiveQuery(
            request.getQueryName(),
            request.getParameters(),
            request.getTimeWindow()
        );

        return ResponseEntity.ok(Map.of(
            "queryId", java.util.UUID.randomUUID().toString(),
            "queryName", request.getQueryName(),
            "status", "processing"
        ));
    }

    /**
     * NEW: Get available query templates
     */
    @GetMapping("/query/templates")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<List<Map<String, Object>>> getQueryTemplates() {
        log.info("Retrieving query templates");

        List<Map<String, Object>> templates = realTimeService.getQueryTemplates();
        return ResponseEntity.ok(templates);
    }

    /**
     * NEW: Get historical events with real-time context
     */
    @GetMapping("/historical/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<List<Map<String, Object>>> getHistoricalEvents(
            @RequestParam Long startTime,
            @RequestParam Long endTime,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "100") int limit) {
        log.info("Retrieving historical events from {} to {} for type: {}", startTime, endTime, eventType);

        List<Map<String, Object>> events = realTimeService.getHistoricalEvents(
            startTime, endTime, eventType, limit
        );
        return ResponseEntity.ok(events);
    }
}

// ========== NEW REQUEST DTOs (Migrated from real-time-analytics-service) ==========

