package com.waqiti.card.controller;

import com.waqiti.card.dto.*;
import com.waqiti.card.service.CardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * CardController - REST API for card management
 *
 * Endpoints:
 * - POST /api/v1/cards - Create new card
 * - GET /api/v1/cards/{cardId} - Get card details
 * - GET /api/v1/cards/user/{userId} - Get user's cards
 * - PUT /api/v1/cards/{cardId} - Update card
 * - POST /api/v1/cards/{cardId}/activate - Activate card
 * - POST /api/v1/cards/{cardId}/block - Block card
 * - POST /api/v1/cards/{cardId}/unblock - Unblock card
 * - POST /api/v1/cards/{cardId}/pin - Set/change PIN
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Slf4j
public class CardController {

    private final CardService cardService;

    /**
     * Create new card
     */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardResponse> createCard(@Valid @RequestBody CardCreateRequest request) {
        log.info("REST: Create card request for user: {}", request.getUserId());
        CardResponse response = cardService.createCard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get card by ID
     */
    @GetMapping("/{cardId}")
    @PreAuthorize("hasRole('USER') and @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<CardResponse> getCardById(@PathVariable UUID cardId) {
        log.info("REST: Get card by ID: {}", cardId);
        CardResponse response = cardService.getCardById(cardId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get card by card ID string
     */
    @GetMapping("/cardId/{cardId}")
    @PreAuthorize("hasRole('USER') and @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<CardResponse> getCardByCardId(@PathVariable String cardId) {
        log.info("REST: Get card by card ID: {}", cardId);
        CardResponse response = cardService.getCardByCardId(cardId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all cards for a user
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal")
    public ResponseEntity<List<CardResponse>> getCardsByUserId(@PathVariable UUID userId) {
        log.info("REST: Get cards for user: {}", userId);
        List<CardResponse> cards = cardService.getCardsByUserId(userId);
        return ResponseEntity.ok(cards);
    }

    /**
     * Get active cards for a user
     */
    @GetMapping("/user/{userId}/active")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal")
    public ResponseEntity<List<CardResponse>> getActiveCardsByUserId(@PathVariable UUID userId) {
        log.info("REST: Get active cards for user: {}", userId);
        List<CardResponse> cards = cardService.getActiveCardsByUserId(userId);
        return ResponseEntity.ok(cards);
    }

    /**
     * Update card details
     */
    @PutMapping("/{cardId}")
    @PreAuthorize("hasRole('USER') and @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<CardResponse> updateCard(
        @PathVariable String cardId,
        @Valid @RequestBody CardUpdateRequest request) {

        log.info("REST: Update card: {}", cardId);
        CardResponse response = cardService.updateCard(cardId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Activate card
     */
    @PostMapping("/{cardId}/activate")
    @PreAuthorize("hasRole('USER') and @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<CardResponse> activateCard(
        @PathVariable String cardId,
        @Valid @RequestBody CardActivateRequest request) {

        log.info("REST: Activate card: {}", cardId);
        CardResponse response = cardService.activateCard(cardId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Block card
     */
    @PostMapping("/{cardId}/block")
    @PreAuthorize("hasRole('USER') and @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<CardResponse> blockCard(
        @PathVariable String cardId,
        @Valid @RequestBody CardBlockRequest request) {

        log.info("REST: Block card: {}", cardId);
        CardResponse response = cardService.blockCard(cardId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Unblock card
     */
    @PostMapping("/{cardId}/unblock")
    @PreAuthorize("hasRole('USER') and @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<CardResponse> unblockCard(@PathVariable String cardId) {
        log.info("REST: Unblock card: {}", cardId);
        CardResponse response = cardService.unblockCard(cardId);
        return ResponseEntity.ok(response);
    }

    /**
     * Set/change card PIN
     */
    @PostMapping("/{cardId}/pin")
    @PreAuthorize("hasRole('USER') and @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<Void> setCardPin(
        @PathVariable String cardId,
        @Valid @RequestBody CardPinSetRequest request) {

        log.info("REST: Set PIN for card: {}", cardId);
        cardService.setCardPin(cardId, request);
        return ResponseEntity.noContent().build();
    }
}
