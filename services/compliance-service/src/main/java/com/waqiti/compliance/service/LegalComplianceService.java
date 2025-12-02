package com.waqiti.compliance.service;

import com.waqiti.compliance.domain.AssetFreeze;
import com.waqiti.compliance.domain.LegalOrder;
import com.waqiti.compliance.domain.LegalOrder.OrderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * CRITICAL P0 FIX: Service for legal compliance operations
 *
 * Integrates with LegalOrderProcessingService to implement full legal order workflow.
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-05
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LegalComplianceService {

    private final LegalOrderProcessingService legalOrderProcessingService;

    /**
     * CRITICAL P0 FIX: Process legal order asset freeze
     *
     * Implements comprehensive legal order processing workflow:
     * - Document legal order
     * - Notify legal department
     * - Track compliance timeline
     * - Execute wallet freeze
     * - Create audit trail
     */
    public LegalOrder processLegalAssetFreeze(AssetFreeze freeze, String correlationId) {
        log.warn("LEGAL: Processing legal order asset freeze - userId: {}, freezeId: {}, legalOrder: {}, correlationId: {}",
            freeze.getUserId(), freeze.getFreezeId(), freeze.getLegalOrder(), correlationId);

        try {
            // Extract legal order details from asset freeze
            String orderNumber = extractOrderNumber(freeze);
            OrderType orderType = determineOrderType(freeze);
            String issuingAuthority = extractIssuingAuthority(freeze);
            String jurisdiction = extractJurisdiction(freeze);

            // Create and process legal order
            LegalOrder legalOrder = legalOrderProcessingService.processIncomingLegalOrder(
                orderNumber,
                orderType,
                UUID.fromString(freeze.getUserId()),
                null, // walletId not specified in AssetFreeze
                freeze.getAmount(),
                freeze.getCurrency(),
                issuingAuthority,
                jurisdiction,
                freeze.getCaseNumber(),
                null, // judgeName not in AssetFreeze
                LocalDate.now(), // issueDate
                null, // expirationDate not in AssetFreeze
                freeze.getReason(),
                null, // documentPath
                correlationId
            );

            log.info("LEGAL: Legal order created for asset freeze - Order ID: {}, Freeze ID: {}",
                legalOrder.getOrderId(), freeze.getFreezeId());

            return legalOrder;

        } catch (Exception e) {
            log.error("LEGAL: Failed to process legal asset freeze - Freeze ID: {}, Correlation: {}",
                freeze.getFreezeId(), correlationId, e);
            throw new RuntimeException("Failed to process legal asset freeze", e);
        }
    }

    /**
     * CRITICAL P0 FIX: Process court order asset freeze
     *
     * Implements court order processing workflow:
     * - Verify court order authenticity
     * - Document court case details
     * - Notify legal counsel
     * - Track court order compliance
     * - Execute wallet freeze
     */
    public LegalOrder processCourtOrderAssetFreeze(AssetFreeze freeze, String correlationId) {
        log.warn("LEGAL: Processing court order asset freeze - userId: {}, freezeId: {}, legalOrder: {}, correlationId: {}",
            freeze.getUserId(), freeze.getFreezeId(), freeze.getLegalOrder(), correlationId);

        try {
            // Extract court order details from asset freeze
            String orderNumber = extractOrderNumber(freeze);
            String issuingAuthority = extractIssuingAuthority(freeze);
            String jurisdiction = extractJurisdiction(freeze);

            // Create and process court order
            LegalOrder legalOrder = legalOrderProcessingService.processIncomingLegalOrder(
                orderNumber,
                OrderType.COURT_ORDER,
                UUID.fromString(freeze.getUserId()),
                null, // walletId not specified in AssetFreeze
                freeze.getAmount(),
                freeze.getCurrency(),
                issuingAuthority,
                jurisdiction,
                freeze.getCaseNumber(),
                null, // judgeName not in AssetFreeze
                LocalDate.now(), // issueDate
                null, // expirationDate not in AssetFreeze
                freeze.getReason(),
                null, // documentPath
                correlationId
            );

            log.info("LEGAL: Court order created for asset freeze - Order ID: {}, Freeze ID: {}",
                legalOrder.getOrderId(), freeze.getFreezeId());

            return legalOrder;

        } catch (Exception e) {
            log.error("LEGAL: Failed to process court order asset freeze - Freeze ID: {}, Correlation: {}",
                freeze.getFreezeId(), correlationId, e);
            throw new RuntimeException("Failed to process court order asset freeze", e);
        }
    }

    // Helper methods to extract data from AssetFreeze

    private String extractOrderNumber(AssetFreeze freeze) {
        return freeze.getLegalOrder() != null ?
            freeze.getLegalOrder() : "ORDER-" + freeze.getFreezeId();
    }

    private OrderType determineOrderType(AssetFreeze freeze) {
        if (freeze.getFreezeType() != null) {
            String type = freeze.getFreezeType().toUpperCase();
            if (type.contains("GARNISHMENT")) return OrderType.GARNISHMENT;
            if (type.contains("TAX") || type.contains("LEVY")) return OrderType.TAX_LEVY;
            if (type.contains("CHILD_SUPPORT")) return OrderType.CHILD_SUPPORT;
            if (type.contains("CRIMINAL")) return OrderType.CRIMINAL_FORFEITURE;
            if (type.contains("REGULATORY") || type.contains("SEC") || type.contains("FINRA")) {
                return OrderType.REGULATORY_FREEZE;
            }
        }
        return OrderType.COURT_ORDER;
    }

    private String extractIssuingAuthority(AssetFreeze freeze) {
        return freeze.getIssuingAuthority() != null ?
            freeze.getIssuingAuthority() : "Unknown Authority";
    }

    private String extractJurisdiction(AssetFreeze freeze) {
        return freeze.getJurisdiction() != null ?
            freeze.getJurisdiction() : "Unknown Jurisdiction";
    }
}
