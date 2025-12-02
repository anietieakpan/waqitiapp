package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.crypto.service.CryptoWalletService;
import com.waqiti.crypto.service.CryptoSecurityService;
import com.waqiti.crypto.service.CryptoComplianceService;
import com.waqiti.crypto.service.CryptoNotificationService;
import com.waqiti.common.exception.CryptoProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for Crypto Wallet Events
 * Handles wallet creation, balance updates, security events, and compliance monitoring
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CryptoWalletEventsConsumer {
    
    private final CryptoWalletService walletService;
    private final CryptoSecurityService securityService;
    private final CryptoComplianceService complianceService;
    private final CryptoNotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"crypto-wallet-events", "wallet-created", "wallet-balance-updated", "wallet-security-alert"},
        groupId = "crypto-service-wallet-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000)
    )
    @Transactional
    public void handleCryptoWalletEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID walletId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            walletId = UUID.fromString((String) event.get("walletId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String walletType = (String) event.get("walletType"); // HOT, COLD, MULTI_SIG, HD_WALLET
            String cryptoCurrency = (String) event.get("cryptoCurrency"); // BTC, ETH, LTC, etc.
            String walletAddress = (String) event.get("walletAddress");
            String walletStatus = (String) event.get("walletStatus"); // ACTIVE, SUSPENDED, FROZEN, CLOSED
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            // Balance information
            BigDecimal currentBalance = event.containsKey("currentBalance") ?
                    new BigDecimal((String) event.get("currentBalance")) : BigDecimal.ZERO;
            BigDecimal previousBalance = event.containsKey("previousBalance") ?
                    new BigDecimal((String) event.get("previousBalance")) : BigDecimal.ZERO;
            BigDecimal balanceChange = currentBalance.subtract(previousBalance);
            
            // Security information
            String securityLevel = (String) event.get("securityLevel"); // STANDARD, ENHANCED, MAXIMUM
            Boolean multiSigEnabled = (Boolean) event.getOrDefault("multiSigEnabled", false);
            Integer requiredSignatures = (Integer) event.getOrDefault("requiredSignatures", 1);
            String encryptionStatus = (String) event.get("encryptionStatus");
            Boolean hardwareWalletLinked = (Boolean) event.getOrDefault("hardwareWalletLinked", false);
            
            // Transaction context
            String transactionId = (String) event.get("transactionId");
            String transactionType = (String) event.get("transactionType"); // DEPOSIT, WITHDRAWAL, TRANSFER
            String sourceAddress = (String) event.get("sourceAddress");
            String destinationAddress = (String) event.get("destinationAddress");
            
            // Security alert information
            String alertType = (String) event.get("alertType"); // UNAUTHORIZED_ACCESS, SUSPICIOUS_TRANSACTION, ADDRESS_REUSE
            String alertSeverity = (String) event.get("alertSeverity"); // LOW, MEDIUM, HIGH, CRITICAL
            String alertDescription = (String) event.get("alertDescription");
            
            log.info("Processing crypto wallet event - WalletId: {}, CustomerId: {}, Type: {}, Currency: {}", 
                    walletId, customerId, eventType, cryptoCurrency);
            
            // Step 1: Validate wallet event data
            Map<String, Object> validationResult = walletService.validateWalletEventData(
                    walletId, customerId, walletType, cryptoCurrency, walletAddress, timestamp);
            
            if ("INVALID".equals(validationResult.get("status"))) {
                walletService.logInvalidWalletEvent(walletId, 
                        (String) validationResult.get("reason"), timestamp);
                log.warn("Invalid wallet event data: {}", validationResult.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Address compliance screening
            Map<String, Object> addressScreening = complianceService.screenCryptoAddress(
                    walletAddress, sourceAddress, destinationAddress, cryptoCurrency, timestamp);
            
            if ("SANCTIONED".equals(addressScreening.get("status"))) {
                walletService.freezeWallet(walletId, "Sanctioned address detected", timestamp);
                log.warn("Wallet frozen due to sanctioned address interaction");
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 3: Security validation
            Map<String, Object> securityValidation = securityService.validateWalletSecurity(
                    walletId, walletType, securityLevel, multiSigEnabled, 
                    encryptionStatus, hardwareWalletLinked, timestamp);
            
            String securityRiskLevel = (String) securityValidation.get("riskLevel");
            
            // Step 4: Process based on event type
            switch (eventType) {
                case "WALLET_CREATED":
                    walletService.processWalletCreation(walletId, customerId, walletType,
                            cryptoCurrency, walletAddress, securityLevel, multiSigEnabled,
                            requiredSignatures, hardwareWalletLinked, timestamp);
                    break;
                    
                case "WALLET_BALANCE_UPDATED":
                    walletService.processBalanceUpdate(walletId, customerId, cryptoCurrency,
                            previousBalance, currentBalance, balanceChange, transactionId,
                            transactionType, sourceAddress, destinationAddress, timestamp);
                    break;
                    
                case "WALLET_SECURITY_ALERT":
                    walletService.processSecurityAlert(walletId, customerId, alertType,
                            alertSeverity, alertDescription, walletAddress, timestamp);
                    break;
                    
                case "WALLET_STATUS_CHANGED":
                    walletService.processStatusChange(walletId, customerId, walletStatus,
                            (String) event.get("previousStatus"), 
                            (String) event.get("statusChangeReason"), timestamp);
                    break;
                    
                default:
                    walletService.processGenericWalletEvent(walletId, eventType, event, timestamp);
            }
            
            // Step 5: Balance monitoring and thresholds
            if (balanceChange.compareTo(BigDecimal.ZERO) != 0) {
                walletService.checkBalanceThresholds(walletId, customerId, cryptoCurrency,
                        currentBalance, balanceChange, timestamp);
                
                // Large transaction monitoring
                BigDecimal transactionThreshold = walletService.getLargeTransactionThreshold(cryptoCurrency);
                if (balanceChange.abs().compareTo(transactionThreshold) >= 0) {
                    complianceService.flagLargeTransaction(walletId, transactionId,
                            balanceChange, cryptoCurrency, timestamp);
                }
            }
            
            // Step 6: Address reuse detection
            if (transactionType != null) {
                Map<String, Object> addressAnalysis = securityService.analyzeAddressUsage(
                        walletAddress, sourceAddress, destinationAddress, cryptoCurrency, timestamp);
                
                Boolean addressReuseDetected = (Boolean) addressAnalysis.get("addressReuseDetected");
                if (addressReuseDetected) {
                    securityService.handleAddressReuse(walletId, walletAddress, timestamp);
                }
            }
            
            // Step 7: Multi-signature workflow validation
            if (multiSigEnabled && transactionId != null) {
                walletService.validateMultiSigWorkflow(walletId, transactionId,
                        requiredSignatures, timestamp);
            }
            
            // Step 8: Hot wallet security monitoring
            if ("HOT".equals(walletType)) {
                securityService.monitorHotWalletSecurity(walletId, currentBalance,
                        cryptoCurrency, securityLevel, timestamp);
            }
            
            // Step 9: Cold storage compliance
            if ("COLD".equals(walletType)) {
                complianceService.validateColdStorageCompliance(walletId, currentBalance,
                        cryptoCurrency, encryptionStatus, timestamp);
            }
            
            // Step 10: Regulatory reporting
            complianceService.updateCryptoReporting(walletId, customerId, cryptoCurrency,
                    currentBalance, balanceChange, transactionType, addressScreening, timestamp);
            
            // Step 11: Send wallet notifications
            notificationService.sendWalletNotification(walletId, customerId, eventType,
                    walletType, cryptoCurrency, currentBalance, balanceChange,
                    alertType, alertSeverity, timestamp);
            
            // Step 12: Update wallet analytics
            walletService.updateWalletAnalytics(walletId, customerId, cryptoCurrency,
                    walletType, currentBalance, balanceChange, transactionType, timestamp);
            
            // Step 13: Audit logging
            auditService.auditFinancialEvent(
                    "CRYPTO_WALLET_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Crypto wallet event processed - Type: %s, Currency: %s, Balance: %s", 
                            eventType, cryptoCurrency, currentBalance),
                    Map.of(
                            "walletId", walletId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "walletType", walletType,
                            "cryptoCurrency", cryptoCurrency,
                            "walletAddress", walletAddress.substring(0, 6) + "..." + 
                                           walletAddress.substring(walletAddress.length() - 6), // Truncated for security
                            "currentBalance", currentBalance.toString(),
                            "balanceChange", balanceChange.toString(),
                            "walletStatus", walletStatus,
                            "securityLevel", securityLevel,
                            "multiSigEnabled", multiSigEnabled.toString(),
                            "hardwareWalletLinked", hardwareWalletLinked.toString(),
                            "securityRiskLevel", securityRiskLevel,
                            "addressScreeningStatus", addressScreening.get("status").toString(),
                            "alertType", alertType != null ? alertType : "N/A",
                            "alertSeverity", alertSeverity != null ? alertSeverity : "N/A"
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed crypto wallet event - WalletId: {}, EventType: {}, Balance: {} {}", 
                    walletId, eventType, currentBalance, cryptoCurrency);
            
        } catch (Exception e) {
            log.error("Crypto wallet event processing failed - WalletId: {}, CustomerId: {}, Error: {}", 
                    walletId, customerId, e.getMessage(), e);
            throw new CryptoProcessingException("Crypto wallet event processing failed", e);
        }
    }
}