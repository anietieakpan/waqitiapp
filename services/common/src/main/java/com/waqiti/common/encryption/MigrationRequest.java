package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Migration request configuration
 */
@Data
@Builder
public class MigrationRequest {
    
    private String tableName;
    private String primaryKeyColumn;
    private List<FieldMigrationConfig> fieldConfigs;
    private String whereClause;
    
    @Builder.Default
    private int batchSize = 1000;
    
    @Builder.Default
    private boolean dryRun = false;
    
    @Builder.Default
    private boolean verifyAfterMigration = true;
    
    @Builder.Default
    private int maxErrors = 100;
    
    @Builder.Default
    private boolean createBackup = true;
    
    private String backupTableName;
    
    /**
     * Create migration request for user table
     */
    public static MigrationRequest forUserTable() {
        return MigrationRequest.builder()
            .tableName("users")
            .primaryKeyColumn("id")
            .fieldConfigs(List.of(
                FieldMigrationConfig.pii("email"),
                FieldMigrationConfig.pii("phone_number"),
                FieldMigrationConfig.pii("full_name"),
                FieldMigrationConfig.pii("address"),
                FieldMigrationConfig.pii("date_of_birth"),
                FieldMigrationConfig.confidential("encrypted_password")
            ))
            .build();
    }
    
    /**
     * Create migration request for wallet/account table
     */
    public static MigrationRequest forWalletTable() {
        return MigrationRequest.builder()
            .tableName("wallets")
            .primaryKeyColumn("id")
            .fieldConfigs(List.of(
                FieldMigrationConfig.financial("account_number"),
                FieldMigrationConfig.financial("routing_number"),
                FieldMigrationConfig.financial("iban"),
                FieldMigrationConfig.financial("balance"),
                FieldMigrationConfig.pii("account_holder_name")
            ))
            .build();
    }
    
    /**
     * Create migration request for transaction table
     */
    public static MigrationRequest forTransactionTable() {
        return MigrationRequest.builder()
            .tableName("transactions")
            .primaryKeyColumn("id")
            .fieldConfigs(List.of(
                FieldMigrationConfig.financial("amount"),
                FieldMigrationConfig.financial("sender_account_info"),
                FieldMigrationConfig.financial("recipient_account_info"),
                FieldMigrationConfig.sensitive("transaction_details"),
                FieldMigrationConfig.pii("sender_name"),
                FieldMigrationConfig.pii("recipient_name")
            ))
            .build();
    }
}