package com.waqiti.wallet.service;

import java.util.UUID;

/**
 * Wallet Compliance Service Interface
 * 
 * Handles compliance-related operations for wallet freezes
 * and regulatory reporting requirements
 */
public interface WalletComplianceService {
    
    /**
     * Update compliance status for wallet operations
     */
    void updateComplianceStatus(UUID userId, String freezeReason, String caseId, 
                              int affectedWallets, boolean regulatoryNotificationRequired);
}