package com.waqiti.grouppayment.controller;

import com.waqiti.grouppayment.dto.*;
import com.waqiti.grouppayment.service.GroupPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/v1/group-payments")
@RequiredArgsConstructor
public class GroupPaymentController {

    private final GroupPaymentService groupPaymentService;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_group-payment:create')")
    public ResponseEntity<GroupPaymentResponse> createGroupPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateGroupPaymentRequest request) {
        
        String userId = userDetails.getUsername();
        log.info("Creating group payment for user: {}", userId);
        
        GroupPaymentResponse response = groupPaymentService.createGroupPayment(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{groupPaymentId}")
    @PreAuthorize("hasAuthority('SCOPE_group-payment:read') or hasRole('USER')")
    public ResponseEntity<GroupPaymentResponse> getGroupPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String groupPaymentId) {
        
        String userId = userDetails.getUsername();
        GroupPaymentResponse response = groupPaymentService.getGroupPayment(groupPaymentId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_group-payment:read') or hasRole('USER')")
    public ResponseEntity<Page<GroupPaymentResponse>> getUserGroupPayments(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        
        String userId = userDetails.getUsername();
        Page<GroupPaymentResponse> response = groupPaymentService.getUserGroupPayments(userId, pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{groupPaymentId}")
    @PreAuthorize("hasAuthority('SCOPE_group-payment:update') or hasRole('USER')")
    public ResponseEntity<GroupPaymentResponse> updateGroupPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String groupPaymentId,
            @Valid @RequestBody UpdateGroupPaymentRequest request) {
        
        String userId = userDetails.getUsername();
        GroupPaymentResponse response = groupPaymentService.updateGroupPayment(groupPaymentId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{groupPaymentId}")
    @PreAuthorize("hasAuthority('SCOPE_group-payment:delete') or hasRole('USER')")
    public ResponseEntity<Void> cancelGroupPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String groupPaymentId) {
        
        String userId = userDetails.getUsername();
        groupPaymentService.cancelGroupPayment(groupPaymentId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupPaymentId}/payments")
    @PreAuthorize("hasAuthority('SCOPE_group-payment:pay') or hasRole('USER')")
    public ResponseEntity<Void> recordPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String groupPaymentId,
            @Valid @RequestBody RecordPaymentRequest request) {
        
        String userId = userDetails.getUsername();
        groupPaymentService.recordPayment(groupPaymentId, userId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{groupPaymentId}/reminders")
    @PreAuthorize("hasAuthority('SCOPE_group-payment:remind') or hasRole('ADMIN')")
    public ResponseEntity<Void> sendReminders(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String groupPaymentId) {
        
        String userId = userDetails.getUsername();
        groupPaymentService.sendReminders(groupPaymentId, userId);
        return ResponseEntity.ok().build();
    }
}