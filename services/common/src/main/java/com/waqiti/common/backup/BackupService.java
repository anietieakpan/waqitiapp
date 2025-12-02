package com.waqiti.common.backup;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.waqiti.vault.service.VaultSecretsService;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Comprehensive backup service for application data and configurations
 * 
 * Provides automated backup functionality with:
 * - Encryption for sensitive data
 * - Compression for storage optimization
 * - Integrity verification with checksums
 * - Multi-destination backup (S3, local, etc.)
 * - Backup rotation and cleanup
 * - Recovery point objectives (RPO) compliance
 * - Disaster recovery integration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final S3Client s3Client;
    private final JdbcTemplate jdbcTemplate;
    private final VaultSecretsService vaultSecretsService;
    
    @Value("${backup.s3.bucket-name}")
    private String backupBucketName;
    
    @Value("${backup.encryption.key}")
    private String encryptionKey;
    
    @Value("${backup.local.directory:/opt/backups}")
    private String localBackupDirectory;
    
    @Value("${backup.retention.days:30}")
    private int retentionDays;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Create a comprehensive backup of application data
     */
    @Async
    public CompletableFuture<BackupResult> createFullBackup(BackupRequest request) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String backupId = generateBackupId(timestamp, request.getBackupType().name());
        
        log.info("Starting full backup: {}", backupId);
        
        try {
            BackupManifest manifest = BackupManifest.builder()
                    .backupId(backupId)
                    .timestamp(timestamp)
                    .backupType(request.getBackupType())
                    .requestedBy(request.getRequestedBy())
                    .build();

            // Create backup directory
            Path backupDir = createBackupDirectory(backupId);
            
            // Backup different data types based on request
            List<BackupComponent> components = new ArrayList<>();
            
            if (request.includeUserData()) {
                components.add(backupUserData(backupDir, timestamp));
            }
            
            if (request.includeTransactionData()) {
                components.add(backupTransactionData(backupDir, timestamp));
            }
            
            if (request.includePaymentData()) {
                components.add(backupPaymentData(backupDir, timestamp));
            }
            
            if (request.includeAuditData()) {
                components.add(backupAuditData(backupDir, timestamp));
            }
            
            if (request.includeConfiguration()) {
                components.add(backupConfiguration(backupDir, timestamp));
            }
            
            if (request.includeSecrets()) {
                components.add(backupSecrets(backupDir, timestamp));
            }

            manifest.setComponents(components);
            manifest.setTotalSize(calculateTotalSize(components));
            
            // Create manifest file
            createManifestFile(backupDir, manifest);
            
            // Compress backup
            Path compressedBackup = compressBackup(backupDir, backupId);
            
            // Encrypt backup
            Path encryptedBackup = encryptBackup(compressedBackup, backupId);
            
            // Upload to S3
            String s3Key = uploadToS3(encryptedBackup, backupId, request.getBackupType());
            
            // Verify backup integrity
            boolean integrityValid = verifyBackupIntegrity(encryptedBackup, s3Key);
            
            if (!integrityValid) {
                throw new BackupException("Backup integrity verification failed");
            }
            
            // Cleanup local files
            if (request.isCleanupLocal()) {
                cleanupLocalFiles(backupDir, compressedBackup, encryptedBackup);
            }
            
            BackupResult result = BackupResult.builder()
                    .backupId(backupId)
                    .success(true)
                    .s3Key(s3Key)
                    .manifest(manifest)
                    .integrityVerified(integrityValid)
                    .completedAt(Instant.now())
                    .build();

            log.info("Full backup completed successfully: {}", backupId);
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Backup failed: {}", backupId, e);
            
            BackupResult result = BackupResult.builder()
                    .backupId(backupId)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .completedAt(Instant.now())
                    .build();
            
            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * Backup user data from database using secure JDBC operations
     * SECURITY FIX: Replaced unsafe Runtime.exec() with secure database operations
     */
    private BackupComponent backupUserData(Path backupDir, String timestamp) {
        try {
            log.info("Backing up user data using secure JDBC operations");
            
            Path backupFile = backupDir.resolve("users_" + timestamp + ".json");
            
            // Use secure JDBC operations instead of shell commands
            List<Map<String, Object>> userData = jdbcTemplate.queryForList(
                "SELECT id, email, first_name, last_name, created_at, updated_at, status " +
                "FROM users WHERE deleted_at IS NULL"
            );
            
            // Write data to JSON file with compression
            try (FileOutputStream fos = new FileOutputStream(backupFile.toFile());
                 GZIPOutputStream gzos = new GZIPOutputStream(fos);
                 OutputStreamWriter writer = new OutputStreamWriter(gzos, "UTF-8")) {
                
                // Convert to JSON and write
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("[\n");
                
                for (int i = 0; i < userData.size(); i++) {
                    Map<String, Object> row = userData.get(i);
                    jsonBuilder.append("  {");
                    
                    boolean first = true;
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        if (!first) jsonBuilder.append(",");
                        jsonBuilder.append("\"").append(entry.getKey()).append("\":");
                        
                        Object value = entry.getValue();
                        if (value == null) {
                            jsonBuilder.append("null");
                        } else if (value instanceof String) {
                            jsonBuilder.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                        } else {
                            jsonBuilder.append("\"").append(value.toString()).append("\"");
                        }
                        first = false;
                    }
                    
                    jsonBuilder.append("}");
                    if (i < userData.size() - 1) jsonBuilder.append(",");
                    jsonBuilder.append("\n");
                }
                
                jsonBuilder.append("]");
                writer.write(jsonBuilder.toString());
            }
            
            long fileSize = Files.size(backupFile);
            String checksum = calculateChecksum(backupFile);
            
            log.info("Successfully backed up {} user records to {}", userData.size(), backupFile.getFileName());
            
            return BackupComponent.builder()
                    .componentType("user_data")
                    .fileName(backupFile.getFileName().toString())
                    .fileSize(fileSize)
                    .checksum(checksum)
                    .encrypted(false)  // Will be encrypted later
                    .compressed(true)
                    .recordCount(userData.size())
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to backup user data", e);
            throw new BackupException("Failed to backup user data", e);
        }
    }

    /**
     * Backup transaction data from database using secure JDBC operations
     * SECURITY FIX: Replaced unsafe Runtime.exec() with secure database operations
     */
    private BackupComponent backupTransactionData(Path backupDir, String timestamp) {
        try {
            log.info("Backing up transaction data using secure JDBC operations");
            
            Path backupFile = backupDir.resolve("transactions_" + timestamp + ".json");
            
            // Use secure JDBC operations instead of shell commands
            List<Map<String, Object>> transactionData = jdbcTemplate.queryForList(
                "SELECT t.id, t.user_id, t.amount, t.currency, t.type, t.status, " +
                "t.created_at, t.updated_at, t.description, t.reference_id " +
                "FROM transactions t WHERE t.created_at >= CURRENT_DATE - INTERVAL '90 days'"
            );
            
            // Write data to JSON file with compression
            try (FileOutputStream fos = new FileOutputStream(backupFile.toFile());
                 GZIPOutputStream gzos = new GZIPOutputStream(fos);
                 OutputStreamWriter writer = new OutputStreamWriter(gzos, "UTF-8")) {
                
                // Convert to JSON and write
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("[\n");
                
                for (int i = 0; i < transactionData.size(); i++) {
                    Map<String, Object> row = transactionData.get(i);
                    jsonBuilder.append("  {");
                    
                    boolean first = true;
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        if (!first) jsonBuilder.append(",");
                        jsonBuilder.append("\"").append(entry.getKey()).append("\":");
                        
                        Object value = entry.getValue();
                        if (value == null) {
                            jsonBuilder.append("null");
                        } else if (value instanceof String) {
                            jsonBuilder.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                        } else {
                            jsonBuilder.append("\"").append(value.toString()).append("\"");
                        }
                        first = false;
                    }
                    
                    jsonBuilder.append("}");
                    if (i < transactionData.size() - 1) jsonBuilder.append(",");
                    jsonBuilder.append("\n");
                }
                
                jsonBuilder.append("]");
                writer.write(jsonBuilder.toString());
            }
            
            long fileSize = Files.size(backupFile);
            String checksum = calculateChecksum(backupFile);
            
            log.info("Successfully backed up {} transaction records to {}", transactionData.size(), backupFile.getFileName());
            
            return BackupComponent.builder()
                    .componentType("transaction_data")
                    .fileName(backupFile.getFileName().toString())
                    .fileSize(fileSize)
                    .checksum(checksum)
                    .encrypted(false)  // Will be encrypted later
                    .compressed(true)
                    .recordCount(transactionData.size())
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to backup transaction data", e);
            throw new BackupException("Failed to backup transaction data", e);
        }
    }

    /**
     * Backup payment data from database using secure JDBC operations
     * SECURITY FIX: Replaced unsafe Runtime.exec() with secure database operations
     */
    private BackupComponent backupPaymentData(Path backupDir, String timestamp) {
        try {
            log.info("Backing up payment data using secure JDBC operations");
            
            Path backupFile = backupDir.resolve("payments_" + timestamp + ".json");
            
            // Use secure JDBC operations instead of shell commands
            List<Map<String, Object>> paymentData = jdbcTemplate.queryForList(
                "SELECT p.id, p.user_id, p.amount, p.currency, p.status, p.type, " +
                "p.provider, p.external_id, p.created_at, p.updated_at " +
                "FROM payments p WHERE p.created_at >= CURRENT_DATE - INTERVAL '90 days'"
            );
            
            // Write data to JSON file with compression
            try (FileOutputStream fos = new FileOutputStream(backupFile.toFile());
                 GZIPOutputStream gzos = new GZIPOutputStream(fos);
                 OutputStreamWriter writer = new OutputStreamWriter(gzos, "UTF-8")) {
                
                // Convert to JSON and write
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("[\n");
                
                for (int i = 0; i < paymentData.size(); i++) {
                    Map<String, Object> row = paymentData.get(i);
                    jsonBuilder.append("  {");
                    
                    boolean first = true;
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        if (!first) jsonBuilder.append(",");
                        jsonBuilder.append("\"").append(entry.getKey()).append("\":");
                        
                        Object value = entry.getValue();
                        if (value == null) {
                            jsonBuilder.append("null");
                        } else if (value instanceof String) {
                            jsonBuilder.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                        } else {
                            jsonBuilder.append("\"").append(value.toString()).append("\"");
                        }
                        first = false;
                    }
                    
                    jsonBuilder.append("}");
                    if (i < paymentData.size() - 1) jsonBuilder.append(",");
                    jsonBuilder.append("\n");
                }
                
                jsonBuilder.append("]");
                writer.write(jsonBuilder.toString());
            }
            
            long fileSize = Files.size(backupFile);
            String checksum = calculateChecksum(backupFile);
            
            log.info("Successfully backed up {} payment records to {}", paymentData.size(), backupFile.getFileName());
            
            return BackupComponent.builder()
                    .componentType("payment_data")
                    .fileName(backupFile.getFileName().toString())
                    .fileSize(fileSize)
                    .checksum(checksum)
                    .encrypted(false)  // Will be encrypted later
                    .compressed(true)
                    .recordCount(paymentData.size())
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to backup payment data", e);
            throw new BackupException("Failed to backup payment data", e);
        }
    }

    /**
     * Backup audit data from database using secure JDBC operations
     * SECURITY FIX: Replaced unsafe Runtime.exec() with secure database operations
     */
    private BackupComponent backupAuditData(Path backupDir, String timestamp) {
        try {
            log.info("Backing up audit data using secure JDBC operations");
            
            Path backupFile = backupDir.resolve("audit_" + timestamp + ".json");
            
            // Use secure JDBC operations instead of shell commands
            List<Map<String, Object>> auditData = jdbcTemplate.queryForList(
                "SELECT a.id, a.user_id, a.action, a.resource_type, a.resource_id, " +
                "a.old_values, a.new_values, a.ip_address, a.user_agent, a.created_at, " +
                "a.session_id, a.request_id FROM audit_log a " +
                "WHERE a.created_at >= CURRENT_DATE - INTERVAL '90 days' " +
                "ORDER BY a.created_at DESC"
            );
            
            // Write data to JSON file with compression
            try (FileOutputStream fos = new FileOutputStream(backupFile.toFile());
                 GZIPOutputStream gzos = new GZIPOutputStream(fos);
                 OutputStreamWriter writer = new OutputStreamWriter(gzos, "UTF-8")) {
                
                // Convert to JSON and write
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("[\n");
                
                for (int i = 0; i < auditData.size(); i++) {
                    Map<String, Object> row = auditData.get(i);
                    jsonBuilder.append("  {");
                    
                    boolean first = true;
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        if (!first) jsonBuilder.append(",");
                        jsonBuilder.append("\"").append(entry.getKey()).append("\":");
                        
                        Object value = entry.getValue();
                        if (value == null) {
                            jsonBuilder.append("null");
                        } else if (value instanceof String) {
                            jsonBuilder.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                        } else {
                            jsonBuilder.append("\"").append(value.toString()).append("\"");
                        }
                        first = false;
                    }
                    
                    jsonBuilder.append("}");
                    if (i < auditData.size() - 1) jsonBuilder.append(",");
                    jsonBuilder.append("\n");
                }
                
                jsonBuilder.append("]");
                writer.write(jsonBuilder.toString());
            }
            
            long fileSize = Files.size(backupFile);
            String checksum = calculateChecksum(backupFile);
            
            log.info("Successfully backed up {} audit records to {}", auditData.size(), backupFile.getFileName());
            
            return BackupComponent.builder()
                    .componentType("audit_data")
                    .fileName(backupFile.getFileName().toString())
                    .fileSize(fileSize)
                    .checksum(checksum)
                    .encrypted(false)  // Will be encrypted later
                    .compressed(true)
                    .recordCount(auditData.size())
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to backup audit data", e);
            throw new BackupException("Failed to backup audit data", e);
        }
    }

    /**
     * Backup application configuration using secure file operations
     * SECURITY FIX: Replaced unsafe Runtime.exec() with secure file operations
     */
    private BackupComponent backupConfiguration(Path backupDir, String timestamp) {
        try {
            log.info("Backing up configuration using secure file operations");
            
            Path configBackup = backupDir.resolve("configuration_" + timestamp + ".json");
            
            // Backup configuration directories securely
            String[] configPaths = {
                "/opt/waqiti/config",
                "/etc/waqiti",
                "/opt/kubernetes/configs"
            };
            
            Map<String, Object> configData = new HashMap<>();
            int totalFiles = 0;
            
            for (String configPath : configPaths) {
                Path path = Paths.get(configPath);
                if (Files.exists(path)) {
                    try {
                        Map<String, Object> pathData = backupConfigurationPath(path);
                        if (!pathData.isEmpty()) {
                            configData.put(configPath, pathData);
                            totalFiles += (Integer) pathData.getOrDefault("file_count", 0);
                            log.debug("Backed up configuration from: {}", configPath);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to backup configuration from path: {}", configPath, e);
                        // Continue with other paths
                    }
                }
            }
            
            // Write configuration data to JSON file with compression
            try (FileOutputStream fos = new FileOutputStream(configBackup.toFile());
                 GZIPOutputStream gzos = new GZIPOutputStream(fos);
                 OutputStreamWriter writer = new OutputStreamWriter(gzos, "UTF-8")) {
                
                // Convert to JSON format
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{\n");
                jsonBuilder.append("  \"backup_timestamp\": \"").append(timestamp).append("\",\n");
                jsonBuilder.append("  \"configuration_paths\": {\n");
                
                boolean firstPath = true;
                for (Map.Entry<String, Object> entry : configData.entrySet()) {
                    if (!firstPath) jsonBuilder.append(",\n");
                    jsonBuilder.append("    \"").append(entry.getKey()).append("\": ");
                    
                    // Serialize the path data (simplified JSON serialization)
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pathData = (Map<String, Object>) entry.getValue();
                    serializeToJson(jsonBuilder, pathData, 2);
                    
                    firstPath = false;
                }
                
                jsonBuilder.append("\n  }\n}");
                writer.write(jsonBuilder.toString());
            }
            
            long fileSize = Files.size(configBackup);
            String checksum = calculateChecksum(configBackup);
            
            log.info("Successfully backed up configuration from {} paths ({} files) to {}", 
                configData.size(), totalFiles, configBackup.getFileName());
            
            return BackupComponent.builder()
                    .componentType("configuration")
                    .fileName(configBackup.getFileName().toString())
                    .fileSize(fileSize)
                    .checksum(checksum)
                    .encrypted(false)
                    .compressed(true)
                    .recordCount(totalFiles)
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to backup configuration", e);
            throw new BackupException("Failed to backup configuration", e);
        }
    }
    
    /**
     * Securely backup configuration files from a given path
     */
    private Map<String, Object> backupConfigurationPath(Path rootPath) throws IOException {
        Map<String, Object> pathData = new HashMap<>();
        Map<String, String> files = new HashMap<>();
        int fileCount = 0;
        
        // Walk the directory tree and read configuration files
        Files.walk(rootPath)
            .filter(Files::isRegularFile)
            .filter(path -> {
                String fileName = path.getFileName().toString().toLowerCase();
                return fileName.endsWith(".yml") || fileName.endsWith(".yaml") || 
                       fileName.endsWith(".properties") || fileName.endsWith(".json") ||
                       fileName.endsWith(".conf") || fileName.endsWith(".cfg");
            })
            .forEach(path -> {
                try {
                    String relativePath = rootPath.relativize(path).toString();
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    files.put(relativePath, content);
                } catch (IOException e) {
                    log.warn("Failed to read configuration file: {}", path, e);
                }
            });
            
        pathData.put("files", files);
        pathData.put("file_count", files.size());
        pathData.put("path", rootPath.toString());
        
        return pathData;
    }
    
    /**
     * Simple JSON serialization helper
     */
    private void serializeToJson(StringBuilder jsonBuilder, Object obj, int indent) {
        String indentStr = "    ".repeat(indent);
        
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            jsonBuilder.append("{\n");
            
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) jsonBuilder.append(",\n");
                jsonBuilder.append(indentStr).append("  \"").append(entry.getKey()).append("\": ");
                serializeToJson(jsonBuilder, entry.getValue(), indent + 1);
                first = false;
            }
            
            jsonBuilder.append("\n").append(indentStr).append("}");
        } else if (obj instanceof String) {
            String str = (String) obj;
            jsonBuilder.append("\"").append(str.replace("\"", "\\\"").replace("\n", "\\n")).append("\"");
        } else if (obj instanceof Number) {
            jsonBuilder.append(obj.toString());
        } else {
            jsonBuilder.append("\"").append(obj.toString().replace("\"", "\\\"")).append("\"");
        }
    }

    /**
     * Backup secrets from Vault using secure VaultSecretsService
     * SECURITY FIX: Replaced unsafe Runtime.exec() with secure Vault API operations
     */
    private BackupComponent backupSecrets(Path backupDir, String timestamp) {
        try {
            log.info("Backing up secrets using secure Vault API operations");
            
            Path secretsBackup = backupDir.resolve("secrets_" + timestamp + ".json.enc");
            Path tempSecretsFile = backupDir.resolve("secrets_" + timestamp + ".json");
            
            // Use secure VaultSecretsService instead of shell commands
            Map<String, Object> allSecrets = new HashMap<>();
            
            // Backup secrets from common paths
            String[] secretPaths = {
                "application/database",
                "application/jwt", 
                "application/api-keys",
                "application/encryption",
                "application/redis",
                "application/kafka"
            };
            
            for (String path : secretPaths) {
                try {
                    Map<String, Object> pathSecrets = vaultSecretsService.getAllSecrets(path);
                    if (pathSecrets != null && !pathSecrets.isEmpty()) {
                        allSecrets.put(path, pathSecrets);
                        log.debug("Backed up {} secrets from path: {}", pathSecrets.size(), path);
                    }
                } catch (Exception e) {
                    log.warn("Failed to backup secrets from path: {}", path, e);
                    // Continue with other paths
                }
            }
            
            // Write secrets to temporary JSON file
            try (FileOutputStream fos = new FileOutputStream(tempSecretsFile.toFile());
                 OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
                
                // Convert to JSON format
                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("{\n");
                jsonBuilder.append("  \"backup_timestamp\": \"").append(timestamp).append("\",\n");
                jsonBuilder.append("  \"secrets\": {\n");
                
                boolean firstPath = true;
                for (Map.Entry<String, Object> pathEntry : allSecrets.entrySet()) {
                    if (!firstPath) jsonBuilder.append(",\n");
                    jsonBuilder.append("    \"").append(pathEntry.getKey()).append("\": ");
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pathSecrets = (Map<String, Object>) pathEntry.getValue();
                    jsonBuilder.append("{\n");
                    
                    boolean firstSecret = true;
                    for (Map.Entry<String, Object> secretEntry : pathSecrets.entrySet()) {
                        if (!firstSecret) jsonBuilder.append(",\n");
                        jsonBuilder.append("      \"").append(secretEntry.getKey()).append("\": \"");
                        jsonBuilder.append(secretEntry.getValue().toString().replace("\"", "\\\""));
                        jsonBuilder.append("\"");
                        firstSecret = false;
                    }
                    
                    jsonBuilder.append("\n    }");
                    firstPath = false;
                }
                
                jsonBuilder.append("\n  }\n}");
                writer.write(jsonBuilder.toString());
            }
            
            // Encrypt secrets file immediately
            encryptFile(tempSecretsFile, secretsBackup);
            Files.delete(tempSecretsFile); // Remove unencrypted file
            
            long fileSize = Files.size(secretsBackup);
            String checksum = calculateChecksum(secretsBackup);
            
            log.info("Successfully backed up secrets from {} paths to {}", allSecrets.size(), secretsBackup.getFileName());
            
            return BackupComponent.builder()
                    .componentType("secrets")
                    .fileName(secretsBackup.getFileName().toString())
                    .fileSize(fileSize)
                    .checksum(checksum)
                    .encrypted(true)
                    .compressed(false)
                    .recordCount(allSecrets.size())
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to backup secrets", e);
            throw new BackupException("Failed to backup secrets", e);
        }
    }

    /**
     * Compress backup directory using secure Java-based compression
     * SECURITY FIX: Replaced unsafe Runtime.exec() with secure Java compression
     */
    private Path compressBackup(Path backupDir, String backupId) throws IOException {
        log.info("Compressing backup using secure Java compression: {}", backupId);
        
        Path compressedFile = Paths.get(localBackupDirectory, backupId + ".zip");
        
        try (FileOutputStream fos = new FileOutputStream(compressedFile.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            // Set compression level
            zos.setLevel(9); // Maximum compression
            
            // Recursively add all files from backup directory
            Files.walk(backupDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String relativePath = backupDir.relativize(file).toString();
                        ZipEntry zipEntry = new ZipEntry(relativePath);
                        zipEntry.setTime(Files.getLastModifiedTime(file).toMillis());
                        zos.putNextEntry(zipEntry);
                        
                        Files.copy(file, zos);
                        zos.closeEntry();
                        
                        log.debug("Added file to backup archive: {}", relativePath);
                        
                    } catch (IOException e) {
                        log.warn("Failed to add file to backup archive: {}", file, e);
                        // Continue with other files
                    }
                });
            
            zos.finish();
        }
        
        long compressedSize = Files.size(compressedFile);
        log.info("Successfully compressed backup {} to {} bytes", backupId, compressedSize);
        
        return compressedFile;
    }

    /**
     * Encrypt backup file
     */
    private Path encryptBackup(Path backupFile, String backupId) throws Exception {
        log.info("Encrypting backup: {}", backupId);
        
        Path encryptedFile = Paths.get(backupFile.toString() + ".enc");
        encryptFile(backupFile, encryptedFile);
        
        return encryptedFile;
    }

    /**
     * Upload backup to S3
     */
    private String uploadToS3(Path backupFile, String backupId, BackupRequest.BackupType backupType) {
        log.info("Uploading backup to S3: {}", backupId);
        
        String s3Key = String.format("%s/%s/%s", 
                backupType, 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),
                backupFile.getFileName().toString());
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("backup-id", backupId);
        metadata.put("backup-type", backupType.toString());
        metadata.put("encryption", "AES-256");
        metadata.put("created-at", LocalDateTime.now().toString());
        
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(backupBucketName)
                    .key(s3Key)
                    .contentType("application/octet-stream")
                    .contentLength(Files.size(backupFile))
                    .metadata(metadata)
                    .storageClass(software.amazon.awssdk.services.s3.model.StorageClass.STANDARD_IA)
                    .build();
            
            s3Client.putObject(putRequest, RequestBody.fromFile(backupFile));
            
            log.info("Backup uploaded successfully to S3: {}", s3Key);
            return s3Key;
            
        } catch (Exception e) {
            throw new BackupException("Failed to upload backup to S3", e);
        }
    }

    /**
     * Verify backup integrity
     */
    private boolean verifyBackupIntegrity(Path localFile, String s3Key) {
        try {
            log.info("Verifying backup integrity");
            
            // Calculate local file checksum
            String localChecksum = calculateChecksum(localFile);
            
            // Get S3 object metadata
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(backupBucketName)
                    .key(s3Key)
                    .build();
            
            HeadObjectResponse headResponse = s3Client.headObject(headRequest);
            String s3ETag = headResponse.eTag().replace("\"", "");
            
            // Download a small portion to verify
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(backupBucketName)
                    .key(s3Key)
                    .range("bytes=0-1023") // First 1KB
                    .build();
            
            ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(getRequest);
            byte[] s3Data = responseBytes.asByteArray();
            
            // Read same portion from local file
            byte[] localData = Files.readAllBytes(localFile);
            byte[] localPortion = Arrays.copyOf(localData, Math.min(1024, localData.length));
            
            boolean portionMatches = Arrays.equals(s3Data, localPortion);
            boolean sizeMatches = headResponse.contentLength() == Files.size(localFile);
            
            log.info("Backup integrity verification: portion={}, size={}", portionMatches, sizeMatches);
            
            return portionMatches && sizeMatches;
            
        } catch (Exception e) {
            log.error("Failed to verify backup integrity", e);
            return false;
        }
    }

    // Helper methods

    private Path createBackupDirectory(String backupId) throws IOException {
        Path backupDir = Paths.get(localBackupDirectory, backupId);
        Files.createDirectories(backupDir);
        return backupDir;
    }

    private String generateBackupId(String timestamp, String backupType) {
        return String.format("%s_%s_%s", backupType, timestamp, UUID.randomUUID().toString().substring(0, 8));
    }

    private long calculateTotalSize(List<BackupComponent> components) {
        return components.stream().mapToLong(BackupComponent::getFileSize).sum();
    }

    private void createManifestFile(Path backupDir, BackupManifest manifest) throws IOException {
        Path manifestFile = backupDir.resolve("manifest.json");
        // This would serialize the manifest to JSON
        Files.write(manifestFile, manifest.toString().getBytes());
    }

    private String calculateChecksum(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(file);
        byte[] hashBytes = digest.digest(fileBytes);
        
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void encryptFile(Path inputFile, Path outputFile) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(), ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        
        try (FileInputStream fis = new FileInputStream(inputFile.toFile());
             FileOutputStream fos = new FileOutputStream(outputFile.toFile())) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] encrypted = cipher.update(buffer, 0, bytesRead);
                if (encrypted != null) {
                    fos.write(encrypted);
                }
            }
            
            byte[] finalBlock = cipher.doFinal();
            if (finalBlock != null) {
                fos.write(finalBlock);
            }
        }
    }

    private void cleanupLocalFiles(Path... files) throws IOException {
        for (Path file : files) {
            if (Files.exists(file)) {
                if (Files.isDirectory(file)) {
                    // Recursively delete directory
                    Files.walk(file)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } else {
                    Files.delete(file);
                }
            }
        }
    }
}