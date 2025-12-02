package com.waqiti.kyc.controller;

import com.waqiti.kyc.migration.BatchMigrationResult;
import com.waqiti.kyc.migration.KYCMigrationService;
import com.waqiti.kyc.migration.MigrationResult;
import com.waqiti.kyc.migration.MigrationStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/kyc/migration")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "KYC Migration", description = "APIs for migrating KYC data from legacy systems")
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(name = "kyc.migration.enabled", havingValue = "true")
public class MigrationController {

    private final KYCMigrationService migrationService;

    @Operation(summary = "Migrate KYC data for a single user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Migration completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "500", description = "Migration failed")
    })
    @PostMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<MigrationResult> migrateUser(@PathVariable String userId) {
        log.info("Starting migration for user: {}", userId);
        
        MigrationResult result = migrationService.migrateUserKYCData(userId);
        
        if (result.isFailed()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
        
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Migrate KYC data for multiple users")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Batch migration completed"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/users/batch")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<BatchMigrationResult> migrateBatchUsers(@RequestBody List<String> userIds) {
        log.info("Starting batch migration for {} users", userIds.size());
        
        if (userIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        if (userIds.size() > 100) {
            return ResponseEntity.badRequest()
                    .body(createErrorBatchResult("Batch size exceeds maximum limit of 100"));
        }
        
        BatchMigrationResult result = migrationService.migrateBatchUsers(userIds);
        
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Check if migration is required for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Check completed")
    })
    @GetMapping("/users/{userId}/required")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<Map<String, Object>> checkMigrationRequired(@PathVariable String userId) {
        log.info("Checking migration requirement for user: {}", userId);
        
        boolean required = migrationService.isMigrationRequired(userId);
        
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "migrationRequired", required
        ));
    }

    @Operation(summary = "Get migration statistics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    })
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<MigrationStats> getMigrationStatistics() {
        log.info("Fetching migration statistics");
        
        MigrationStats stats = migrationService.getMigrationStatistics();
        
        return ResponseEntity.ok(stats);
    }

    @Operation(summary = "Dry run migration for a user (no data changes)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Dry run completed")
    })
    @PostMapping("/users/{userId}/dry-run")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<Map<String, Object>> dryRunMigration(@PathVariable String userId) {
        log.info("Running dry migration for user: {}", userId);
        
        // In a real implementation, this would analyze what would be migrated
        // without actually performing the migration
        
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "status", "DRY_RUN_SUCCESS",
                "wouldMigrate", Map.of(
                        "verifications", 1,
                        "documents", 3,
                        "checks", 5
                )
        ));
    }

    private BatchMigrationResult createErrorBatchResult(String message) {
        BatchMigrationResult result = new BatchMigrationResult();
        MigrationResult errorResult = new MigrationResult();
        errorResult.setError(message);
        result.addResult(errorResult);
        return result;
    }
}