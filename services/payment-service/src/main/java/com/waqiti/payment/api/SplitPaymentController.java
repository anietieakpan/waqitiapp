package com.waqiti.payment.api;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.SplitPaymentService;
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
@RequestMapping("/api/v1/split-payments")
@RequiredArgsConstructor
@Slf4j
public class SplitPaymentController {
    private final SplitPaymentService splitPaymentService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SplitPaymentResponse> createSplitPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateSplitPaymentRequest request) {
        log.info("Create split payment received");
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(splitPaymentService.createSplitPayment(userId, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SplitPaymentResponse> getSplitPaymentById(@PathVariable UUID id) {
        log.info("Get split payment received for ID: {}", id);
        return ResponseEntity.ok(splitPaymentService.getSplitPaymentById(id));
    }

    @GetMapping("/organized")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<SplitPaymentResponse>> getSplitPaymentsByOrganizer(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        log.info("Get organized split payments received");
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(splitPaymentService.getSplitPaymentsByOrganizer(userId, pageable));
    }

    @GetMapping("/participating")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<SplitPaymentResponse>> getSplitPaymentsByParticipant(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        log.info("Get participating split payments received");
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(splitPaymentService.getSplitPaymentsByParticipant(userId, pageable));
    }

    @GetMapping("/{id}/statistics")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SplitPaymentStatisticsResponse> getSplitPaymentStatistics(@PathVariable UUID id) {
        log.info("Get split payment statistics received for ID: {}", id);
        return ResponseEntity.ok(splitPaymentService.getStatistics(id));
    }

    @PostMapping("/{id}/participants")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SplitPaymentResponse> addParticipant(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AddParticipantRequest request) {
        log.info("Add participant received for split payment ID: {}", id);
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(splitPaymentService.addParticipant(userId, id, request));
    }

    @DeleteMapping("/{id}/participants/{participantId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SplitPaymentResponse> removeParticipant(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @PathVariable UUID participantId) {
        log.info("Remove participant received for split payment ID: {}", id);
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(splitPaymentService.removeParticipant(userId, id, participantId));
    }

    @PutMapping("/{id}/participants/{participantId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SplitPaymentResponse> updateParticipantAmount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @PathVariable UUID participantId,
            @Valid @RequestBody UpdateParticipantAmountRequest request) {
        log.info("Update participant amount received for split payment ID: {}", id);
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(splitPaymentService.updateParticipantAmount(userId, id, participantId, request));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SplitPaymentResponse> payShare(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody PaySplitShareRequest request) {
        log.info("Pay share received for split payment ID: {}", id);
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(splitPaymentService.payShare(userId, id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SplitPaymentResponse> cancelSplitPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        log.info("Cancel split payment received for ID: {}", id);
        UUID userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(splitPaymentService.cancelSplitPayment(userId, id));
    }

    @PostMapping("/{id}/remind")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> sendReminder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        log.info("Send reminder request received for split payment ID: {}", id);
        UUID userId = getUserIdFromUserDetails(userDetails);
        splitPaymentService.sendReminder(userId, id);
        return ResponseEntity.ok().build();
    }

    /**
     * Helper method to extract user ID from UserDetails
     */
    private UUID getUserIdFromUserDetails(UserDetails userDetails) {
        return UUID.fromString(userDetails.getUsername());
    }
}