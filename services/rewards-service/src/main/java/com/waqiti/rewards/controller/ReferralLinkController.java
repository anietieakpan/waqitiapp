package com.waqiti.rewards.controller;

import com.waqiti.common.security.SecurityContext;
import com.waqiti.rewards.domain.ReferralLink;
import com.waqiti.rewards.dto.referral.CreateReferralLinkRequest;
import com.waqiti.rewards.dto.referral.ReferralLinkDto;
import com.waqiti.rewards.service.ReferralClickAnalyticsService;
import com.waqiti.rewards.service.ReferralLinkService;
import com.waqiti.rewards.service.ReferralProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API controller for referral link management
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@RestController
@RequestMapping("/api/v1/referrals/links")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Referral Links", description = "Referral link generation and tracking")
public class ReferralLinkController {

    private final ReferralLinkService linkService;
    private final ReferralClickAnalyticsService analyticsService;
    private final ReferralProgramService programService;
    private final SecurityContext securityContext;

    @Operation(
            summary = "Create referral link",
            description = "Generate a new referral link for the authenticated user"
    )
    @ApiResponse(responseCode = "201", description = "Link created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "404", description = "Program not found")
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReferralLinkDto> createLink(
            @Valid @RequestBody CreateReferralLinkRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("User {} creating referral link for program: {}", userId, request.getProgramId());

        ReferralLink link = linkService.createLink(
                UUID.fromString(userId),
                request.getProgramId(),
                request.getChannel()
        );

        // Set custom code if provided
        if (request.getCustomCode() != null && !request.getCustomCode().isBlank()) {
            link = linkService.updateLinkCode(link.getLinkId(), request.getCustomCode());
        }

        // Set campaign if provided
        if (request.getCampaign() != null) {
            link.setCampaign(request.getCampaign());
        }

        // Set metadata if provided
        if (request.getMetadata() != null) {
            link.setMetadata(request.getMetadata());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(link));
    }

    @Operation(
            summary = "Get my referral links",
            description = "Get all referral links for the authenticated user"
    )
    @ApiResponse(responseCode = "200", description = "Links retrieved successfully")
    @GetMapping("/my-links")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<ReferralLinkDto>> getMyLinks(
            @RequestParam(required = false) String programId,
            @RequestParam(required = false) Boolean activeOnly,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving referral links", userId);

        Page<ReferralLink> links;
        if (programId != null) {
            links = linkService.getUserLinksByProgram(
                    UUID.fromString(userId),
                    programId,
                    pageable
            );
        } else if (Boolean.TRUE.equals(activeOnly)) {
            links = linkService.getActiveUserLinks(UUID.fromString(userId), pageable);
        } else {
            links = linkService.getUserLinks(UUID.fromString(userId), pageable);
        }

        Page<ReferralLinkDto> dtos = links.map(this::toDto);
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get link by ID",
            description = "Get detailed information about a specific referral link"
    )
    @ApiResponse(responseCode = "200", description = "Link retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Link not found")
    @GetMapping("/{linkId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReferralLinkDto> getLinkById(
            @Parameter(description = "Link ID", example = "LNK-ABC12345")
            @PathVariable String linkId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving link: {}", userId, linkId);

        ReferralLink link = linkService.getLinkByLinkId(linkId);

        // Verify ownership
        if (!link.getUserId().toString().equals(userId)) {
            log.warn("User {} attempted to access link {} owned by {}", userId, linkId, link.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(toDto(link));
    }

    @Operation(
            summary = "Get link analytics",
            description = "Get detailed analytics for a specific referral link"
    )
    @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Link not found")
    @GetMapping("/{linkId}/analytics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getLinkAnalytics(
            @Parameter(description = "Link ID") @PathVariable String linkId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving analytics for link: {}", userId, linkId);

        ReferralLink link = linkService.getLinkByLinkId(linkId);

        // Verify ownership
        if (!link.getUserId().toString().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> analytics = analyticsService.getLinkAnalytics(linkId);
        return ResponseEntity.ok(analytics);
    }

    @Operation(
            summary = "Deactivate link",
            description = "Deactivate a referral link (it will no longer track clicks)"
    )
    @ApiResponse(responseCode = "200", description = "Link deactivated successfully")
    @ApiResponse(responseCode = "404", description = "Link not found")
    @PostMapping("/{linkId}/deactivate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReferralLinkDto> deactivateLink(
            @Parameter(description = "Link ID") @PathVariable String linkId) {
        String userId = securityContext.getCurrentUserId();
        log.info("User {} deactivating link: {}", userId, linkId);

        ReferralLink link = linkService.getLinkByLinkId(linkId);

        // Verify ownership
        if (!link.getUserId().toString().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        linkService.deactivateLink(linkId);
        ReferralLink updated = linkService.getLinkByLinkId(linkId);
        return ResponseEntity.ok(toDto(updated));
    }

    @Operation(
            summary = "Reactivate link",
            description = "Reactivate a previously deactivated referral link"
    )
    @ApiResponse(responseCode = "200", description = "Link reactivated successfully")
    @ApiResponse(responseCode = "404", description = "Link not found")
    @PostMapping("/{linkId}/reactivate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReferralLinkDto> reactivateLink(
            @Parameter(description = "Link ID") @PathVariable String linkId) {
        String userId = securityContext.getCurrentUserId();
        log.info("User {} reactivating link: {}", userId, linkId);

        ReferralLink link = linkService.getLinkByLinkId(linkId);

        // Verify ownership
        if (!link.getUserId().toString().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        linkService.reactivateLink(linkId);
        ReferralLink updated = linkService.getLinkByLinkId(linkId);
        return ResponseEntity.ok(toDto(updated));
    }

    @Operation(
            summary = "Track referral click (public)",
            description = "Public endpoint to track clicks on referral links and redirect to app"
    )
    @ApiResponse(responseCode = "302", description = "Redirect to application")
    @ApiResponse(responseCode = "404", description = "Invalid referral code")
    @GetMapping("/r/{referralCode}")
    public ResponseEntity<Void> trackClick(
            @Parameter(description = "Referral code") @PathVariable String referralCode,
            HttpServletRequest request) {
        log.info("Tracking click for referral code: {}", referralCode);

        try {
            // Extract click information
            String ipAddress = getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");
            String deviceType = determineDeviceType(userAgent);
            String browser = determineBrowser(userAgent);

            // Record click
            analyticsService.recordClick(
                    referralCode,
                    ipAddress,
                    userAgent,
                    deviceType,
                    browser,
                    null // country code would come from IP geolocation service
            );

            // Redirect to app (would be configured)
            String redirectUrl = "https://example.com/signup?ref=" + referralCode;
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirectUrl))
                    .build();

        } catch (Exception e) {
            log.error("Error tracking click for code: {}", referralCode, e);
            // Redirect to homepage on error
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("https://example.com"))
                    .build();
        }
    }

    @Operation(
            summary = "Admin: Get all links",
            description = "Get all referral links in the system (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Links retrieved successfully")
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ReferralLinkDto>> getAllLinks(
            @RequestParam(required = false) String programId,
            @RequestParam(required = false) String userId,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        log.info("Admin retrieving all referral links");

        Page<ReferralLink> links;
        if (programId != null && userId != null) {
            links = linkService.getUserLinksByProgram(
                    UUID.fromString(userId),
                    programId,
                    pageable
            );
        } else if (userId != null) {
            links = linkService.getUserLinks(UUID.fromString(userId), pageable);
        } else {
            links = linkService.getAllLinks(pageable);
        }

        Page<ReferralLinkDto> dtos = links.map(this::toDto);
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Admin: Get suspicious click patterns",
            description = "Detect suspicious click patterns that may indicate fraud (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Suspicious patterns retrieved successfully")
    @GetMapping("/admin/suspicious-patterns")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Object[]>> getSuspiciousPatterns(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "10") long threshold) {
        log.info("Admin retrieving suspicious click patterns");

        List<Object[]> patterns = analyticsService.detectSuspiciousPatterns(hours, threshold);
        return ResponseEntity.ok(patterns);
    }

    /**
     * Convert entity to DTO
     */
    private ReferralLinkDto toDto(ReferralLink link) {
        String programName = null;
        try {
            programName = programService.getProgramByProgramId(link.getProgram().getProgramId())
                    .getProgramName();
        } catch (Exception e) {
            log.warn("Could not fetch program name for link: {}", link.getLinkId());
        }

        Double conversionRate = null;
        if (link.getClickCount() != null && link.getClickCount() > 0) {
            conversionRate = (link.getConversionCount() * 100.0) / link.getClickCount();
        }

        return ReferralLinkDto.builder()
                .linkId(link.getLinkId())
                .userId(link.getUserId())
                .programId(link.getProgram().getProgramId())
                .programName(programName)
                .referralCode(link.getReferralCode())
                .shortUrl(link.getShortUrl())
                .fullUrl(link.getFullUrl())
                .channel(link.getChannel())
                .campaign(link.getCampaign())
                .clickCount(link.getClickCount())
                .uniqueClicks(link.getUniqueClicks())
                .conversionCount(link.getConversionCount())
                .conversionRate(conversionRate)
                .isActive(link.getIsActive())
                .expiresAt(link.getExpiresAt())
                .lastClickedAt(link.getLastClickedAt())
                .metadata(link.getMetadata())
                .createdAt(link.getCreatedAt())
                .updatedAt(link.getUpdatedAt())
                .build();
    }

    /**
     * Extract client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Determine device type from user agent
     */
    private String determineDeviceType(String userAgent) {
        if (userAgent == null) return "UNKNOWN";
        String lower = userAgent.toLowerCase();
        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")) {
            return "MOBILE";
        } else if (lower.contains("tablet") || lower.contains("ipad")) {
            return "TABLET";
        }
        return "DESKTOP";
    }

    /**
     * Determine browser from user agent
     */
    private String determineBrowser(String userAgent) {
        if (userAgent == null) return "UNKNOWN";
        String lower = userAgent.toLowerCase();
        if (lower.contains("chrome")) return "CHROME";
        if (lower.contains("safari")) return "SAFARI";
        if (lower.contains("firefox")) return "FIREFOX";
        if (lower.contains("edge")) return "EDGE";
        return "OTHER";
    }
}
