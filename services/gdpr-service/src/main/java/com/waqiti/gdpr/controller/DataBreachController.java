package com.waqiti.gdpr.controller;

import com.waqiti.gdpr.domain.*;
import com.waqiti.gdpr.repository.DataBreachRepository;
import com.waqiti.gdpr.service.DataBreachNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Breach Management Controller
 *
 * REST API for GDPR Articles 33-34 breach notification compliance.
 *
 * Endpoints:
 * - POST /api/v1/privacy/breaches - Report new breach
 * - GET /api/v1/privacy/breaches - List breaches
 * - GET /api/v1/privacy/breaches/{id} - Get breach details
 * - POST /api/v1/privacy/breaches/{id}/notify-regulatory - Notify supervisory authority
 * - POST /api/v1/privacy/breaches/{id}/notify-users - Notify affected users
 * - POST /api/v1/privacy/breaches/{id}/contain - Mark breach as contained
 * - POST /api/v1/privacy/breaches/{id}/resolve - Mark breach as resolved
 */
@RestController
@RequestMapping("/api/v1/privacy/breaches")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Data Breach Management", description = "GDPR Articles 33-34 - Data Breach Notification")
public class DataBreachController {

    private final DataBreachNotificationService breachService;
    private final DataBreachRepository breachRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('DPO', 'SECURITY_ADMIN', 'ADMIN')")
    @Operation(summary = "Report data breach", description = "Report a new data breach incident (Article 33-34)")
    public ResponseEntity<DataBreach> reportBreach(
            @Valid @RequestBody DataBreachNotificationService.DataBreachReport request) {

        log.info("API: Reporting data breach: type={}, severity={}",
                request.getBreachType(), request.getSeverity());

        DataBreach breach = breachService.reportDataBreach(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(breach);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DPO', 'SECURITY_ADMIN', 'ADMIN', 'AUDITOR')")
    @Operation(summary = "List data breaches")
    public ResponseEntity<List<DataBreach>> listBreaches(
            @RequestParam(required = false) BreachStatus status,
            @RequestParam(required = false) BreachSeverity severity) {

        List<DataBreach> breaches;

        if (status != null) {
            breaches = breachRepository.findByStatus(status);
        } else if (severity != null) {
            breaches = breachRepository.findBySeverity(severity);
        } else {
            breaches = breachRepository.findAll();
        }

        return ResponseEntity.ok(breaches);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DPO', 'SECURITY_ADMIN', 'ADMIN', 'AUDITOR')")
    @Operation(summary = "Get breach details")
    public ResponseEntity<DataBreach> getBreach(@PathVariable String id) {
        return breachRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/notify-regulatory")
    @PreAuthorize("hasAnyRole('DPO', 'ADMIN')")
    @Operation(summary = "Notify regulatory authority", description = "Notify supervisory authority within 72 hours (Article 33)")
    public ResponseEntity<DataBreach> notifyRegulatory(
            @PathVariable String id,
            @RequestParam String reference) {

        log.info("API: Notifying regulatory authority for breach: id={}, reference={}", id, reference);

        DataBreach breach = breachService.notifyRegulatoryAuthority(id, reference);

        return ResponseEntity.ok(breach);
    }

    @PostMapping("/{id}/notify-users")
    @PreAuthorize("hasAnyRole('DPO', 'ADMIN')")
    @Operation(summary = "Notify affected users", description = "Notify data subjects without undue delay if high risk (Article 34)")
    public ResponseEntity<DataBreach> notifyUsers(
            @PathVariable String id,
            @RequestParam int notificationCount,
            @RequestParam String method) {

        log.info("API: Notifying affected users for breach: id={}, count={}, method={}",
                id, notificationCount, method);

        DataBreach breach = breachService.notifyAffectedUsers(id, notificationCount, method);

        return ResponseEntity.ok(breach);
    }

    @PostMapping("/{id}/contain")
    @PreAuthorize("hasAnyRole('DPO', 'SECURITY_ADMIN', 'ADMIN')")
    @Operation(summary = "Mark breach as contained")
    public ResponseEntity<DataBreach> containBreach(
            @PathVariable String id,
            @RequestBody String containmentActions) {

        log.info("API: Marking breach as contained: id={}", id);

        DataBreach breach = breachService.containBreach(id, containmentActions);

        return ResponseEntity.ok(breach);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('DPO', 'SECURITY_ADMIN', 'ADMIN')")
    @Operation(summary = "Mark breach as resolved")
    public ResponseEntity<DataBreach> resolveBreach(
            @PathVariable String id,
            @RequestBody String recoveryActions) {

        log.info("API: Marking breach as resolved: id={}", id);

        DataBreach breach = breachService.resolveBreach(id, recoveryActions);

        return ResponseEntity.ok(breach);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('DPO', 'SECURITY_ADMIN', 'ADMIN')")
    @Operation(summary = "Get active breaches")
    public ResponseEntity<List<DataBreach>> getActiveBreaches() {
        List<DataBreach> active = breachRepository.findActiveBreaches();
        return ResponseEntity.ok(active);
    }

    @GetMapping("/critical")
    @PreAuthorize("hasAnyRole('DPO', 'SECURITY_ADMIN', 'ADMIN')")
    @Operation(summary = "Get critical/high severity active breaches")
    public ResponseEntity<List<DataBreach>> getCriticalBreaches() {
        List<DataBreach> critical = breachRepository.findCriticalActiveBreaches();
        return ResponseEntity.ok(critical);
    }

    @GetMapping("/regulatory-due")
    @PreAuthorize("hasAnyRole('DPO', 'ADMIN')")
    @Operation(summary = "Get breaches requiring regulatory notification")
    public ResponseEntity<List<DataBreach>> getRegulatoryDue() {
        List<DataBreach> due = breachRepository.findRegulatoryNotificationsDue(LocalDateTime.now());
        return ResponseEntity.ok(due);
    }

    @GetMapping("/user-notification-due")
    @PreAuthorize("hasAnyRole('DPO', 'ADMIN')")
    @Operation(summary = "Get breaches requiring user notification")
    public ResponseEntity<List<DataBreach>> getUserNotificationDue() {
        List<DataBreach> due = breachRepository.findUserNotificationsDue(LocalDateTime.now());
        return ResponseEntity.ok(due);
    }
}
