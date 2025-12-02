package com.waqiti.security.api;

import com.waqiti.security.domain.SecurityEvent;
import com.waqiti.security.domain.SecurityEventType;
import com.waqiti.security.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/security/audit")
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditController {

    private final AuditTrailService auditTrailService;

    @PostMapping("/events")
    @PreAuthorize("hasAnyRole('SYSTEM', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> logSecurityEvent(@RequestBody @Valid SecurityEventRequest request) {
        log.info("Logging security event: type={}, userId={}", request.getEventType(), request.getUserId());
        
        SecurityEvent event = SecurityEvent.builder()
            .userId(request.getUserId())
            .eventType(request.getEventType())
            .description(request.getDescription())
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .metadata(request.getMetadata())
            .build();
        
        String eventId = auditTrailService.logEvent(event);
        
        return ResponseEntity.ok(Map.of(
            "eventId", eventId,
            "status", "logged",
            "timestamp", LocalDateTime.now()
        ));
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public ResponseEntity<List<SecurityEvent>> getSecurityEvents(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) SecurityEventType eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.info("Fetching security events: userId={}, type={}, from={}, to={}", userId, eventType, startTime, endTime);
        
        List<SecurityEvent> events = auditTrailService.getEvents(userId, eventType, startTime, endTime, page, size);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public ResponseEntity<SecurityEvent> getSecurityEvent(@PathVariable String eventId) {
        log.info("Fetching security event: {}", eventId);
        
        SecurityEvent event = auditTrailService.getEvent(eventId);
        return ResponseEntity.ok(event);
    }

    @GetMapping("/trail/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'COMPLIANCE')")
    public ResponseEntity<UserAuditTrail> getUserAuditTrail(
            @PathVariable String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        log.info("Fetching audit trail for user: {}", userId);
        
        UserAuditTrail trail = auditTrailService.getUserAuditTrail(userId, startTime, endTime, page, size);
        return ResponseEntity.ok(trail);
    }

    @GetMapping("/login-history/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<LoginHistory>> getLoginHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "30") int days) {
        log.info("Fetching login history for user: {} for last {} days", userId, days);
        
        List<LoginHistory> history = auditTrailService.getLoginHistory(userId, days);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/access-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AccessLog>> getAccessLogs(
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        log.info("Fetching access logs: resource={}, action={}", resource, action);
        
        List<AccessLog> logs = auditTrailService.getAccessLogs(resource, action, startTime, endTime, page, size);
        return ResponseEntity.ok(logs);
    }

    @PostMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public ResponseEntity<Map<String, Object>> exportAuditData(@RequestBody @Valid AuditExportRequest request) {
        log.info("Exporting audit data: format={}, from={}, to={}", request.getFormat(), request.getStartTime(), request.getEndTime());
        
        String exportId = auditTrailService.exportAuditData(
            request.getFormat(),
            request.getStartTime(),
            request.getEndTime(),
            request.getFilters()
        );
        
        return ResponseEntity.ok(Map.of(
            "exportId", exportId,
            "status", "processing",
            "format", request.getFormat(),
            "message", "Export request has been queued. You will be notified when ready."
        ));
    }

    @GetMapping("/export/{exportId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public ResponseEntity<ExportStatus> getExportStatus(@PathVariable String exportId) {
        log.info("Checking export status: {}", exportId);
        
        ExportStatus status = auditTrailService.getExportStatus(exportId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuditStatistics> getAuditStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("Getting audit statistics from {} to {}", startTime, endTime);
        
        AuditStatistics stats = auditTrailService.getStatistics(startTime, endTime);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/retention-policy")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateRetentionPolicy(@RequestBody @Valid RetentionPolicyRequest request) {
        log.info("Updating retention policy: {} days", request.getRetentionDays());
        
        auditTrailService.updateRetentionPolicy(request.getRetentionDays(), request.getArchiveEnabled());
        
        return ResponseEntity.ok(Map.of(
            "status", "updated",
            "retentionDays", request.getRetentionDays(),
            "archiveEnabled", request.getArchiveEnabled()
        ));
    }
}

@lombok.Data
@lombok.Builder
class SecurityEventRequest {
    private String userId;
    private SecurityEventType eventType;
    private String description;
    private String ipAddress;
    private String userAgent;
    private Map<String, Object> metadata;
}

@lombok.Data
@lombok.Builder
class UserAuditTrail {
    private String userId;
    private List<SecurityEvent> events;
    private Map<String, Integer> eventTypeCounts;
    private LocalDateTime oldestEvent;
    private LocalDateTime newestEvent;
    private int totalEvents;
}

@lombok.Data
@lombok.Builder
class LoginHistory {
    private String sessionId;
    private LocalDateTime loginTime;
    private LocalDateTime logoutTime;
    private String ipAddress;
    private String location;
    private String device;
    private boolean successful;
    private String failureReason;
}

@lombok.Data
@lombok.Builder
class AccessLog {
    private String logId;
    private String userId;
    private String resource;
    private String action;
    private boolean allowed;
    private String reason;
    private LocalDateTime timestamp;
    private Map<String, Object> context;
}

@lombok.Data
@lombok.Builder
class AuditExportRequest {
    private String format; // CSV, JSON, PDF
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Map<String, Object> filters;
}

@lombok.Data
@lombok.Builder
class ExportStatus {
    private String exportId;
    private String status; // QUEUED, PROCESSING, COMPLETED, FAILED
    private String downloadUrl;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private long sizeBytes;
    private String error;
}

@lombok.Data
@lombok.Builder
class AuditStatistics {
    private long totalEvents;
    private Map<SecurityEventType, Long> eventsByType;
    private Map<String, Long> topUsers;
    private Map<String, Long> topResources;
    private long failedLogins;
    private long suspiciousActivities;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
}

@lombok.Data
@lombok.Builder
class RetentionPolicyRequest {
    private int retentionDays;
    private boolean archiveEnabled;
}