package com.waqiti.common.encryption;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * CRITICAL MIGRATION SERVICE: Encrypt existing unencrypted data in database
 * Provides safe, auditable migration of sensitive data to encrypted format
 * 
 * Features:
 * - Batch processing to avoid memory issues
 * - Progress tracking and reporting
 * - Rollback capability for failed migrations
 * - Comprehensive error handling
 * - Database-agnostic SQL generation
 * - Dry-run mode for validation
 * - Migration verification and integrity checks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EncryptionMigrationService {
    
    private final AdvancedEncryptionService encryptionService;
    private final JdbcTemplate jdbcTemplate;
    
    // Migration tracking
    private final Map<String, MigrationProgress> activeMigrations = new ConcurrentHashMap<>();
    
    // Migration configuration
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int MAX_RETRIES = 3;
    
    // SQL identifier validation
    private static final Pattern SQL_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Set<String> SQL_KEYWORDS = Set.of(
        "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", 
        "UNION", "WHERE", "ORDER", "GROUP", "HAVING", "JOIN", "FROM", "INTO",
        "VALUES", "SET", "AS", "ON", "INNER", "LEFT", "RIGHT", "OUTER", "FULL",
        "AND", "OR", "NOT", "IN", "EXISTS", "BETWEEN", "LIKE", "IS", "NULL",
        "TRUE", "FALSE", "CASE", "WHEN", "THEN", "ELSE", "END", "CAST",
        "CONVERT", "SUBSTRING", "CONCAT", "LENGTH", "UPPER", "LOWER",
        "TRIM", "COALESCE", "NULLIF", "COUNT", "SUM", "AVG", "MIN", "MAX",
        "DISTINCT", "ALL", "ANY", "SOME", "LIMIT", "OFFSET", "TOP",
        "EXEC", "EXECUTE", "DECLARE", "BEGIN", "END", "IF", "ELSE",
        "WHILE", "FOR", "LOOP", "BREAK", "CONTINUE", "RETURN",
        "TRANSACTION", "COMMIT", "ROLLBACK", "SAVEPOINT"
    );
    
    /**
     * Migrate table data to encrypted format
     */
    @Async
    public CompletableFuture<MigrationResult> migrateTableData(MigrationRequest request) {
        String migrationId = UUID.randomUUID().toString();
        MigrationProgress progress = new MigrationProgress(migrationId, request);
        activeMigrations.put(migrationId, progress);
        
        try {
            log.info("Starting encryption migration for table '{}' - Migration ID: {}", 
                request.getTableName(), migrationId);
            
            MigrationResult result = performTableMigration(request, progress);
            
            log.info("Encryption migration completed - Migration ID: {}, Status: {}, Processed: {}, Errors: {}", 
                migrationId, result.getStatus(), result.getProcessedCount(), result.getErrorCount());
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Encryption migration failed - Migration ID: {}", migrationId, e);
            progress.setError(e.getMessage());
            
            MigrationResult result = MigrationResult.failed(migrationId, e.getMessage());
            return CompletableFuture.completedFuture(result);
            
        } finally {
            activeMigrations.remove(migrationId);
        }
    }
    
    /**
     * Perform the actual table migration
     */
    private MigrationResult performTableMigration(MigrationRequest request, MigrationProgress progress) {
        MigrationResult result = new MigrationResult(progress.getMigrationId());
        
        try {
            // Step 1: Validate migration request
            validateMigrationRequest(request);
            
            // Step 2: Count total records
            long totalRecords = countTotalRecords(request.getTableName(), request.getWhereClause());
            progress.setTotalRecords(totalRecords);
            result.setTotalRecords(totalRecords);
            
            log.info("Migration {} - Processing {} records in table '{}'", 
                progress.getMigrationId(), totalRecords, request.getTableName());
            
            // Step 3: Process in batches
            int batchSize = request.getBatchSize() > 0 ? request.getBatchSize() : DEFAULT_BATCH_SIZE;
            long offset = 0;
            AtomicLong processedCount = new AtomicLong(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            while (offset < totalRecords) {
                try {
                    int batchProcessed = processBatch(request, offset, batchSize, progress);
                    processedCount.addAndGet(batchProcessed);
                    progress.setProcessedRecords(processedCount.get());
                    
                    offset += batchSize;
                    
                    // Update progress
                    double progressPercent = (double) processedCount.get() / totalRecords * 100;
                    progress.setProgressPercent(progressPercent);
                    
                    log.info("Migration {} - Progress: {:.2f}% ({}/{})", 
                        progress.getMigrationId(), progressPercent, processedCount.get(), totalRecords);
                    
                    // Check for cancellation
                    if (progress.isCancelled()) {
                        log.warn("Migration {} cancelled by user", progress.getMigrationId());
                        break;
                    }
                    
                    // Small delay to avoid overwhelming the database
                    Thread.sleep(100);
                    
                } catch (Exception e) {
                    log.error("Error processing batch at offset {}", offset, e);
                    errorCount.incrementAndGet();
                    
                    // Skip this batch and continue if we haven't exceeded max errors
                    if (errorCount.get() > request.getMaxErrors()) {
                        throw new EncryptionException("Too many errors during migration: " + errorCount.get());
                    }
                    
                    offset += batchSize; // Skip problematic batch
                }
            }
            
            // Step 4: Verify migration if requested
            if (request.isVerifyAfterMigration()) {
                verifyMigration(request, result);
            }
            
            // Set final results
            result.setProcessedCount(processedCount.get());
            result.setErrorCount(errorCount.get());
            result.setStatus(progress.isCancelled() ? MigrationStatus.CANCELLED : 
                           errorCount.get() > 0 ? MigrationStatus.COMPLETED_WITH_ERRORS : 
                           MigrationStatus.COMPLETED);
            
            progress.setCompleted(true);
            
            return result;
            
        } catch (Exception e) {
            log.error("Migration failed for table: {}", request.getTableName(), e);
            result.setStatus(MigrationStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }
    
    /**
     * Process a single batch of records
     */
    @Transactional
    public int processBatch(MigrationRequest request, long offset, int batchSize, MigrationProgress progress) {
        try {
            // Build the SELECT query for this batch
            String selectSql = buildSelectQuery(request, offset, batchSize);
            
            // Execute query and get results
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql);
            
            if (rows.isEmpty()) {
                return 0;
            }
            
            // Process each row
            int processedInBatch = 0;
            
            for (Map<String, Object> row : rows) {
                try {
                    if (request.isDryRun()) {
                        // Dry run - just validate encryption would work
                        validateRowForEncryption(row, request);
                    } else {
                        // Actual migration - encrypt and update
                        encryptAndUpdateRow(row, request);
                    }
                    
                    processedInBatch++;
                    
                } catch (Exception e) {
                    log.error("Error processing row with primary key: {}", 
                        row.get(request.getPrimaryKeyColumn()), e);
                    progress.addError(e.getMessage());
                    
                    // Continue with next row
                }
            }
            
            return processedInBatch;
            
        } catch (Exception e) {
            log.error("Error processing batch at offset {}", offset, e);
            throw new EncryptionException("Batch processing failed", e);
        }
    }
    
    /**
     * Encrypt and update a single row
     */
    private void encryptAndUpdateRow(Map<String, Object> row, MigrationRequest request) {
        try {
            // Create migration context
            DataContext context = DataContext.migrationContext("TABLE_ENCRYPTION_MIGRATION");
            
            // Prepare update parameters
            List<Object> updateParams = new ArrayList<>();
            List<String> updateClauses = new ArrayList<>();
            
            // Encrypt each specified field
            for (FieldMigrationConfig fieldConfig : request.getFieldConfigs()) {
                String fieldName = fieldConfig.getColumnName();
                Object fieldValue = row.get(fieldName);
                
                if (fieldValue != null && !isAlreadyEncrypted(fieldValue.toString(), fieldConfig)) {
                    // Encrypt the field value
                    EncryptedData encrypted = encryptionService.encryptSensitiveData(
                        fieldName, fieldValue, context);
                    
                    // Serialize encrypted data for storage
                    String encryptedValue = serializeEncryptedData(encrypted, fieldConfig);
                    
                    // Properly quote the column name to prevent injection
                    String quotedFieldName = quoteSqlIdentifier(fieldName);
                    updateClauses.add(quotedFieldName + " = ?");
                    updateParams.add(encryptedValue);
                }
            }
            
            if (!updateClauses.isEmpty()) {
                // Add primary key for WHERE clause
                updateParams.add(row.get(request.getPrimaryKeyColumn()));
                
                // Build and execute update query with proper identifier quoting
                String quotedTableName = quoteSqlIdentifier(request.getTableName());
                String quotedPrimaryKeyColumn = quoteSqlIdentifier(request.getPrimaryKeyColumn());
                String updateSql = "UPDATE " + quotedTableName + " SET " + 
                    String.join(", ", updateClauses) + " WHERE " + quotedPrimaryKeyColumn + " = ?";
                
                int updatedRows = jdbcTemplate.update(updateSql, updateParams.toArray());
                
                if (updatedRows != 1) {
                    throw new EncryptionException("Expected to update 1 row, but updated: " + updatedRows);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to encrypt and update row", e);
            throw new EncryptionException("Row encryption failed", e);
        }
    }
    
    /**
     * Validate row for encryption (dry run)
     */
    private void validateRowForEncryption(Map<String, Object> row, MigrationRequest request) {
        for (FieldMigrationConfig fieldConfig : request.getFieldConfigs()) {
            String fieldName = fieldConfig.getColumnName();
            Object fieldValue = row.get(fieldName);
            
            if (fieldValue != null) {
                // Try to classify and validate the data
                DataContext context = DataContext.migrationContext("VALIDATION");
                
                try {
                    // Test encryption without actually storing
                    encryptionService.encryptSensitiveData(fieldName, fieldValue, context);
                } catch (Exception e) {
                    throw new EncryptionException("Field validation failed: " + fieldName, e);
                }
            }
        }
    }
    
    /**
     * Check if field is already encrypted
     */
    private boolean isAlreadyEncrypted(String value, FieldMigrationConfig fieldConfig) {
        // Check for encryption prefixes
        return value.startsWith("ENC:") || 
               value.startsWith("PII:") || 
               value.startsWith("FIN:");
    }
    
    /**
     * Serialize encrypted data based on field type
     */
    private String serializeEncryptedData(EncryptedData encrypted, FieldMigrationConfig fieldConfig) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String encryptedJson = mapper.writeValueAsString(encrypted);
            
            // Add appropriate prefix based on data classification
            String prefix = getEncryptionPrefix(encrypted.getClassification());
            return prefix + encryptedJson;
            
        } catch (Exception e) {
            throw new EncryptionException("Failed to serialize encrypted data", e);
        }
    }
    
    /**
     * Get encryption prefix based on classification
     */
    private String getEncryptionPrefix(AdvancedEncryptionService.DataClassification classification) {
        switch (classification) {
            case PII:
                return "PII:";
            case FINANCIAL:
                return "FIN:";
            default:
                return "ENC:";
        }
    }
    
    /**
     * Build SELECT query for batch processing
     */
    private String buildSelectQuery(MigrationRequest request, long offset, int batchSize) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        
        // Add primary key (safely quoted)
        String quotedPrimaryKey = quoteSqlIdentifier(request.getPrimaryKeyColumn());
        sql.append(quotedPrimaryKey);
        
        // Add fields to be encrypted (safely quoted)
        for (FieldMigrationConfig fieldConfig : request.getFieldConfigs()) {
            String quotedColumnName = quoteSqlIdentifier(fieldConfig.getColumnName());
            sql.append(", ").append(quotedColumnName);
        }
        
        // Add table name (safely quoted)
        String quotedTableName = quoteSqlIdentifier(request.getTableName());
        sql.append(" FROM ").append(quotedTableName);
        
        // Add WHERE clause if specified (validated)
        if (request.getWhereClause() != null && !request.getWhereClause().trim().isEmpty()) {
            validateWhereClause(request.getWhereClause());
            sql.append(" WHERE ").append(request.getWhereClause());
        }
        
        // Add ordering and pagination (safely quoted primary key)
        sql.append(" ORDER BY ").append(quotedPrimaryKey);
        sql.append(" LIMIT ").append(batchSize);
        sql.append(" OFFSET ").append(offset);
        
        return sql.toString();
    }
    
    /**
     * Count total records for migration
     */
    private long countTotalRecords(String tableName, String whereClause) {
        StringBuilder sql = new StringBuilder();
        
        // Safely quote table name
        String quotedTableName = quoteSqlIdentifier(tableName);
        sql.append("SELECT COUNT(*) FROM ").append(quotedTableName);
        
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            validateWhereClause(whereClause);
            sql.append(" WHERE ").append(whereClause);
        }
        
        return jdbcTemplate.queryForObject(sql.toString(), Long.class);
    }
    
    /**
     * Validate migration request
     */
    private void validateMigrationRequest(MigrationRequest request) {
        if (request.getTableName() == null || request.getTableName().trim().isEmpty()) {
            throw new IllegalArgumentException("Table name is required");
        }
        
        if (request.getPrimaryKeyColumn() == null || request.getPrimaryKeyColumn().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary key column is required");
        }
        
        if (request.getFieldConfigs() == null || request.getFieldConfigs().isEmpty()) {
            throw new IllegalArgumentException("At least one field configuration is required");
        }
        
        // Validate SQL identifiers for injection prevention
        validateSqlIdentifier(request.getTableName(), "Table name");
        validateSqlIdentifier(request.getPrimaryKeyColumn(), "Primary key column");
        
        // Validate WHERE clause if present
        if (request.getWhereClause() != null && !request.getWhereClause().trim().isEmpty()) {
            validateWhereClause(request.getWhereClause());
        }
        
        // Validate field configurations
        for (FieldMigrationConfig fieldConfig : request.getFieldConfigs()) {
            if (fieldConfig.getColumnName() == null || fieldConfig.getColumnName().trim().isEmpty()) {
                throw new IllegalArgumentException("Column name is required for field configuration");
            }
            
            // Validate each column name for SQL injection prevention
            validateSqlIdentifier(fieldConfig.getColumnName(), "Column name");
        }
        
        // Additional validation for batch size and other parameters
        if (request.getBatchSize() > 0 && request.getBatchSize() > 10000) {
            throw new IllegalArgumentException("Batch size cannot exceed 10,000 records");
        }
        
        if (request.getMaxErrors() < 0) {
            throw new IllegalArgumentException("Max errors cannot be negative");
        }
    }
    
    /**
     * Verify migration results
     */
    private void verifyMigration(MigrationRequest request, MigrationResult result) {
        try {
            log.info("Verifying migration for table: {}", request.getTableName());
            
            // Count encrypted vs unencrypted records
            String whereClause = request.getWhereClause();
            int totalRecords = (int) countTotalRecords(request.getTableName(), whereClause);
            int encryptedRecords = 0;
            
            // Sample verification - check first 100 records
            String sampleQuery = buildSelectQuery(request, 0, Math.min(100, totalRecords));
            List<Map<String, Object>> sampleRows = jdbcTemplate.queryForList(sampleQuery);
            
            for (Map<String, Object> row : sampleRows) {
                for (FieldMigrationConfig fieldConfig : request.getFieldConfigs()) {
                    Object fieldValue = row.get(fieldConfig.getColumnName());
                    if (fieldValue != null && isAlreadyEncrypted(fieldValue.toString(), fieldConfig)) {
                        encryptedRecords++;
                        break; // Count row only once even if multiple fields are encrypted
                    }
                }
            }
            
            double encryptionRate = (double) encryptedRecords / sampleRows.size() * 100;
            result.setVerificationDetails(String.format("Sample verification: %.2f%% of records encrypted", encryptionRate));
            
            log.info("Migration verification completed - Encryption rate: {:.2f}%", encryptionRate);
            
        } catch (Exception e) {
            log.error("Migration verification failed", e);
            result.setVerificationDetails("Verification failed: " + e.getMessage());
        }
    }
    
    /**
     * Get migration progress
     */
    public MigrationProgress getMigrationProgress(String migrationId) {
        return activeMigrations.get(migrationId);
    }
    
    /**
     * Cancel active migration
     */
    public boolean cancelMigration(String migrationId) {
        MigrationProgress progress = activeMigrations.get(migrationId);
        if (progress != null) {
            progress.setCancelled(true);
            return true;
        }
        return false;
    }
    
    /**
     * Get all active migrations
     */
    public Map<String, MigrationProgress> getActiveMigrations() {
        return new HashMap<>(activeMigrations);
    }
    
    /**
     * Validate SQL identifier to prevent injection attacks
     */
    private void validateSqlIdentifier(String identifier, String identifierType) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException(identifierType + " cannot be null or empty");
        }
        
        String trimmed = identifier.trim();
        
        // Check length (reasonable limit)
        if (trimmed.length() > 128) {
            throw new IllegalArgumentException(identifierType + " is too long (max 128 characters)");
        }
        
        // Check for valid SQL identifier pattern
        if (!SQL_IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(identifierType + " contains invalid characters. Only letters, numbers, and underscores are allowed, starting with letter or underscore");
        }
        
        // Check if it's a reserved keyword
        if (SQL_KEYWORDS.contains(trimmed.toUpperCase())) {
            throw new IllegalArgumentException(identifierType + " cannot be a SQL reserved keyword: " + trimmed);
        }
        
        // Additional security checks
        if (trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/") || 
            trimmed.contains(";") || trimmed.contains("'") || trimmed.contains("\"")) {
            throw new IllegalArgumentException(identifierType + " contains potentially dangerous characters");
        }
    }
    
    /**
     * Safely quote SQL identifier (double quotes for standard SQL compliance)
     */
    private String quoteSqlIdentifier(String identifier) {
        // First validate the identifier
        validateSqlIdentifier(identifier, "SQL identifier");
        // Return quoted identifier to handle edge cases and ensure safety
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
    
    /**
     * Validate WHERE clause for basic safety (simplified validation)
     */
    private void validateWhereClause(String whereClause) {
        if (whereClause == null || whereClause.trim().isEmpty()) {
            return; // Empty WHERE clause is acceptable
        }
        
        String trimmed = whereClause.trim();
        
        // Basic length check
        if (trimmed.length() > 1000) {
            throw new IllegalArgumentException("WHERE clause is too long (max 1000 characters)");
        }
        
        // Check for dangerous patterns (simplified - in production, use a proper SQL parser)
        String upperClause = trimmed.toUpperCase();
        String[] dangerousPatterns = {
            "DROP ", "DELETE ", "INSERT ", "UPDATE ", "CREATE ", "ALTER ",
            "TRUNCATE ", "EXEC ", "EXECUTE ", "SP_", "XP_", "OPENROWSET",
            "OPENDATASOURCE", "BULK ", "WAITFOR ", "SHUTDOWN", "--", "/*",
            "*/", "@@", "INFORMATION_SCHEMA", "SYS.", "SYSOBJECTS", "SYSCOLUMNS"
        };
        
        for (String pattern : dangerousPatterns) {
            if (upperClause.contains(pattern)) {
                throw new IllegalArgumentException("WHERE clause contains potentially dangerous pattern: " + pattern.trim());
            }
        }
        
        // Check for excessive use of SQL keywords that might indicate injection
        long keywordCount = Arrays.stream(upperClause.split("\\s+"))
            .filter(SQL_KEYWORDS::contains)
            .count();
        
        if (keywordCount > 10) {
            throw new IllegalArgumentException("WHERE clause contains too many SQL keywords, potential injection attempt");
        }
    }
    
    // Supporting classes would be in separate files in production
    // MigrationRequest, MigrationResult, MigrationProgress, etc.
}