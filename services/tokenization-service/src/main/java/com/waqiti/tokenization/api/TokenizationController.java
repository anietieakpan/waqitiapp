package com.waqiti.tokenization.api;

import com.waqiti.tokenization.api.dto.*;
import com.waqiti.tokenization.service.TokenizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.waqiti.common.security.rbac.RequiresPermission;
import com.waqiti.common.security.rbac.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Tokenization REST API Controller
 *
 * PCI-DSS compliant tokenization endpoints for sensitive data management.
 *
 * Endpoints:
 * - POST   /api/v1/tokenization/tokenize      - Create token
 * - POST   /api/v1/tokenization/detokenize    - Retrieve original data
 * - DELETE /api/v1/tokenization/tokens/{token} - Revoke token
 * - GET    /api/v1/tokenization/tokens/{token} - Validate token
 * - GET    /api/v1/tokenization/tokens         - List user's tokens
 *
 * Security:
 * - OAuth2 JWT authentication required
 * - User ownership validation enforced
 * - Rate limiting applied
 * - Audit logging enabled
 *
 * @author Waqiti Platform Engineering
 */
@RestController
@RequestMapping("/api/v1/tokenization")
@RequiredArgsConstructor
@Slf4j
public class TokenizationController {

    private final TokenizationService tokenizationService;

    /**
     * Tokenize sensitive data
     *
     * POST /api/v1/tokenization/tokenize
     *
     * @param request Tokenization request
     * @param jwt JWT token (contains userId)
     * @param httpRequest HTTP request (for IP, user agent)
     * @return Tokenization response with token
     */
    @PostMapping("/tokenize")
    @RequiresPermission(Permission.PAYMENT_WRITE)
    public ResponseEntity<TokenizeResponse> tokenize(
            @Valid @RequestBody TokenizeRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {

        String userId = jwt.getSubject(); // Extract user ID from JWT

        log.info("Tokenization request: type={}, userId={}", request.getType(), userId);

        // Build service request
        TokenizationService.TokenizationRequest serviceRequest = TokenizationService.TokenizationRequest.builder()
            .sensitiveData(request.getSensitiveData())
            .type(request.getType())
            .userId(userId)
            .kmsKeyId(request.getKmsKeyId())
            .expirationDays(request.getExpirationDays())
            .metadata(request.getMetadata())
            .ipAddress(getClientIp(httpRequest))
            .userAgent(httpRequest.getHeader("User-Agent"))
            .build();

        // Tokenize
        TokenizationService.TokenizationResult result = tokenizationService.tokenize(serviceRequest);

        if (result.isSuccess()) {
            TokenizeResponse response = TokenizeResponse.builder()
                .token(result.getToken())
                .type(result.getType())
                .expiresAt(result.getExpiresAt())
                .success(true)
                .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            TokenizeResponse response = TokenizeResponse.builder()
                .success(false)
                .build();

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Detokenize to retrieve original sensitive data
     *
     * POST /api/v1/tokenization/detokenize
     *
     * WARNING: This endpoint returns sensitive data. Use with caution.
     *
     * @param request Detokenization request
     * @param jwt JWT token
     * @return Original sensitive data
     */
    @PostMapping("/detokenize")
    @RequiresPermission(Permission.PAYMENT_READ)
    public ResponseEntity<DetokenizeResponse> detokenize(
            @Valid @RequestBody DetokenizeRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.info("Detokenization request: token=***, userId={}", userId);

        // Build service request
        TokenizationService.DetokenizationRequest serviceRequest = TokenizationService.DetokenizationRequest.builder()
            .token(request.getToken())
            .userId(userId)
            .build();

        // Detokenize
        TokenizationService.DetokenizationResult result = tokenizationService.detokenize(serviceRequest);

        if (result.isSuccess()) {
            DetokenizeResponse response = DetokenizeResponse.builder()
                .sensitiveData(result.getSensitiveData())
                .type(result.getType())
                .success(true)
                .build();

            return ResponseEntity.ok(response);
        } else {
            DetokenizeResponse response = DetokenizeResponse.builder()
                .success(false)
                .build();

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    /**
     * Revoke a token
     *
     * DELETE /api/v1/tokenization/tokens/{token}
     *
     * Makes the token permanently unusable.
     *
     * @param token Token to revoke
     * @param jwt JWT token
     * @return Success response
     */
    @DeleteMapping("/tokens/{token}")
    public ResponseEntity<Void> revokeToken(
            @PathVariable String token,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.info("Token revocation request: token=***, userId={}", userId);

        boolean revoked = tokenizationService.revokeToken(token, userId);

        if (revoked) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Validate token
     *
     * GET /api/v1/tokenization/tokens/{token}
     *
     * Checks if token is valid without retrieving sensitive data.
     *
     * @param token Token to validate
     * @param jwt JWT token
     * @return Validation result
     */
    @GetMapping("/tokens/{token}")
    public ResponseEntity<TokenValidationResponse> validateToken(
            @PathVariable String token,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.debug("Token validation request: token=***, userId={}", userId);

        TokenizationService.TokenValidationResult result =
            tokenizationService.validateToken(token, userId);

        TokenValidationResponse response = TokenValidationResponse.builder()
            .valid(result.isValid())
            .type(result.getType())
            .expiresAt(result.getExpiresAt())
            .reason(result.getReason())
            .build();

        return ResponseEntity.ok(response);
    }

    /**
     * List user's tokens
     *
     * GET /api/v1/tokenization/tokens
     *
     * Returns list of tokens (without sensitive data)
     *
     * @param jwt JWT token
     * @return List of tokens
     */
    @GetMapping("/tokens")
    public ResponseEntity<?> listTokens(
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        log.debug("List tokens request: userId={}", userId);

        var tokens = tokenizationService.getUserTokens(userId, null);

        return ResponseEntity.ok(tokens);
    }

    // Helper methods

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
