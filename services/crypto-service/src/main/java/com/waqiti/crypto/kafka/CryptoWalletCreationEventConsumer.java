package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.crypto.service.CryptoWalletService;
import com.waqiti.crypto.compliance.ComplianceService;
import com.waqiti.crypto.security.HDWalletGenerator;
import com.waqiti.crypto.entity.CryptoWallet;
import com.waqiti.crypto.entity.CryptoCurrency;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #266: Crypto Wallet Creation Event Consumer
 * Processes HD wallet generation and key management with regulatory compliance
 * Implements 12-step zero-tolerance processing for crypto wallet creation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoWalletCreationEventConsumer extends BaseKafkaConsumer {

    private final CryptoWalletService cryptoWalletService;
    private final ComplianceService complianceService;
    private final HDWalletGenerator hdWalletGenerator;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "crypto-wallet-creation-events", groupId = "crypto-wallet-creation-group")
    @CircuitBreaker(name = "crypto-wallet-creation-consumer")
    @Retry(name = "crypto-wallet-creation-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCryptoWalletCreationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "crypto-wallet-creation-event");
        
        try {
            log.info("Step 1: Processing crypto wallet creation event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String userId = eventData.path("userId").asText();
            String cryptoCurrency = eventData.path("cryptoCurrency").asText();
            String walletType = eventData.path("walletType").asText();
            boolean multiSigRequired = eventData.path("multiSigRequired").asBoolean(false);
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted crypto wallet details: userId={}, currency={}, type={}", 
                    userId, cryptoCurrency, walletType);
            
            // Step 3: Crypto wallet eligibility validation
            boolean eligible = cryptoWalletService.validateWalletEligibility(userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), timestamp);
            if (!eligible) {
                log.error("Step 3: Crypto wallet eligibility failed for userId={}", userId);
                cryptoWalletService.rejectWalletCreation(eventId, "NOT_ELIGIBLE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Crypto wallet eligibility validated");
            
            // Step 4: OFAC crypto address screening
            boolean ofacCleared = complianceService.performCryptoOFACScreening(userId, 
                    cryptoCurrency, timestamp);
            if (!ofacCleared) {
                log.error("Step 4: OFAC crypto screening failed");
                cryptoWalletService.blockWalletCreation(eventId, "OFAC_SANCTIONS", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: OFAC crypto screening passed");
            
            // Step 5: HD wallet generation with entropy
            String seedPhrase = hdWalletGenerator.generateSecureSeedPhrase();
            String masterPrivateKey = hdWalletGenerator.deriveMasterKey(seedPhrase);
            String publicKey = hdWalletGenerator.derivePublicKey(masterPrivateKey, 0);
            log.info("Step 5: HD wallet keys generated securely");
            
            // Step 6: Multi-signature setup if required
            if (multiSigRequired) {
                String multiSigAddress = cryptoWalletService.createMultiSigWallet(userId, 
                        CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), publicKey, timestamp);
                log.info("Step 6: Multi-signature wallet created: {}", multiSigAddress);
            } else {
                log.info("Step 6: Single signature wallet (multi-sig not required)");
            }
            
            // Step 7: Wallet creation and blockchain registration
            CryptoWallet wallet = cryptoWalletService.createCryptoWallet(eventId, userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), masterPrivateKey, 
                    publicKey, multiSigRequired, timestamp);
            log.info("Step 7: Crypto wallet created: walletId={}, address={}", 
                    wallet.getId(), wallet.getAddress());
            
            // Step 8: AML risk assessment for crypto
            int amlRiskScore = complianceService.calculateCryptoAMLRisk(userId, cryptoCurrency, 
                    wallet.getAddress(), timestamp);
            if (amlRiskScore > 800) {
                cryptoWalletService.flagHighRiskWallet(wallet.getId(), amlRiskScore, timestamp);
                log.warn("Step 8: High AML risk detected: score={}", amlRiskScore);
            } else {
                log.info("Step 8: AML risk assessment passed: score={}", amlRiskScore);
            }
            
            // Step 9: Blockchain address validation
            boolean addressValid = cryptoWalletService.validateBlockchainAddress(wallet.getAddress(), 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), timestamp);
            if (!addressValid) {
                log.error("Step 9: Blockchain address validation failed");
                cryptoWalletService.invalidateWallet(wallet.getId(), timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 9: Blockchain address validated");
            
            // Step 10: KYC level verification for crypto
            boolean kycSufficient = complianceService.validateCryptoKYCLevel(userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), timestamp);
            if (!kycSufficient) {
                cryptoWalletService.limitWalletFunctionality(wallet.getId(), "KYC_INSUFFICIENT", timestamp);
                log.warn("Step 10: KYC insufficient - wallet functionality limited");
            } else {
                log.info("Step 10: KYC level sufficient for crypto operations");
            }
            
            // Step 11: Security backup and recovery setup
            cryptoWalletService.setupSecurityBackup(wallet.getId(), seedPhrase, timestamp);
            log.info("Step 11: Security backup configured");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed crypto wallet creation: eventId={}, walletId={}", 
                    eventId, wallet.getId());
            
        } catch (Exception e) {
            log.error("Error processing crypto wallet creation event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("userId") || !eventData.has("cryptoCurrency")) {
            throw new IllegalArgumentException("Invalid crypto wallet creation event structure");
        }
    }
}