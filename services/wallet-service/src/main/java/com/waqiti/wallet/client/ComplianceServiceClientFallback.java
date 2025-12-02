package com.waqiti.wallet.client;

import com.waqiti.wallet.dto.RegulatoryClosureRequest;
import com.waqiti.wallet.dto.WalletClosureComplianceRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for ComplianceServiceClient
 *
 * Provides graceful degradation when compliance-service is unavailable.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Slf4j
@Component
public class ComplianceServiceClientFallback implements ComplianceServiceClient {

    @Override
    public void reportWalletClosure(WalletClosureComplianceRequest request) {
        log.warn("ComplianceServiceClient fallback triggered for wallet closure: userId={}, walletCount={}",
                request.getUserId(), request.getWalletIds().size());
        // Compliance reporting failure should not block wallet closure
        // Will be retried via circuit breaker or handled via manual process
    }

    @Override
    public void reportRegulatoryWalletClosure(RegulatoryClosureRequest request) {
        log.error("ComplianceServiceClient fallback triggered for regulatory closure: userId={}, regulation={}",
                request.getUserId(), request.getRegulationReference());
        // Critical: Regulatory reporting failures require manual intervention
    }
}
