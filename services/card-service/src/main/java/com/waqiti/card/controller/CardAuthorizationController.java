package com.waqiti.card.controller;

import com.waqiti.card.dto.CardAuthorizationRequest;
import com.waqiti.card.dto.CardAuthorizationResponse;
import com.waqiti.card.service.CardAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * CardAuthorizationController - REST API for authorization processing
 *
 * Endpoints:
 * - POST /api/v1/authorizations - Process authorization
 * - GET /api/v1/authorizations/{authorizationId} - Get authorization details
 * - POST /api/v1/authorizations/{authorizationId}/capture - Capture authorization
 * - POST /api/v1/authorizations/{authorizationId}/reverse - Reverse authorization
 * - GET /api/v1/authorizations/card/{cardId}/active - Get active authorizations
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@RestController
@RequestMapping("/api/v1/authorizations")
@RequiredArgsConstructor
@Slf4j
public class CardAuthorizationController {

    private final CardAuthorizationService authorizationService;

    /**
     * Process authorization request
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SYSTEM', 'MERCHANT', 'ADMIN')")
    public ResponseEntity<CardAuthorizationResponse> authorizeTransaction(
        @Valid @RequestBody CardAuthorizationRequest request) {

        log.info("REST: Authorization request for card: {} - Amount: {}",
            request.getCardId(), request.getAmount());

        CardAuthorizationResponse response = authorizationService.authorizeTransaction(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get authorization by ID
     */
    @GetMapping("/{authorizationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isAuthorizationOwner(#authorizationId)")
    public ResponseEntity<CardAuthorizationResponse> getAuthorizationById(@PathVariable String authorizationId) {
        log.info("REST: Get authorization: {}", authorizationId);
        CardAuthorizationResponse response = authorizationService.getAuthorizationById(authorizationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Capture authorization
     */
    @PostMapping("/{authorizationId}/capture")
    @PreAuthorize("hasAnyRole('SYSTEM', 'MERCHANT', 'ADMIN')")
    public ResponseEntity<CardAuthorizationResponse> captureAuthorization(
        @PathVariable String authorizationId,
        @RequestParam(required = false) BigDecimal amount) {

        log.info("REST: Capture authorization: {} - Amount: {}", authorizationId, amount);
        CardAuthorizationResponse response = authorizationService.captureAuthorization(authorizationId, amount);
        return ResponseEntity.ok(response);
    }

    /**
     * Reverse authorization
     */
    @PostMapping("/{authorizationId}/reverse")
    @PreAuthorize("hasAnyRole('SYSTEM', 'MERCHANT', 'ADMIN') or @cardOwnershipValidator.isAuthorizationOwner(#authorizationId)")
    public ResponseEntity<CardAuthorizationResponse> reverseAuthorization(
        @PathVariable String authorizationId,
        @RequestParam String reason) {

        log.info("REST: Reverse authorization: {} - Reason: {}", authorizationId, reason);
        CardAuthorizationResponse response = authorizationService.reverseAuthorization(authorizationId, reason);
        return ResponseEntity.ok(response);
    }

    /**
     * Get active authorizations for card
     */
    @GetMapping("/card/{cardId}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<List<CardAuthorizationResponse>> getActiveAuthorizationsByCardId(@PathVariable UUID cardId) {
        log.info("REST: Get active authorizations for card: {}", cardId);
        List<CardAuthorizationResponse> authorizations = authorizationService.getActiveAuthorizationsByCardId(cardId);
        return ResponseEntity.ok(authorizations);
    }

    /**
     * Get expired authorizations
     */
    @GetMapping("/expired")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<List<CardAuthorizationResponse>> getExpiredAuthorizations() {
        log.info("REST: Get expired authorizations");
        List<CardAuthorizationResponse> authorizations = authorizationService.getExpiredAuthorizations();
        return ResponseEntity.ok(authorizations);
    }
}
