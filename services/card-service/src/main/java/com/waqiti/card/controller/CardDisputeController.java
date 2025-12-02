package com.waqiti.card.controller;

import com.waqiti.card.dto.CardDisputeCreateRequest;
import com.waqiti.card.dto.CardDisputeResponse;
import com.waqiti.card.service.CardDisputeService;
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
 * CardDisputeController - REST API for dispute management
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
@Slf4j
public class CardDisputeController {

    private final CardDisputeService disputeService;

    @PostMapping
    @PreAuthorize("hasRole('USER') and @cardOwnershipValidator.isTransactionOwner(#request.transactionId)")
    public ResponseEntity<CardDisputeResponse> createDispute(@Valid @RequestBody CardDisputeCreateRequest request) {
        log.info("REST: Create dispute for transaction: {}", request.getTransactionId());
        CardDisputeResponse response = disputeService.createDispute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{disputeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isDisputeOwner(#disputeId)")
    public ResponseEntity<CardDisputeResponse> getDisputeById(@PathVariable String disputeId) {
        log.info("REST: Get dispute: {}", disputeId);
        CardDisputeResponse response = disputeService.getDisputeById(disputeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/card/{cardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<List<CardDisputeResponse>> getDisputesByCardId(@PathVariable UUID cardId) {
        log.info("REST: Get disputes for card: {}", cardId);
        List<CardDisputeResponse> disputes = disputeService.getDisputesByCardId(cardId);
        return ResponseEntity.ok(disputes);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or #userId == authentication.principal")
    public ResponseEntity<List<CardDisputeResponse>> getDisputesByUserId(@PathVariable UUID userId) {
        log.info("REST: Get disputes for user: {}", userId);
        List<CardDisputeResponse> disputes = disputeService.getDisputesByUserId(userId);
        return ResponseEntity.ok(disputes);
    }

    @PostMapping("/{disputeId}/provisional-credit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<CardDisputeResponse> issueProvisionalCredit(@PathVariable String disputeId) {
        log.info("REST: Issue provisional credit for dispute: {}", disputeId);
        CardDisputeResponse response = disputeService.issueProvisionalCredit(disputeId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{disputeId}/chargeback")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<CardDisputeResponse> issueChargeback(@PathVariable String disputeId) {
        log.info("REST: Issue chargeback for dispute: {}", disputeId);
        CardDisputeResponse response = disputeService.issueChargeback(disputeId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{disputeId}/resolve/cardholder")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<CardDisputeResponse> resolveInFavorOfCardholder(
        @PathVariable String disputeId,
        @RequestParam String outcome,
        @RequestParam BigDecimal creditAmount) {

        log.info("REST: Resolve dispute in favor of cardholder: {}", disputeId);
        CardDisputeResponse response = disputeService.resolveInFavorOfCardholder(disputeId, outcome, creditAmount);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{disputeId}/resolve/merchant")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<CardDisputeResponse> resolveInFavorOfMerchant(
        @PathVariable String disputeId,
        @RequestParam String outcome) {

        log.info("REST: Resolve dispute in favor of merchant: {}", disputeId);
        CardDisputeResponse response = disputeService.resolveInFavorOfMerchant(disputeId, outcome);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{disputeId}/withdraw")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isDisputeOwner(#disputeId)")
    public ResponseEntity<CardDisputeResponse> withdrawDispute(
        @PathVariable String disputeId,
        @RequestParam String reason) {

        log.info("REST: Withdraw dispute: {}", disputeId);
        CardDisputeResponse response = disputeService.withdrawDispute(disputeId, reason);
        return ResponseEntity.ok(response);
    }
}
