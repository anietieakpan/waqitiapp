package com.waqiti.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL P0 FIX: Wallet Freeze Service
 *
 * Service for integrating with wallet-service to freeze/unfreeze wallets
 * for legal compliance purposes.
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-05
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletFreezeService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;

    private static final String WALLET_FREEZE_TOPIC = "wallet.freeze.requests";
    private static final String WALLET_UNFREEZE_TOPIC = "wallet.unfreeze.requests";

    /**
     * Freeze wallet for legal order
     *
     * @param walletId Wallet ID to freeze
     * @param userId User ID
     * @param amount Amount to freeze (null = entire balance)
     * @param orderNumber Legal order number
     * @param issuingAuthority Issuing authority
     * @param reason Freeze reason
     * @return Freeze ID
     */
    public UUID freezeWalletForLegalOrder(
        UUID walletId,
        UUID userId,
        BigDecimal amount,
        String orderNumber,
        String issuingAuthority,
        String reason
    ) {
        log.warn("LEGAL: Freezing wallet for legal order - Wallet: {}, User: {}, Order: {}, Authority: {}",
            walletId, userId, orderNumber, issuingAuthority);

        try {
            UUID freezeId = UUID.randomUUID();

            Map<String, Object> freezeRequest = new HashMap<>();
            freezeRequest.put("freezeId", freezeId.toString());
            freezeRequest.put("walletId", walletId.toString());
            freezeRequest.put("userId", userId.toString());
            freezeRequest.put("amount", amount);
            freezeRequest.put("freezeType", "LEGAL_ORDER");
            freezeRequest.put("orderNumber", orderNumber);
            freezeRequest.put("issuingAuthority", issuingAuthority);
            freezeRequest.put("reason", reason);
            freezeRequest.put("timestamp", System.currentTimeMillis());
            freezeRequest.put("correlationId", UUID.randomUUID().toString());

            // Publish freeze request to wallet-service via Kafka
            kafkaTemplate.send(WALLET_FREEZE_TOPIC, walletId.toString(), freezeRequest);

            log.info("LEGAL: Wallet freeze request sent - Wallet: {}, Freeze ID: {}",
                walletId, freezeId);

            return freezeId;

        } catch (Exception e) {
            log.error("LEGAL: Failed to freeze wallet - Wallet: {}, Order: {}",
                walletId, orderNumber, e);
            throw new RuntimeException("Failed to freeze wallet for legal order", e);
        }
    }

    /**
     * Unfreeze wallet (release legal order)
     *
     * @param freezeId Freeze ID to release
     * @param releasedBy User releasing freeze
     * @param reason Release reason
     */
    public void unfreezeWallet(UUID freezeId, String releasedBy, String reason) {
        log.info("LEGAL: Unfreezing wallet - Freeze ID: {}, Released by: {}, Reason: {}",
            freezeId, releasedBy, reason);

        try {
            Map<String, Object> unfreezeRequest = new HashMap<>();
            unfreezeRequest.put("freezeId", freezeId.toString());
            unfreezeRequest.put("releasedBy", releasedBy);
            unfreezeRequest.put("reason", reason);
            unfreezeRequest.put("timestamp", System.currentTimeMillis());
            unfreezeRequest.put("correlationId", UUID.randomUUID().toString());

            // Publish unfreeze request to wallet-service via Kafka
            kafkaTemplate.send(WALLET_UNFREEZE_TOPIC, freezeId.toString(), unfreezeRequest);

            log.info("LEGAL: Wallet unfreeze request sent - Freeze ID: {}", freezeId);

        } catch (Exception e) {
            log.error("LEGAL: Failed to unfreeze wallet - Freeze ID: {}", freezeId, e);
            throw new RuntimeException("Failed to unfreeze wallet", e);
        }
    }
}
