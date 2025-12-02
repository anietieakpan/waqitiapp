package com.waqiti.payment.api;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.ScheduledPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scheduled-payments")
@RequiredArgsConstructor
@Slf4j
public class ScheduledPaymentController {
    private final ScheduledPaymentService scheduledPaymentService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScheduledPaymentResponse> createScheduledPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateScheduledPaymentRequest request) {
        log.info("Create scheduled payment received");
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(scheduledPaymentService.createScheduledPayment(userId, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScheduledPaymentResponse> getScheduledPaymentById(@PathVariable UUID id) {
        log.info("Get scheduled payment received for ID: {}", id);
        return ResponseEntity.ok(scheduledPaymentService.getScheduledPaymentById(id));
    }

    @GetMapping("/sent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ScheduledPaymentResponse>> getScheduledPaymentsBySender(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        log.info("Get sent scheduled payments received");
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(scheduledPaymentService.getScheduledPaymentsBySender(userId, pageable));
    }

    @GetMapping("/received")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ScheduledPaymentResponse>> getScheduledPaymentsByRecipient(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        log.info("Get received scheduled payments received");
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(scheduledPaymentService.getScheduledPaymentsByRecipient(userId, pageable));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScheduledPaymentResponse> pauseScheduledPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) PauseScheduledPaymentRequest request) {
        log.info("Pause scheduled payment received for ID: {}", id);
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(scheduledPaymentService.pauseScheduledPayment(
                userId, id, request != null ? request : new PauseScheduledPaymentRequest()));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScheduledPaymentResponse> resumeScheduledPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ResumeScheduledPaymentRequest request) {
        log.info("Resume scheduled payment received for ID: {}", id);
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(scheduledPaymentService.resumeScheduledPayment(
                userId, id, request != null ? request : new ResumeScheduledPaymentRequest()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScheduledPaymentResponse> cancelScheduledPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelScheduledPaymentRequest request) {
        log.info("Cancel scheduled payment received for ID: {}", id);
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(scheduledPaymentService.cancelScheduledPayment(
                userId, id, request != null ? request : new CancelScheduledPaymentRequest()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScheduledPaymentResponse> updateScheduledPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScheduledPaymentRequest request) {
        log.info("Update scheduled payment received for ID: {}", id);
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(scheduledPaymentService.updateScheduledPayment(userId, id, request));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScheduledPaymentResponse> executeScheduledPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        log.info("Execute scheduled payment received for ID: {}", id);
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(scheduledPaymentService.executeScheduledPayment(userId, id));
    }

    /**
     * Helper method to extract user ID from UserDetails
     */
    private UUID getUserIdFromUserDetails(UserDetails userDetails) {
        return UUID.fromString(userDetails.getUsername());
    }
}