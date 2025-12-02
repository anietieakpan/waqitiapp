package com.waqiti.corebanking.api;

import com.waqiti.corebanking.dto.GdprDataExportDto;
import com.waqiti.corebanking.dto.GdprErasureRequestDto;
import com.waqiti.corebanking.dto.GdprErasureResponseDto;
import com.waqiti.corebanking.service.GdprDataErasureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * GDPR Compliance REST API
 *
 * Implements GDPR requirements:
 * - Article 15: Right of Access (data export)
 * - Article 17: Right to Erasure ("Right to be Forgotten")
 * - Article 20: Right to Data Portability
 *
 * CRITICAL: Required for EU operations
 *
 * Endpoints:
 * - GET /gdpr/export/{userId} - Export all user data
 * - POST /gdpr/erase - Erase/anonymize user data
 * - GET /gdpr/status/{userId} - Check erasure status
 *
 * Security:
 * - Requires ADMIN or COMPLIANCE role
 * - User can request their own data (USER role)
 * - All operations are audited
 *
 * @author Core Banking Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/gdpr")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "GDPR Compliance", description = "GDPR data subject rights: export, erasure, portability")
@SecurityRequirement(name = "bearer-jwt")
public class GdprController {

    private final GdprDataErasureService gdprDataErasureService;

    /**
     * Export all user data (GDPR Article 15 & 20)
     *
     * Returns complete data export including:
     * - All accounts
     * - All bank accounts
     * - All transactions
     * - Personal information
     * - Financial records
     *
     * MUST be provided to user upon request within 30 days per GDPR
     *
     * @param userId User ID to export data for
     * @return Complete data export
     */
    @GetMapping("/export/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE') or #userId == authentication.principal.userId")
    @Operation(
            summary = "Export user data",
            description = "GDPR Article 15 & 20: Export all user data for data portability. " +
                    "Returns complete data including accounts, transactions, and personal information."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Data export successful",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GdprDataExportDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found or no data exists",
                    content = @Content(schema = @Schema(implementation = String.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - insufficient permissions",
                    content = @Content(schema = @Schema(implementation = String.class))
            )
    })
    public ResponseEntity<GdprDataExportDto> exportUserData(
            @Parameter(description = "User ID to export data for", required = true)
            @PathVariable UUID userId) {

        log.info("GDPR data export request for user: {}", userId);

        GdprDataExportDto exportDto = gdprDataErasureService.exportUserData(userId);

        log.info("GDPR data export completed for user: {}. Records exported: Accounts={}, Transactions={}",
                userId,
                exportDto.getAccounts() != null ? exportDto.getAccounts().size() : 0,
                exportDto.getTransactions() != null ? exportDto.getTransactions().size() : 0);

        return ResponseEntity.ok(exportDto);
    }

    /**
     * Erase user data (GDPR Article 17 - Right to be Forgotten)
     *
     * CRITICAL OPERATION
     *
     * Process:
     * 1. Validates erasure is allowed (no active transactions, zero balances)
     * 2. Exports data before erasure (for user download)
     * 3. Anonymizes personal data
     * 4. Soft-deletes accounts
     * 5. Pseudonymizes transactions (regulatory requirement - cannot delete)
     * 6. Creates audit trail
     *
     * IMPORTANT:
     * - Financial transaction records are ANONYMIZED, not deleted
     * - Complies with both GDPR and financial record retention laws
     * - Audit trail maintained for 7 years minimum
     *
     * @param request Erasure request
     * @return Erasure confirmation with details
     */
    @PostMapping("/erase")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE')")
    @Operation(
            summary = "Erase user data (Right to be Forgotten)",
            description = "GDPR Article 17: Erase/anonymize all user data. " +
                    "Personal data is anonymized, financial transaction records are pseudonymized per regulatory requirements. " +
                    "User must have zero balances and no pending transactions."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Data erasure successful",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GdprErasureResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - erasure not allowed (active transactions, non-zero balance, etc.)",
                    content = @Content(schema = @Schema(implementation = String.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found",
                    content = @Content(schema = @Schema(implementation = String.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - insufficient permissions",
                    content = @Content(schema = @Schema(implementation = String.class))
            )
    })
    public ResponseEntity<GdprErasureResponseDto> eraseUserData(
            @Parameter(description = "GDPR erasure request", required = true)
            @Valid @RequestBody GdprErasureRequestDto request) {

        log.warn("CRITICAL: GDPR data erasure request for user: {}. Requested by: {}. Reason: {}",
                request.getUserId(), request.getRequestedBy(), request.getReason());

        GdprErasureResponseDto response = gdprDataErasureService.eraseUserData(request);

        log.warn("GDPR data erasure completed for user: {}. Accounts erased: {}, Transactions pseudonymized: {}",
                request.getUserId(), response.getAccountsErased(), response.getTransactionsPseudonymized());

        return ResponseEntity.ok(response);
    }

    /**
     * Check erasure status for user
     *
     * Returns current erasure state:
     * - Whether data has been erased
     * - Number of accounts erased
     * - Number of transactions pseudonymized
     * - Erasure date (if applicable)
     *
     * @param userId User ID to check
     * @return Erasure status
     */
    @GetMapping("/status/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE')")
    @Operation(
            summary = "Check GDPR erasure status",
            description = "Get current erasure status for a user. " +
                    "Shows whether data has been erased, erasure date, and what was anonymized."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Status retrieved successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GdprErasureResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - insufficient permissions",
                    content = @Content(schema = @Schema(implementation = String.class))
            )
    })
    public ResponseEntity<GdprErasureResponseDto> getErasureStatus(
            @Parameter(description = "User ID to check status for", required = true)
            @PathVariable UUID userId) {

        log.info("GDPR erasure status check for user: {}", userId);

        GdprErasureResponseDto status = gdprDataErasureService.getErasureStatus(userId);

        return ResponseEntity.ok(status);
    }

    /**
     * Check if user data is erased (simple boolean check)
     *
     * @param userId User ID to check
     * @return true if data is erased, false otherwise
     */
    @GetMapping("/is-erased/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE')")
    @Operation(
            summary = "Check if user data is erased (boolean)",
            description = "Simple boolean check to determine if user data has been erased."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Check successful",
                    content = @Content(schema = @Schema(implementation = Boolean.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - insufficient permissions",
                    content = @Content(schema = @Schema(implementation = String.class))
            )
    })
    public ResponseEntity<Boolean> isUserDataErased(
            @Parameter(description = "User ID to check", required = true)
            @PathVariable UUID userId) {

        boolean isErased = gdprDataErasureService.isUserDataErased(userId);

        return ResponseEntity.ok(isErased);
    }
}
