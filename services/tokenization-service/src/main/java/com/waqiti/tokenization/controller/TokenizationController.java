package com.waqiti.tokenization.controller;

import com.waqiti.tokenization.dto.*;
import com.waqiti.tokenization.service.VaultTokenizationService;
import com.waqiti.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Tokenization REST Controller
 *
 * Provides PCI-DSS compliant card tokenization and detokenization endpoints
 *
 * Security:
 * - All endpoints require authentication
 * - User can only tokenize/detokenize their own cards
 * - All operations are audited
 * - Rate limited to prevent abuse
 *
 * @author Waqiti Security Team
 */
@RestController
@RequestMapping("/api/v1/tokenization")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tokenization", description = "PCI-DSS Compliant Card Tokenization API")
@SecurityRequirement(name = "bearer-jwt")
public class TokenizationController {

    private final VaultTokenizationService tokenizationService;

    /**
     * Tokenize a card number
     *
     * POST /api/v1/tokenization/tokenize
     */
    @PostMapping("/tokenize")
    @PreAuthorize("hasRole('USER') and #request.userId == authentication.principal.userId")
    @Operation(
        summary = "Tokenize card number",
        description = "Converts a card number to a secure token using Vault Transit Engine. " +
                      "This endpoint is PCI-DSS compliant and never stores plaintext card numbers."
    )
    public ResponseEntity<TokenizationResponse> tokenizeCard(
            @Valid @RequestBody TokenizationRequest request,
            @CurrentUser UUID currentUserId) {

        log.info("Tokenization request received for user: {}", currentUserId);

        // Ensure user can only tokenize their own cards
        if (!request.getUserId().equals(currentUserId)) {
            log.warn("Unauthorized tokenization attempt: user {} tried to tokenize for user {}",
                    currentUserId, request.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(TokenizationResponse.builder()
                            .success(false)
                            .errorMessage("Unauthorized: Cannot tokenize cards for other users")
                            .build());
        }

        try {
            TokenizationResponse response = tokenizationService.tokenizeCardNumber(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid card data: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(TokenizationResponse.builder()
                            .success(false)
                            .errorMessage("Invalid card data: " + e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Tokenization failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TokenizationResponse.builder()
                            .success(false)
                            .errorMessage("Tokenization failed. Please try again.")
                            .build());
        }
    }

    /**
     * Detokenize a card token
     *
     * POST /api/v1/tokenization/detokenize
     *
     * SECURITY WARNING: This endpoint returns sensitive card data.
     * Only call when absolutely necessary (e.g., payment gateway integration)
     */
    @PostMapping("/detokenize")
    @PreAuthorize("hasAnyRole('USER', 'PAYMENT_PROCESSOR') and #request.userId == authentication.principal.userId")
    @Operation(
        summary = "Detokenize card token",
        description = "Retrieves the original card number from a token. " +
                      "This operation is highly sensitive and fully audited. " +
                      "Only call when necessary for payment processing."
    )
    public ResponseEntity<DetokenizationResponse> detokenizeCard(
            @Valid @RequestBody DetokenizationRequest request,
            @CurrentUser UUID currentUserId) {

        log.info("Detokenization request received for user: {}", currentUserId);

        // Ensure user can only detokenize their own cards
        if (!request.getUserId().equals(currentUserId)) {
            log.warn("Unauthorized detokenization attempt: user {} tried to detokenize for user {}",
                    currentUserId, request.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(DetokenizationResponse.builder()
                            .success(false)
                            .errorMessage("Unauthorized: Cannot detokenize cards for other users")
                            .build());
        }

        try {
            DetokenizationResponse response = tokenizationService.detokenizeCardNumber(request);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Detokenization failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(DetokenizationResponse.builder()
                            .success(false)
                            .errorMessage("Detokenization failed: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Revoke a card token
     *
     * DELETE /api/v1/tokenization/{token}
     */
    @DeleteMapping("/{token}")
    @PreAuthorize("hasRole('USER')")
    @Operation(
        summary = "Revoke card token",
        description = "Marks a token as inactive. The card data remains encrypted but cannot be used."
    )
    public ResponseEntity<Void> revokeToken(
            @PathVariable String token,
            @CurrentUser UUID currentUserId) {

        log.info("Token revocation request for token: {}", token);

        try {
            tokenizationService.revokeToken(token, currentUserId);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Token revocation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
