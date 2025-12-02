package com.waqiti.crypto.kafka;

import com.waqiti.crypto.events.model.CryptoEvent;
import com.waqiti.crypto.service.CryptoWalletService;
import com.waqiti.crypto.service.CryptoTransactionService;
import com.waqiti.crypto.compliance.ComplianceService;
import com.waqiti.crypto.security.HDWalletGenerator;
import com.waqiti.crypto.security.CryptoSecurityService;
import com.waqiti.crypto.repository.CryptoWalletRepository;
import com.waqiti.crypto.entity.CryptoWallet;
import com.waqiti.crypto.entity.WalletStatus;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class EnhancedCryptoWalletCreatedConsumer {

    private final CryptoWalletService cryptoWalletService;
    private final CryptoTransactionService cryptoTransactionService;
    private final CryptoWalletRepository walletRepository;
    private final ComplianceService complianceService;
    private final HDWalletGenerator hdWalletGenerator;
    private final CryptoSecurityService cryptoSecurityService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("crypto_wallet_created_processed_total")
            .description("Total number of successfully processed crypto wallet created events")
            .register(meterRegistry);
        errorCounter = Counter.builder("crypto_wallet_created_errors_total")
            .description("Total number of crypto wallet created processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("crypto_wallet_created_processing_duration")
            .description("Time taken to process crypto wallet created events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"crypto-wallet-created", "crypto-wallet-generation", "crypto-wallet-provisioning"},
        groupId = "enhanced-crypto-wallet-created-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "crypto-wallet-created", fallbackMethod = "handleCryptoWalletCreatedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCryptoWalletCreatedEvent(
            @Payload CryptoEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("wallet-%s-p%d-o%d", event.getCryptoWalletId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getCryptoWalletId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing crypto wallet created: walletId={}, userId={}, currency={}",
                event.getCryptoWalletId(), event.getUserId(), event.getCryptoSymbol());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case "WALLET_CREATION_REQUESTED":
                    processWalletCreationRequested(event, correlationId);
                    break;

                case "WALLET_HD_GENERATED":
                    processWalletHdGenerated(event, correlationId);
                    break;

                case "WALLET_ADDRESSES_GENERATED":
                    processWalletAddressesGenerated(event, correlationId);
                    break;

                case "WALLET_SECURITY_CONFIGURED":
                    processWalletSecurityConfigured(event, correlationId);
                    break;

                case "WALLET_COMPLIANCE_INITIATED":
                    processWalletComplianceInitiated(event, correlationId);
                    break;

                case "WALLET_PROVISIONING_COMPLETED":
                    processWalletProvisioningCompleted(event, correlationId);
                    break;

                case "WALLET_ACTIVATION_READY":
                    processWalletActivationReady(event, correlationId);
                    break;

                case "WALLET_CREATED_SUCCESSFULLY":
                    processWalletCreatedSuccessfully(event, correlationId);
                    break;

                default:
                    log.warn("Unknown crypto wallet created event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("CRYPTO_WALLET_CREATED_EVENT_PROCESSED", event.getUserId(),
                Map.of("eventType", event.getEventType(), "walletId", event.getCryptoWalletId(),
                    "currency", event.getCryptoSymbol(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process crypto wallet created event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("crypto-wallet-created-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCryptoWalletCreatedEventFallback(
            CryptoEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("wallet-fallback-%s-p%d-o%d", event.getCryptoWalletId(), partition, offset);

        log.error("Circuit breaker fallback triggered for crypto wallet created: walletId={}, error={}",
            event.getCryptoWalletId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("crypto-wallet-created-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Crypto Wallet Creation Circuit Breaker Triggered",
                String.format("Wallet creation failed for user %s: %s", event.getUserId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCryptoWalletCreatedEvent(
            @Payload CryptoEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-wallet-%s-%d", event.getCryptoWalletId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Crypto wallet creation permanently failed: walletId={}, topic={}, error={}",
            event.getCryptoWalletId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("CRYPTO_WALLET_CREATED_DLT_EVENT", event.getUserId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "walletId", event.getCryptoWalletId(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Crypto Wallet Creation Dead Letter Event",
                String.format("Wallet creation for user %s sent to DLT: %s", event.getUserId(), exceptionMessage),
                Map.of("userId", event.getUserId(), "walletId", event.getCryptoWalletId(),
                    "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processWalletCreationRequested(CryptoEvent event, String correlationId) {
        log.info("Processing wallet creation requested: userId={}, currency={}", event.getUserId(), event.getCryptoSymbol());

        // Validate user eligibility for crypto wallet
        if (!cryptoWalletService.validateUserEligibility(event.getUserId())) {
            throw new RuntimeException("User not eligible for crypto wallet");
        }

        // Check if wallet already exists for this currency
        if (cryptoWalletService.walletExists(event.getUserId(), event.getCryptoSymbol())) {
            log.warn("Wallet already exists for user {} and currency {}", event.getUserId(), event.getCryptoSymbol());
            return;
        }

        // Create initial wallet record
        CryptoWallet wallet = CryptoWallet.builder()
            .walletId(event.getCryptoWalletId())
            .userId(event.getUserId())
            .cryptoSymbol(event.getCryptoSymbol())
            .cryptoName(event.getCryptoName())
            .status(WalletStatus.PROVISIONING)
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        walletRepository.save(wallet);

        // Trigger HD wallet generation
        kafkaTemplate.send("crypto-wallet-generation-workflow", Map.of(
            "walletId", event.getCryptoWalletId(),
            "userId", event.getUserId(),
            "cryptoSymbol", event.getCryptoSymbol(),
            "eventType", "HD_GENERATION_REQUESTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Wallet creation initiated: walletId={}", event.getCryptoWalletId());
    }

    private void processWalletHdGenerated(CryptoEvent event, String correlationId) {
        log.info("Processing wallet HD generated: walletId={}", event.getCryptoWalletId());

        // Generate HD wallet seed and master key
        var hdWalletData = hdWalletGenerator.generateHDWallet(event.getCryptoSymbol(), correlationId);

        // Store encrypted wallet data
        cryptoWalletService.storeHdWalletData(event.getCryptoWalletId(), hdWalletData, correlationId);

        // Update wallet status
        cryptoWalletService.updateWalletStatus(event.getCryptoWalletId(), WalletStatus.HD_GENERATED, correlationId);

        // Trigger address generation
        kafkaTemplate.send("crypto-wallet-generation-workflow", Map.of(
            "walletId", event.getCryptoWalletId(),
            "eventType", "ADDRESS_GENERATION_REQUESTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("HD wallet generated successfully: walletId={}", event.getCryptoWalletId());
    }

    private void processWalletAddressesGenerated(CryptoEvent event, String correlationId) {
        log.info("Processing wallet addresses generated: walletId={}", event.getCryptoWalletId());

        // Generate initial set of addresses (receiving, change, etc.)
        var addresses = cryptoWalletService.generateInitialAddresses(event.getCryptoWalletId(), event.getCryptoSymbol(), correlationId);

        // Store addresses with proper indexing
        cryptoWalletService.storeWalletAddresses(event.getCryptoWalletId(), addresses, correlationId);

        // Update wallet status
        cryptoWalletService.updateWalletStatus(event.getCryptoWalletId(), WalletStatus.ADDRESSES_GENERATED, correlationId);

        // Trigger security configuration
        kafkaTemplate.send("crypto-wallet-generation-workflow", Map.of(
            "walletId", event.getCryptoWalletId(),
            "eventType", "SECURITY_CONFIGURATION_REQUESTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Wallet addresses generated: walletId={}, addressCount={}", event.getCryptoWalletId(), addresses.size());
    }

    private void processWalletSecurityConfigured(CryptoEvent event, String correlationId) {
        log.info("Processing wallet security configured: walletId={}", event.getCryptoWalletId());

        // Configure security settings (multi-sig, threshold, etc.)
        cryptoSecurityService.configureWalletSecurity(event.getCryptoWalletId(), event.getUserId(), correlationId);

        // Set up monitoring and alerting
        cryptoSecurityService.enableSecurityMonitoring(event.getCryptoWalletId(), correlationId);

        // Update wallet status
        cryptoWalletService.updateWalletStatus(event.getCryptoWalletId(), WalletStatus.SECURITY_CONFIGURED, correlationId);

        // Trigger compliance checks
        kafkaTemplate.send("crypto-compliance-workflow", Map.of(
            "walletId", event.getCryptoWalletId(),
            "userId", event.getUserId(),
            "eventType", "WALLET_COMPLIANCE_CHECK_REQUESTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Wallet security configured: walletId={}", event.getCryptoWalletId());
    }

    private void processWalletComplianceInitiated(CryptoEvent event, String correlationId) {
        log.info("Processing wallet compliance initiated: walletId={}", event.getCryptoWalletId());

        // Start KYC verification if needed
        if (!complianceService.isKycComplete(event.getUserId())) {
            complianceService.initiateKycVerification(event.getUserId(), "CRYPTO_WALLET_CREATION", correlationId);
        }

        // Perform AML screening
        complianceService.performAmlScreening(event.getUserId(), event.getCryptoWalletId(), correlationId);

        // Check sanctions lists
        complianceService.performSanctionsScreening(event.getUserId(), correlationId);

        // Update wallet status
        cryptoWalletService.updateWalletStatus(event.getCryptoWalletId(), WalletStatus.COMPLIANCE_PENDING, correlationId);

        // Schedule compliance timeout check
        kafkaTemplate.send("crypto-compliance-scheduler", Map.of(
            "walletId", event.getCryptoWalletId(),
            "eventType", "COMPLIANCE_TIMEOUT_CHECK",
            "scheduledTime", Instant.now().plusSeconds(259200), // 72 hour timeout
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Wallet compliance checks initiated: walletId={}", event.getCryptoWalletId());
    }

    private void processWalletProvisioningCompleted(CryptoEvent event, String correlationId) {
        log.info("Processing wallet provisioning completed: walletId={}", event.getCryptoWalletId());

        // Verify all provisioning steps are complete
        if (!cryptoWalletService.verifyProvisioningComplete(event.getCryptoWalletId())) {
            throw new RuntimeException("Wallet provisioning verification failed");
        }

        // Update wallet status
        cryptoWalletService.updateWalletStatus(event.getCryptoWalletId(), WalletStatus.PROVISIONED, correlationId);

        // Initialize zero balances for supported currencies
        cryptoWalletService.initializeWalletBalances(event.getCryptoWalletId(), correlationId);

        // Trigger activation readiness check
        kafkaTemplate.send("crypto-wallet-activation-workflow", Map.of(
            "walletId", event.getCryptoWalletId(),
            "eventType", "ACTIVATION_READINESS_CHECK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Wallet provisioning completed: walletId={}", event.getCryptoWalletId());
    }

    private void processWalletActivationReady(CryptoEvent event, String correlationId) {
        log.info("Processing wallet activation ready: walletId={}", event.getCryptoWalletId());

        // Perform final activation checks
        if (!cryptoWalletService.canActivateWallet(event.getCryptoWalletId())) {
            log.warn("Wallet not ready for activation: walletId={}", event.getCryptoWalletId());
            return;
        }

        // Check compliance status
        if (!complianceService.isWalletCompliant(event.getCryptoWalletId())) {
            log.info("Wallet pending compliance clearance: walletId={}", event.getCryptoWalletId());
            return;
        }

        // Activate the wallet
        cryptoWalletService.activateWallet(event.getCryptoWalletId(), correlationId);

        // Update status to active
        cryptoWalletService.updateWalletStatus(event.getCryptoWalletId(), WalletStatus.ACTIVE, correlationId);

        // Trigger successful creation workflow
        kafkaTemplate.send("crypto-wallet-lifecycle", Map.of(
            "walletId", event.getCryptoWalletId(),
            "userId", event.getUserId(),
            "cryptoSymbol", event.getCryptoSymbol(),
            "eventType", "WALLET_CREATION_SUCCESS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Wallet activation ready and activated: walletId={}", event.getCryptoWalletId());
    }

    private void processWalletCreatedSuccessfully(CryptoEvent event, String correlationId) {
        log.info("Processing wallet created successfully: walletId={}", event.getCryptoWalletId());

        // Final verification of wallet creation
        var wallet = walletRepository.findById(event.getCryptoWalletId())
            .orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (!WalletStatus.ACTIVE.equals(wallet.getStatus())) {
            throw new RuntimeException("Wallet is not in active status");
        }

        // Update completion timestamp
        wallet.setActivatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        // Send welcome notification to user
        notificationService.sendNotification(event.getUserId(),
            "Crypto Wallet Created Successfully",
            String.format("Your %s (%s) wallet has been created and is ready to use. Start trading now!",
                event.getCryptoName(), event.getCryptoSymbol()),
            correlationId);

        // Enable trading capabilities
        cryptoWalletService.enableTradingFeatures(event.getCryptoWalletId(), correlationId);

        // Send analytics event
        kafkaTemplate.send("crypto-analytics-events", Map.of(
            "walletId", event.getCryptoWalletId(),
            "userId", event.getUserId(),
            "cryptoSymbol", event.getCryptoSymbol(),
            "eventType", "WALLET_CREATION_COMPLETED",
            "creationDuration", calculateCreationDuration(wallet.getCreatedAt()),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Trigger onboarding workflow
        kafkaTemplate.send("crypto-onboarding-events", Map.of(
            "userId", event.getUserId(),
            "walletId", event.getCryptoWalletId(),
            "cryptoSymbol", event.getCryptoSymbol(),
            "eventType", "CRYPTO_ONBOARDING_WALLET_READY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Wallet creation completed successfully: walletId={}, duration={}ms",
            event.getCryptoWalletId(), calculateCreationDuration(wallet.getCreatedAt()));
    }

    private long calculateCreationDuration(LocalDateTime createdAt) {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toMillis();
    }
}