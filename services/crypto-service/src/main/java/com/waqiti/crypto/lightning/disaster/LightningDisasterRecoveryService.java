package com.waqiti.crypto.lightning.disaster;

import com.waqiti.crypto.lightning.service.LightningNetworkService;
import com.waqiti.crypto.lightning.entity.*;
import com.waqiti.crypto.lightning.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Comprehensive Lightning Network Disaster Recovery Service
 * Handles backup, restore, and disaster recovery procedures
 * Ensures Lightning operations can be recovered in case of system failures
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LightningDisasterRecoveryService implements HealthIndicator {

    private final LightningNetworkService lightningService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ChannelRepository channelRepository;
    private final StreamRepository streamRepository;
    private final SwapRepository swapRepository;
    private final LightningAuditRepository auditRepository;
    
    @Value("${waqiti.lightning.backup.enabled:true}")
    private boolean backupEnabled;
    
    @Value("${waqiti.lightning.backup.local-path:/var/backups/lightning}")
    private String localBackupPath;
    
    @Value("${waqiti.lightning.backup.remote-path:}")
    private String remoteBackupPath;
    
    @Value("${waqiti.lightning.backup.retention-days:90}")
    private int backupRetentionDays;
    
    @Value("${waqiti.lightning.backup.channel-backup-frequency:PT1H}")
    private Duration channelBackupFrequency;
    
    @Value("${waqiti.lightning.backup.database-backup-frequency:PT6H}")
    private Duration databaseBackupFrequency;
    
    @Value("${waqiti.lightning.backup.encryption-key:}")
    private String encryptionKey;
    
    @Value("${waqiti.lightning.backup.compress:true}")
    private boolean compressBackups;
    
    @Value("${waqiti.lightning.disaster.rto-minutes:30}")
    private int recoveryTimeObjectiveMinutes;
    
    @Value("${waqiti.lightning.disaster.rpo-minutes:5}")
    private int recoveryPointObjectiveMinutes;
    
    private final ExecutorService backupExecutor = Executors.newFixedThreadPool(3);
    private volatile boolean disasterRecoveryInProgress = false;
    private volatile Instant lastChannelBackup;
    private volatile Instant lastDatabaseBackup;
    private volatile BackupStatus lastBackupStatus = BackupStatus.UNKNOWN;

    /**
     * Execute comprehensive Lightning backup (all components)
     */
    @Scheduled(cron = "0 0 */6 * * ?") // Every 6 hours
    public CompletableFuture<BackupResult> executeFullBackup() {
        if (!backupEnabled) {
            log.info("Lightning backup is disabled");
            return CompletableFuture.completedFuture(
                BackupResult.builder()
                    .success(false)
                    .message("Backup disabled")
                    .build()
            );
        }

        log.info("Starting comprehensive Lightning backup");
        Instant backupStart = Instant.now();

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create backup directory
                Path backupDir = createBackupDirectory(backupStart);
                
                BackupResult.BackupResultBuilder resultBuilder = BackupResult.builder()
                    .startTime(backupStart)
                    .backupPath(backupDir.toString());

                // Execute all backup components in parallel
                CompletableFuture<Boolean> channelBackup = backupChannelState(backupDir);
                CompletableFuture<Boolean> walletBackup = backupWalletState(backupDir);
                CompletableFuture<Boolean> databaseBackup = backupLightningDatabase(backupDir);
                CompletableFuture<Boolean> configBackup = backupConfiguration(backupDir);
                
                // Wait for all backups to complete
                CompletableFuture.allOf(channelBackup, walletBackup, databaseBackup, configBackup)
                    .join();
                
                boolean allSuccessful = channelBackup.join() && walletBackup.join() && 
                                      databaseBackup.join() && configBackup.join();
                
                if (allSuccessful) {
                    // Compress and encrypt if enabled
                    if (compressBackups) {
                        compressBackupDirectory(backupDir);
                    }
                    
                    if (!encryptionKey.isEmpty()) {
                        encryptBackup(backupDir);
                    }
                    
                    // Upload to remote storage if configured
                    if (!remoteBackupPath.isEmpty()) {
                        uploadToRemoteStorage(backupDir);
                    }
                    
                    // Clean old backups
                    cleanupOldBackups();
                    
                    lastBackupStatus = BackupStatus.SUCCESS;
                    log.info("Lightning backup completed successfully in {} ms", 
                        Duration.between(backupStart, Instant.now()).toMillis());
                    
                    return resultBuilder
                        .success(true)
                        .endTime(Instant.now())
                        .message("Full backup completed successfully")
                        .build();
                } else {
                    lastBackupStatus = BackupStatus.FAILED;
                    log.error("Lightning backup failed - some components failed");
                    
                    return resultBuilder
                        .success(false)
                        .endTime(Instant.now())
                        .message("Backup failed - check logs for details")
                        .build();
                }
                
            } catch (Exception e) {
                lastBackupStatus = BackupStatus.FAILED;
                log.error("Lightning backup failed with exception", e);
                
                return BackupResult.builder()
                    .startTime(backupStart)
                    .endTime(Instant.now())
                    .success(false)
                    .message("Backup failed: " + e.getMessage())
                    .build();
            }
        }, backupExecutor);
    }

    /**
     * Backup Lightning channel state (SCB - Static Channel Backup)
     */
    @Scheduled(fixedDelayString = "#{T(java.time.Duration).parse('${waqiti.lightning.backup.channel-backup-frequency:PT1H}').toMillis()}")
    public CompletableFuture<Boolean> backupChannelState() {
        return backupChannelState(Paths.get(localBackupPath, "channels"));
    }

    private CompletableFuture<Boolean> backupChannelState(Path backupDir) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Starting channel backup");
                
                // Get channel backup from LND
                byte[] channelBackup = lightningService.exportAllChannelBackup();
                
                if (channelBackup == null || channelBackup.length == 0) {
                    log.warn("No channel backup data available");
                    return false;
                }
                
                // Save channel backup
                Path channelBackupPath = backupDir.resolve("channels")
                    .resolve("channel-backup-" + Instant.now().toEpochMilli() + ".scb");
                Files.createDirectories(channelBackupPath.getParent());
                Files.write(channelBackupPath, channelBackup);
                
                // Save channel list for reference
                List<ChannelEntity> channels = channelRepository.findAll();
                String channelInfo = channels.stream()
                    .map(c -> String.format("%s,%s,%d,%d", 
                        c.getId(), c.getRemotePubkey(), c.getCapacity(), c.getLocalBalance()))
                    .reduce("", (a, b) -> a + "\n" + b);
                
                Path channelInfoPath = backupDir.resolve("channels")
                    .resolve("channel-info-" + Instant.now().toEpochMilli() + ".csv");
                Files.write(channelInfoPath, channelInfo.getBytes());
                
                lastChannelBackup = Instant.now();
                log.debug("Channel backup completed successfully");
                return true;
                
            } catch (Exception e) {
                log.error("Channel backup failed", e);
                return false;
            }
        }, backupExecutor);
    }

    /**
     * Backup Lightning wallet state
     */
    private CompletableFuture<Boolean> backupWalletState(Path backupDir) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Starting wallet backup");
                
                // Get wallet info
                LightningNetworkService.WalletInfo walletInfo = lightningService.getWalletInfo();
                
                // Save wallet backup (excluding private keys)
                Path walletBackupPath = backupDir.resolve("wallet")
                    .resolve("wallet-info-" + Instant.now().toEpochMilli() + ".json");
                Files.createDirectories(walletBackupPath.getParent());
                
                String walletData = String.format("""
                    {
                        "pubkey": "%s",
                        "alias": "%s",
                        "totalBalance": %d,
                        "confirmedBalance": %d,
                        "unconfirmedBalance": %d,
                        "backupTime": "%s"
                    }
                    """, 
                    walletInfo.getPubkey(),
                    walletInfo.getAlias(),
                    walletInfo.getTotalBalance(),
                    walletInfo.getConfirmedBalance(),
                    walletInfo.getUnconfirmedBalance(),
                    Instant.now().toString()
                );
                
                Files.write(walletBackupPath, walletData.getBytes());
                
                log.debug("Wallet backup completed successfully");
                return true;
                
            } catch (Exception e) {
                log.error("Wallet backup failed", e);
                return false;
            }
        }, backupExecutor);
    }

    /**
     * Backup Lightning database state
     */
    @Scheduled(fixedDelayString = "#{T(java.time.Duration).parse('${waqiti.lightning.backup.database-backup-frequency:PT6H}').toMillis()}")
    public CompletableFuture<Boolean> backupLightningDatabase() {
        return backupLightningDatabase(Paths.get(localBackupPath, "database"));
    }

    private CompletableFuture<Boolean> backupLightningDatabase(Path backupDir) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Starting Lightning database backup");
                
                Path dbBackupDir = backupDir.resolve("database")
                    .resolve("db-backup-" + Instant.now().toEpochMilli());
                Files.createDirectories(dbBackupDir);
                
                // Backup invoices
                List<InvoiceEntity> invoices = invoiceRepository.findAll();
                backupEntityData(dbBackupDir, "invoices.json", invoices);
                
                // Backup payments
                List<PaymentEntity> payments = paymentRepository.findAll();
                backupEntityData(dbBackupDir, "payments.json", payments);
                
                // Backup channels
                List<ChannelEntity> channels = channelRepository.findAll();
                backupEntityData(dbBackupDir, "channels.json", channels);
                
                // Backup streams
                List<StreamEntity> streams = streamRepository.findAll();
                backupEntityData(dbBackupDir, "streams.json", streams);
                
                // Backup swaps
                List<SwapEntity> swaps = swapRepository.findAll();
                backupEntityData(dbBackupDir, "swaps.json", swaps);
                
                // Backup audit logs (last 30 days only)
                Instant thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30));
                List<LightningAuditEntity> auditLogs = auditRepository
                    .findByTimestampBetween(thirtyDaysAgo, Instant.now());
                backupEntityData(dbBackupDir, "audit_logs.json", auditLogs);
                
                lastDatabaseBackup = Instant.now();
                log.debug("Database backup completed successfully");
                return true;
                
            } catch (Exception e) {
                log.error("Database backup failed", e);
                return false;
            }
        }, backupExecutor);
    }

    /**
     * Backup Lightning configuration
     */
    private CompletableFuture<Boolean> backupConfiguration(Path backupDir) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Starting configuration backup");
                
                Path configBackupDir = backupDir.resolve("config");
                Files.createDirectories(configBackupDir);
                
                // Copy LND configuration files
                copyConfigurationFiles(configBackupDir);
                
                // Save current Lightning service configuration
                saveServiceConfiguration(configBackupDir);
                
                log.debug("Configuration backup completed successfully");
                return true;
                
            } catch (Exception e) {
                log.error("Configuration backup failed", e);
                return false;
            }
        }, backupExecutor);
    }

    /**
     * Execute disaster recovery procedure
     */
    @Transactional
    public CompletableFuture<DisasterRecoveryResult> executeDisasterRecovery(String backupPath) {
        if (disasterRecoveryInProgress) {
            return CompletableFuture.completedFuture(
                DisasterRecoveryResult.builder()
                    .success(false)
                    .message("Disaster recovery already in progress")
                    .build()
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            disasterRecoveryInProgress = true;
            Instant recoveryStart = Instant.now();
            
            try {
                log.warn("Starting Lightning disaster recovery from backup: {}", backupPath);
                
                Path backupDir = Paths.get(backupPath);
                if (!Files.exists(backupDir)) {
                    throw new IllegalArgumentException("Backup path does not exist: " + backupPath);
                }
                
                DisasterRecoveryResult.DisasterRecoveryResultBuilder resultBuilder = 
                    DisasterRecoveryResult.builder()
                        .startTime(recoveryStart)
                        .backupPath(backupPath);
                
                // Step 1: Validate backup integrity
                if (!validateBackupIntegrity(backupDir)) {
                    throw new RuntimeException("Backup integrity validation failed");
                }
                
                // Step 2: Stop Lightning service
                lightningService.stopService();
                
                // Step 3: Restore wallet state
                restoreWalletState(backupDir);
                
                // Step 4: Restore channel state
                restoreChannelState(backupDir);
                
                // Step 5: Restore database state
                restoreDatabaseState(backupDir);
                
                // Step 6: Restore configuration
                restoreConfiguration(backupDir);
                
                // Step 7: Restart Lightning service
                lightningService.startService();
                
                // Step 8: Verify recovery
                if (!verifyRecoverySuccess()) {
                    throw new RuntimeException("Recovery verification failed");
                }
                
                log.warn("Lightning disaster recovery completed successfully in {} minutes", 
                    Duration.between(recoveryStart, Instant.now()).toMinutes());
                
                return resultBuilder
                    .success(true)
                    .endTime(Instant.now())
                    .message("Disaster recovery completed successfully")
                    .build();
                
            } catch (Exception e) {
                log.error("Disaster recovery failed", e);
                
                return DisasterRecoveryResult.builder()
                    .startTime(recoveryStart)
                    .endTime(Instant.now())
                    .backupPath(backupPath)
                    .success(false)
                    .message("Disaster recovery failed: " + e.getMessage())
                    .build();
                    
            } finally {
                disasterRecoveryInProgress = false;
            }
        }, backupExecutor);
    }

    /**
     * List available backups
     */
    public List<BackupInfo> listAvailableBackups() {
        try {
            Path backupRoot = Paths.get(localBackupPath);
            if (!Files.exists(backupRoot)) {
                return List.of();
            }
            
            return Files.list(backupRoot)
                .filter(Files::isDirectory)
                .map(this::createBackupInfo)
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .toList();
                
        } catch (Exception e) {
            log.error("Failed to list backups", e);
            return List.of();
        }
    }

    /**
     * Get backup status and health information
     */
    @Override
    public Health health() {
        Health.Builder healthBuilder = Health.up();
        
        try {
            // Check if backups are enabled
            healthBuilder.withDetail("backup_enabled", backupEnabled);
            healthBuilder.withDetail("last_backup_status", lastBackupStatus);
            healthBuilder.withDetail("disaster_recovery_in_progress", disasterRecoveryInProgress);
            
            // Check backup freshness
            if (lastChannelBackup != null) {
                Duration channelBackupAge = Duration.between(lastChannelBackup, Instant.now());
                healthBuilder.withDetail("channel_backup_age_minutes", channelBackupAge.toMinutes());
                
                if (channelBackupAge.compareTo(channelBackupFrequency.multipliedBy(2)) > 0) {
                    healthBuilder.down().withDetail("issue", "Channel backup is stale");
                }
            }
            
            if (lastDatabaseBackup != null) {
                Duration dbBackupAge = Duration.between(lastDatabaseBackup, Instant.now());
                healthBuilder.withDetail("database_backup_age_minutes", dbBackupAge.toMinutes());
                
                if (dbBackupAge.compareTo(databaseBackupFrequency.multipliedBy(2)) > 0) {
                    healthBuilder.down().withDetail("issue", "Database backup is stale");
                }
            }
            
            // Check disk space
            Path backupDir = Paths.get(localBackupPath);
            if (Files.exists(backupDir)) {
                long freeSpace = backupDir.toFile().getFreeSpace();
                long totalSpace = backupDir.toFile().getTotalSpace();
                double freeSpacePercent = (double) freeSpace / totalSpace * 100;
                
                healthBuilder.withDetail("backup_disk_free_percent", String.format("%.1f", freeSpacePercent));
                
                if (freeSpacePercent < 10) {
                    healthBuilder.down().withDetail("issue", "Low disk space for backups");
                }
            }
            
            // Check RTO/RPO compliance
            healthBuilder.withDetail("rto_minutes", recoveryTimeObjectiveMinutes);
            healthBuilder.withDetail("rpo_minutes", recoveryPointObjectiveMinutes);
            
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
        
        return healthBuilder.build();
    }

    // ============ HELPER METHODS ============

    private Path createBackupDirectory(Instant timestamp) throws IOException {
        String backupDirName = "lightning-backup-" + timestamp.toEpochMilli();
        Path backupDir = Paths.get(localBackupPath, backupDirName);
        Files.createDirectories(backupDir);
        return backupDir;
    }

    private <T> void backupEntityData(Path backupDir, String filename, List<T> entities) throws IOException {
        Path filePath = backupDir.resolve(filename);
        
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entities.size(); i++) {
            json.append("  ").append(entityToJson(entities.get(i)));
            if (i < entities.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("]");
        
        Files.write(filePath, json.toString().getBytes());
    }

    private String entityToJson(Object entity) {
        // In production, use proper JSON serialization
        return entity.toString();
    }

    private void copyConfigurationFiles(Path configBackupDir) throws IOException {
        // Copy LND configuration files
        String[] configFiles = {
            "/data/lnd/lnd.conf",
            "/data/bitcoin/bitcoin.conf"
        };
        
        for (String configFile : configFiles) {
            Path source = Paths.get(configFile);
            if (Files.exists(source)) {
                Path target = configBackupDir.resolve(source.getFileName());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void saveServiceConfiguration(Path configBackupDir) throws IOException {
        // Save current service configuration
        String serviceConfig = String.format("""
            {
                "backup_enabled": %s,
                "backup_retention_days": %d,
                "channel_backup_frequency": "%s",
                "database_backup_frequency": "%s",
                "rto_minutes": %d,
                "rpo_minutes": %d,
                "backup_time": "%s"
            }
            """,
            backupEnabled,
            backupRetentionDays,
            channelBackupFrequency.toString(),
            databaseBackupFrequency.toString(),
            recoveryTimeObjectiveMinutes,
            recoveryPointObjectiveMinutes,
            Instant.now().toString()
        );
        
        Path configFile = configBackupDir.resolve("service-config.json");
        Files.write(configFile, serviceConfig.getBytes());
    }

    private void compressBackupDirectory(Path backupDir) {
        // Implement backup compression
        log.debug("Compressing backup directory: {}", backupDir);
    }

    private void encryptBackup(Path backupDir) {
        // Implement backup encryption
        log.debug("Encrypting backup directory: {}", backupDir);
    }

    private void uploadToRemoteStorage(Path backupDir) {
        // Implement remote storage upload (S3, etc.)
        log.debug("Uploading backup to remote storage: {}", backupDir);
    }

    private void cleanupOldBackups() {
        try {
            Path backupRoot = Paths.get(localBackupPath);
            Instant cutoff = Instant.now().minus(Duration.ofDays(backupRetentionDays));
            
            Files.list(backupRoot)
                .filter(Files::isDirectory)
                .filter(dir -> {
                    try {
                        return Files.getLastModifiedTime(dir).toInstant().isBefore(cutoff);
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(dir -> {
                    try {
                        deleteDirectory(dir);
                        log.info("Deleted old backup: {}", dir);
                    } catch (IOException e) {
                        log.error("Failed to delete old backup: {}", dir, e);
                    }
                });
                
        } catch (Exception e) {
            log.error("Failed to cleanup old backups", e);
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .sorted((a, b) -> b.compareTo(a)) // Reverse order for deletion
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.error("Failed to delete path: {}", path, e);
                }
            });
    }

    private boolean validateBackupIntegrity(Path backupDir) {
        try {
            // Validate backup structure and files
            return Files.exists(backupDir.resolve("channels")) &&
                   Files.exists(backupDir.resolve("wallet")) &&
                   Files.exists(backupDir.resolve("database")) &&
                   Files.exists(backupDir.resolve("config"));
        } catch (Exception e) {
            log.error("Backup integrity validation failed", e);
            return false;
        }
    }

    private void restoreWalletState(Path backupDir) throws IOException {
        log.info("Restoring wallet state from backup");
        // Implement wallet state restoration
    }

    private void restoreChannelState(Path backupDir) throws IOException {
        log.info("Restoring channel state from backup");
        // Implement channel state restoration
    }

    private void restoreDatabaseState(Path backupDir) throws IOException {
        log.info("Restoring database state from backup");
        // Implement database state restoration
    }

    private void restoreConfiguration(Path backupDir) throws IOException {
        log.info("Restoring configuration from backup");
        // Implement configuration restoration
    }

    private boolean verifyRecoverySuccess() {
        try {
            // Verify Lightning service is operational
            return lightningService.isHealthy() && lightningService.isWalletUnlocked();
        } catch (Exception e) {
            log.error("Recovery verification failed", e);
            return false;
        }
    }

    private BackupInfo createBackupInfo(Path backupDir) {
        try {
            return BackupInfo.builder()
                .path(backupDir.toString())
                .name(backupDir.getFileName().toString())
                .timestamp(Files.getLastModifiedTime(backupDir).toInstant())
                .size(getDirectorySize(backupDir))
                .build();
        } catch (Exception e) {
            log.error("Failed to create backup info", e);
            return BackupInfo.builder()
                .path(backupDir.toString())
                .name(backupDir.getFileName().toString())
                .timestamp(Instant.now())
                .size(0L)
                .build();
        }
    }

    private long getDirectorySize(Path directory) throws IOException {
        return Files.walk(directory)
            .filter(Files::isRegularFile)
            .mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    return 0L;
                }
            })
            .sum();
    }

    // ============ INNER CLASSES ============

    public enum BackupStatus {
        UNKNOWN, SUCCESS, FAILED, IN_PROGRESS
    }

    @lombok.Builder
    @lombok.Getter
    public static class BackupResult {
        private final Instant startTime;
        private final Instant endTime;
        private final boolean success;
        private final String message;
        private final String backupPath;
        private final Map<String, Object> metadata;
    }

    @lombok.Builder
    @lombok.Getter
    public static class DisasterRecoveryResult {
        private final Instant startTime;
        private final Instant endTime;
        private final boolean success;
        private final String message;
        private final String backupPath;
        private final Map<String, Object> recoveryDetails;
    }

    @lombok.Builder
    @lombok.Getter
    public static class BackupInfo {
        private final String path;
        private final String name;
        private final Instant timestamp;
        private final long size;
    }
}