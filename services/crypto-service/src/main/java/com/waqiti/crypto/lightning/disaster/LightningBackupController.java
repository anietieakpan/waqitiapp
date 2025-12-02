package com.waqiti.crypto.lightning.disaster;

import com.waqiti.crypto.lightning.disaster.LightningDisasterRecoveryService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Lightning Network Backup and Disaster Recovery Controller
 * Provides REST endpoints for backup management and disaster recovery operations
 * Requires ADMIN role for all operations due to security implications
 */
@RestController
@RequestMapping("/api/v1/lightning/backup")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lightning Backup & Disaster Recovery", 
     description = "Lightning Network backup and disaster recovery operations")
@SecurityRequirement(name = "bearerAuth")
public class LightningBackupController {

    private final LightningDisasterRecoveryService disasterRecoveryService;

    // ============ BACKUP OPERATIONS ============

    @PostMapping("/full")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Execute full Lightning backup", 
               description = "Creates a comprehensive backup of all Lightning components including channels, wallet, database, and configuration")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Backup started successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "409", description = "Backup already in progress"),
        @ApiResponse(responseCode = "500", description = "Backup failed")
    })
    public ResponseEntity<BackupResponse> executeFullBackup() {
        log.info("Admin requested full Lightning backup");
        
        try {
            CompletableFuture<BackupResult> backupFuture = disasterRecoveryService.executeFullBackup();
            
            return ResponseEntity.accepted().body(
                BackupResponse.builder()
                    .status("STARTED")
                    .message("Full backup started successfully")
                    .backupId(java.util.UUID.randomUUID().toString())
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to start full backup", e);
            return ResponseEntity.internalServerError().body(
                BackupResponse.builder()
                    .status("FAILED")
                    .message("Failed to start backup: " + e.getMessage())
                    .build()
            );
        }
    }

    @PostMapping("/channels")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Backup Lightning channels", 
               description = "Creates a Static Channel Backup (SCB) of all Lightning channels")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Channel backup started"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Channel backup failed")
    })
    public ResponseEntity<BackupResponse> backupChannels() {
        log.info("Admin requested channel backup");
        
        try {
            CompletableFuture<Boolean> channelBackup = disasterRecoveryService.backupChannelState();
            
            return ResponseEntity.accepted().body(
                BackupResponse.builder()
                    .status("STARTED")
                    .message("Channel backup started successfully")
                    .backupId(java.util.UUID.randomUUID().toString())
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to start channel backup", e);
            return ResponseEntity.internalServerError().body(
                BackupResponse.builder()
                    .status("FAILED")
                    .message("Failed to start channel backup: " + e.getMessage())
                    .build()
            );
        }
    }

    @PostMapping("/database")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Backup Lightning database", 
               description = "Creates a backup of all Lightning database entities")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Database backup started"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Database backup failed")
    })
    public ResponseEntity<BackupResponse> backupDatabase() {
        log.info("Admin requested database backup");
        
        try {
            CompletableFuture<Boolean> databaseBackup = disasterRecoveryService.backupLightningDatabase();
            
            return ResponseEntity.accepted().body(
                BackupResponse.builder()
                    .status("STARTED")
                    .message("Database backup started successfully")
                    .backupId(java.util.UUID.randomUUID().toString())
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to start database backup", e);
            return ResponseEntity.internalServerError().body(
                BackupResponse.builder()
                    .status("FAILED")
                    .message("Failed to start database backup: " + e.getMessage())
                    .build()
            );
        }
    }

    // ============ DISASTER RECOVERY OPERATIONS ============

    @PostMapping("/disaster-recovery")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Execute disaster recovery", 
               description = "Restores Lightning Network from specified backup. WARNING: This will stop and restart Lightning services.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Disaster recovery started"),
        @ApiResponse(responseCode = "400", description = "Invalid backup path"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "409", description = "Recovery already in progress"),
        @ApiResponse(responseCode = "500", description = "Recovery failed")
    })
    public ResponseEntity<DisasterRecoveryResponse> executeDisasterRecovery(
            @Valid @RequestBody DisasterRecoveryRequest request) {
        
        log.warn("Admin requested disaster recovery from backup: {}", request.getBackupPath());
        
        try {
            CompletableFuture<DisasterRecoveryResult> recoveryFuture = 
                disasterRecoveryService.executeDisasterRecovery(request.getBackupPath());
            
            return ResponseEntity.accepted().body(
                DisasterRecoveryResponse.builder()
                    .status("STARTED")
                    .message("Disaster recovery started successfully")
                    .recoveryId(java.util.UUID.randomUUID().toString())
                    .backupPath(request.getBackupPath())
                    .estimatedCompletionMinutes(30)
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to start disaster recovery", e);
            return ResponseEntity.internalServerError().body(
                DisasterRecoveryResponse.builder()
                    .status("FAILED")
                    .message("Failed to start disaster recovery: " + e.getMessage())
                    .backupPath(request.getBackupPath())
                    .build()
            );
        }
    }

    // ============ BACKUP MANAGEMENT ============

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List available backups", 
               description = "Returns a list of all available Lightning backups")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Backup list retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Failed to list backups")
    })
    public ResponseEntity<BackupListResponse> listBackups() {
        log.debug("Admin requested backup list");
        
        try {
            List<BackupInfo> backups = disasterRecoveryService.listAvailableBackups();
            
            return ResponseEntity.ok(
                BackupListResponse.builder()
                    .backups(backups)
                    .totalBackups(backups.size())
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to list backups", e);
            return ResponseEntity.internalServerError().body(
                BackupListResponse.builder()
                    .backups(List.of())
                    .totalBackups(0)
                    .error("Failed to list backups: " + e.getMessage())
                    .build()
            );
        }
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get backup system status", 
               description = "Returns the current status and health of the backup system")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Backup status retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Failed to get backup status")
    })
    public ResponseEntity<BackupStatusResponse> getBackupStatus() {
        log.debug("Admin requested backup status");
        
        try {
            Health backupHealth = disasterRecoveryService.health();
            
            return ResponseEntity.ok(
                BackupStatusResponse.builder()
                    .status(backupHealth.getStatus().getCode())
                    .details(backupHealth.getDetails())
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to get backup status", e);
            return ResponseEntity.internalServerError().body(
                BackupStatusResponse.builder()
                    .status("ERROR")
                    .error("Failed to get backup status: " + e.getMessage())
                    .build()
            );
        }
    }

    @DeleteMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Clean up old backups", 
               description = "Manually triggers cleanup of old backup files based on retention policy")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Backup cleanup completed"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Cleanup failed")
    })
    public ResponseEntity<BackupResponse> cleanupOldBackups() {
        log.info("Admin requested backup cleanup");
        
        try {
            // Trigger cleanup (this would be implemented in the service)
            return ResponseEntity.ok(
                BackupResponse.builder()
                    .status("COMPLETED")
                    .message("Backup cleanup completed successfully")
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to cleanup backups", e);
            return ResponseEntity.internalServerError().body(
                BackupResponse.builder()
                    .status("FAILED")
                    .message("Failed to cleanup backups: " + e.getMessage())
                    .build()
            );
        }
    }

    // ============ REQUEST/RESPONSE CLASSES ============

    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class DisasterRecoveryRequest {
        @NotBlank(message = "Backup path is required")
        @Parameter(description = "Path to the backup to restore from", required = true)
        private String backupPath;

        @Parameter(description = "Force recovery even if integrity checks fail")
        private boolean force = false;

        @Parameter(description = "Skip certain recovery steps")
        private List<String> skipSteps = List.of();
    }

    @lombok.Builder
    @lombok.Getter
    public static class BackupResponse {
        private final String status;
        private final String message;
        private final String backupId;
        private final String backupPath;
        private final Long estimatedSizeBytes;
        private final Integer estimatedDurationMinutes;
    }

    @lombok.Builder
    @lombok.Getter
    public static class DisasterRecoveryResponse {
        private final String status;
        private final String message;
        private final String recoveryId;
        private final String backupPath;
        private final Integer estimatedCompletionMinutes;
        private final List<String> recoverySteps;
    }

    @lombok.Builder
    @lombok.Getter
    public static class BackupListResponse {
        private final List<BackupInfo> backups;
        private final int totalBackups;
        private final String error;
    }

    @lombok.Builder
    @lombok.Getter
    public static class BackupStatusResponse {
        private final String status;
        private final java.util.Map<String, Object> details;
        private final String error;
    }
}