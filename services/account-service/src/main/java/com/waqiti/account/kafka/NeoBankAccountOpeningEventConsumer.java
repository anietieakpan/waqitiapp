package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.model.*;
import com.waqiti.account.service.*;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.service.DlqService;
import com.waqiti.common.utils.MDCUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.math.BigDecimal;

@Component
public class NeoBankAccountOpeningEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NeoBankAccountOpeningEventConsumer.class);
    
    private static final String TOPIC = "waqiti.account.neo-bank-account-opening";
    private static final String CONSUMER_GROUP = "neo-bank-account-opening-consumer-group";
    private static final String DLQ_TOPIC = "waqiti.account.neo-bank-account-opening.dlq";
    
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final DlqService dlqService;
    private final MeterRegistry meterRegistry;
    private final NeoBankAccountService accountService;
    private final DigitalOnboardingService onboardingService;
    private final InstantKycService kycService;
    private final VirtualCardService cardService;
    private final NeoBankComplianceService complianceService;
    
    private Counter messagesProcessedCounter;
    private Counter accountsOpenedCounter;
    private Counter kycCompletedCounter;
    private Counter cardsIssuedCounter;
    private Timer messageProcessingTimer;
    
    private final ConcurrentHashMap<String, NeoBankAccountApplication> applications = new ConcurrentHashMap<>();
    
    public NeoBankAccountOpeningEventConsumer(
            ObjectMapper objectMapper,
            MetricsService metricsService,
            DlqService dlqService,
            MeterRegistry meterRegistry,
            NeoBankAccountService accountService,
            DigitalOnboardingService onboardingService,
            InstantKycService kycService,
            VirtualCardService cardService,
            NeoBankComplianceService complianceService) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.dlqService = dlqService;
        this.meterRegistry = meterRegistry;
        this.accountService = accountService;
        this.onboardingService = onboardingService;
        this.kycService = kycService;
        this.cardService = cardService;
        this.complianceService = complianceService;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        messagesProcessedCounter = Counter.builder("neo_bank_account_messages_processed_total")
            .register(meterRegistry);
        accountsOpenedCounter = Counter.builder("neo_bank_accounts_opened_total")
            .register(meterRegistry);
        kycCompletedCounter = Counter.builder("neo_bank_kyc_completed_total")
            .register(meterRegistry);
        cardsIssuedCounter = Counter.builder("neo_bank_cards_issued_total")
            .register(meterRegistry);
        messageProcessingTimer = Timer.builder("neo_bank_account_message_processing_duration")
            .register(meterRegistry);
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processNeoBankAccountOpening(@Payload String message,
                                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                           @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                           @Header(KafkaHeaders.OFFSET) long offset,
                                           Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String requestId = UUID.randomUUID().toString();
        
        try {
            MDCUtil.setRequestId(requestId);
            
            JsonNode messageNode = objectMapper.readTree(message);
            String eventType = messageNode.path("eventType").asText();
            
            boolean processed = executeProcessingStep(eventType, messageNode, requestId);
            
            if (processed) {
                messagesProcessedCounter.increment();
                acknowledgment.acknowledge();
                logger.info("Successfully processed neo bank account message: eventType={}", eventType);
            } else {
                throw new RuntimeException("Failed to process message: " + eventType);
            }
            
        } catch (Exception e) {
            logger.error("Error processing neo bank account message", e);
            dlqService.sendToDlq(DLQ_TOPIC, message, e.getMessage(), requestId);
            acknowledgment.acknowledge();
        } finally {
            sample.stop(messageProcessingTimer);
        }
    }
    
    private boolean executeProcessingStep(String eventType, JsonNode messageNode, String requestId) {
        switch (eventType) {
            case "ACCOUNT_APPLICATION_SUBMITTED":
                return processAccountApplicationSubmitted(messageNode, requestId);
            case "INSTANT_KYC_VERIFICATION":
                return processInstantKycVerification(messageNode, requestId);
            case "DIGITAL_ONBOARDING_COMPLETED":
                return processDigitalOnboardingCompleted(messageNode, requestId);
            case "ACCOUNT_APPROVAL_DECISION":
                return processAccountApprovalDecision(messageNode, requestId);
            case "VIRTUAL_CARD_REQUESTED":
                return processVirtualCardRequested(messageNode, requestId);
            case "ACCOUNT_ACTIVATION":
                return processAccountActivation(messageNode, requestId);
            case "INITIAL_DEPOSIT_RECEIVED":
                return processInitialDepositReceived(messageNode, requestId);
            case "ACCOUNT_FEATURES_SETUP":
                return processAccountFeaturesSetup(messageNode, requestId);
            case "COMPLIANCE_VERIFICATION":
                return processComplianceVerification(messageNode, requestId);
            case "WELCOME_PACKAGE_DELIVERY":
                return processWelcomePackageDelivery(messageNode, requestId);
            default:
                logger.warn("Unknown event type: {}", eventType);
                return false;
        }
    }
    
    private boolean processAccountApplicationSubmitted(JsonNode messageNode, String requestId) {
        try {
            String applicantId = messageNode.path("applicantId").asText();
            String accountType = messageNode.path("accountType").asText();
            String customerEmail = messageNode.path("customerEmail").asText();
            String customerPhone = messageNode.path("customerPhone").asText();
            JsonNode personalInfo = messageNode.path("personalInfo");
            JsonNode preferredFeatures = messageNode.path("preferredFeatures");
            String referralCode = messageNode.path("referralCode").asText();
            
            NeoBankAccountApplication application = NeoBankAccountApplication.builder()
                .id(UUID.randomUUID().toString())
                .applicantId(applicantId)
                .accountType(accountType)
                .customerEmail(customerEmail)
                .customerPhone(customerPhone)
                .personalInfo(personalInfo.toString())
                .preferredFeatures(extractStringList(preferredFeatures))
                .referralCode(referralCode)
                .status("APPLICATION_SUBMITTED")
                .submittedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            applications.put(application.getId(), application);
            onboardingService.initiateOnboarding(application);
            
            logger.info("Processed account application: id={}, applicantId={}, accountType={}", 
                application.getId(), applicantId, accountType);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing account application", e);
            return false;
        }
    }
    
    private boolean processInstantKycVerification(JsonNode messageNode, String requestId) {
        try {
            String applicationId = messageNode.path("applicationId").asText();
            String documentType = messageNode.path("documentType").asText();
            String documentNumber = messageNode.path("documentNumber").asText();
            String biometricData = messageNode.path("biometricData").asText();
            String verificationMethod = messageNode.path("verificationMethod").asText();
            
            InstantKycVerification kycVerification = InstantKycVerification.builder()
                .id(UUID.randomUUID().toString())
                .applicationId(applicationId)
                .documentType(documentType)
                .documentNumber(documentNumber)
                .biometricData(biometricData)
                .verificationMethod(verificationMethod)
                .status("VERIFYING")
                .initiatedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            InstantKycResult result = kycService.performInstantVerification(kycVerification);
            
            NeoBankAccountApplication application = applications.get(applicationId);
            if (application != null) {
                application.setKycStatus(result.getStatus());
                application.setKycScore(result.getVerificationScore());
                application.setKycCompletedAt(LocalDateTime.now());
                
                if (result.isVerified()) {
                    application.setStatus("KYC_VERIFIED");
                    kycCompletedCounter.increment();
                } else {
                    application.setStatus("KYC_FAILED");
                }
                
                onboardingService.updateApplication(application);
            }
            
            logger.info("Processed instant KYC: applicationId={}, verified={}, score={}", 
                applicationId, result.isVerified(), result.getVerificationScore());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing instant KYC verification", e);
            return false;
        }
    }
    
    private boolean processDigitalOnboardingCompleted(JsonNode messageNode, String requestId) {
        try {
            String applicationId = messageNode.path("applicationId").asText();
            String onboardingScore = messageNode.path("onboardingScore").asText();
            JsonNode completedSteps = messageNode.path("completedSteps");
            String completionTime = messageNode.path("completionTime").asText();
            
            DigitalOnboardingCompletion completion = DigitalOnboardingCompletion.builder()
                .id(UUID.randomUUID().toString())
                .applicationId(applicationId)
                .onboardingScore(Double.parseDouble(onboardingScore))
                .completedSteps(extractStringList(completedSteps))
                .completionTime(Integer.parseInt(completionTime))
                .completedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            onboardingService.completeOnboarding(completion);
            
            NeoBankAccountApplication application = applications.get(applicationId);
            if (application != null) {
                application.setStatus("ONBOARDING_COMPLETED");
                application.setOnboardingScore(completion.getOnboardingScore());
                onboardingService.updateApplication(application);
            }
            
            logger.info("Completed digital onboarding: applicationId={}, score={}, steps={}", 
                applicationId, onboardingScore, completedSteps.size());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing digital onboarding completion", e);
            return false;
        }
    }
    
    private boolean processAccountApprovalDecision(JsonNode messageNode, String requestId) {
        try {
            String applicationId = messageNode.path("applicationId").asText();
            String decision = messageNode.path("decision").asText();
            String decisionReason = messageNode.path("decisionReason").asText();
            String approvedAccountType = messageNode.path("approvedAccountType").asText();
            JsonNode accountLimits = messageNode.path("accountLimits");
            
            AccountApprovalDecision approvalDecision = AccountApprovalDecision.builder()
                .id(UUID.randomUUID().toString())
                .applicationId(applicationId)
                .decision(decision)
                .decisionReason(decisionReason)
                .approvedAccountType(approvedAccountType)
                .accountLimits(accountLimits.toString())
                .decidedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            accountService.processApprovalDecision(approvalDecision);
            
            NeoBankAccountApplication application = applications.get(applicationId);
            if (application != null) {
                application.setStatus(decision);
                application.setDecisionReason(decisionReason);
                application.setDecidedAt(LocalDateTime.now());
                
                if ("APPROVED".equals(decision)) {
                    NeoBankAccount account = accountService.createNeoBankAccount(application, approvalDecision);
                    application.setAccountId(account.getId());
                    accountsOpenedCounter.increment();
                }
                
                onboardingService.updateApplication(application);
            }
            
            logger.info("Processed approval decision: applicationId={}, decision={}, accountType={}", 
                applicationId, decision, approvedAccountType);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing account approval decision", e);
            return false;
        }
    }
    
    private boolean processVirtualCardRequested(JsonNode messageNode, String requestId) {
        try {
            String accountId = messageNode.path("accountId").asText();
            String cardType = messageNode.path("cardType").asText();
            String cardDesign = messageNode.path("cardDesign").asText();
            JsonNode cardLimits = messageNode.path("cardLimits");
            String deliveryMethod = messageNode.path("deliveryMethod").asText();
            
            VirtualCardRequest cardRequest = VirtualCardRequest.builder()
                .id(UUID.randomUUID().toString())
                .accountId(accountId)
                .cardType(cardType)
                .cardDesign(cardDesign)
                .cardLimits(cardLimits.toString())
                .deliveryMethod(deliveryMethod)
                .status("REQUESTED")
                .requestedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            VirtualCard card = cardService.issueVirtualCard(cardRequest);
            
            if (card != null) {
                cardsIssuedCounter.increment();
                cardRequest.setStatus("ISSUED");
                cardRequest.setCardId(card.getId());
            } else {
                cardRequest.setStatus("FAILED");
            }
            
            cardService.updateCardRequest(cardRequest);
            
            logger.info("Processed virtual card request: accountId={}, cardType={}, issued={}", 
                accountId, cardType, card != null);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing virtual card request", e);
            return false;
        }
    }
    
    private boolean processAccountActivation(JsonNode messageNode, String requestId) {
        try {
            String accountId = messageNode.path("accountId").asText();
            String activationMethod = messageNode.path("activationMethod").asText();
            String activationCode = messageNode.path("activationCode").asText();
            String customerConfirmation = messageNode.path("customerConfirmation").asText();
            
            AccountActivation activation = AccountActivation.builder()
                .id(UUID.randomUUID().toString())
                .accountId(accountId)
                .activationMethod(activationMethod)
                .activationCode(activationCode)
                .customerConfirmation(customerConfirmation)
                .status("ACTIVATING")
                .initiatedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            boolean activated = accountService.activateAccount(activation);
            
            activation.setStatus(activated ? "ACTIVATED" : "FAILED");
            activation.setCompletedAt(LocalDateTime.now());
            
            accountService.updateActivation(activation);
            
            logger.info("Processed account activation: accountId={}, method={}, activated={}", 
                accountId, activationMethod, activated);
            
            return activated;
            
        } catch (Exception e) {
            logger.error("Error processing account activation", e);
            return false;
        }
    }
    
    private boolean processInitialDepositReceived(JsonNode messageNode, String requestId) {
        try {
            String accountId = messageNode.path("accountId").asText();
            String depositAmount = messageNode.path("depositAmount").asText();
            String depositMethod = messageNode.path("depositMethod").asText();
            String depositSource = messageNode.path("depositSource").asText();
            
            BigDecimal amount = new BigDecimal(depositAmount);
            
            InitialDeposit deposit = InitialDeposit.builder()
                .id(UUID.randomUUID().toString())
                .accountId(accountId)
                .depositAmount(amount)
                .depositMethod(depositMethod)
                .depositSource(depositSource)
                .status("PROCESSING")
                .receivedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            boolean processed = accountService.processInitialDeposit(deposit);
            
            deposit.setStatus(processed ? "COMPLETED" : "FAILED");
            deposit.setProcessedAt(LocalDateTime.now());
            
            accountService.updateDeposit(deposit);
            
            logger.info("Processed initial deposit: accountId={}, amount={}, method={}, processed={}", 
                accountId, amount, depositMethod, processed);
            
            return processed;
            
        } catch (Exception e) {
            logger.error("Error processing initial deposit", e);
            return false;
        }
    }
    
    private boolean processAccountFeaturesSetup(JsonNode messageNode, String requestId) {
        try {
            String accountId = messageNode.path("accountId").asText();
            JsonNode enabledFeatures = messageNode.path("enabledFeatures");
            JsonNode notificationPreferences = messageNode.path("notificationPreferences");
            JsonNode securitySettings = messageNode.path("securitySettings");
            
            AccountFeaturesSetup featuresSetup = AccountFeaturesSetup.builder()
                .id(UUID.randomUUID().toString())
                .accountId(accountId)
                .enabledFeatures(extractStringList(enabledFeatures))
                .notificationPreferences(notificationPreferences.toString())
                .securitySettings(securitySettings.toString())
                .status("CONFIGURING")
                .initiatedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            boolean configured = accountService.setupAccountFeatures(featuresSetup);
            
            featuresSetup.setStatus(configured ? "CONFIGURED" : "FAILED");
            featuresSetup.setCompletedAt(LocalDateTime.now());
            
            accountService.updateFeaturesSetup(featuresSetup);
            
            logger.info("Setup account features: accountId={}, featureCount={}, configured={}", 
                accountId, enabledFeatures.size(), configured);
            
            return configured;
            
        } catch (Exception e) {
            logger.error("Error setting up account features", e);
            return false;
        }
    }
    
    private boolean processComplianceVerification(JsonNode messageNode, String requestId) {
        try {
            String accountId = messageNode.path("accountId").asText();
            String applicantId = messageNode.path("applicantId").asText();
            JsonNode complianceChecks = messageNode.path("complianceChecks");
            
            NeoBankComplianceVerification verification = complianceService.performVerification(
                accountId, applicantId, complianceChecks);
            
            verification.setRequestId(requestId);
            complianceService.saveVerification(verification);
            
            logger.info("Processed compliance verification: accountId={}, compliant={}", 
                accountId, verification.isCompliant());
            
            return verification.isCompliant();
            
        } catch (Exception e) {
            logger.error("Error processing compliance verification", e);
            return false;
        }
    }
    
    private boolean processWelcomePackageDelivery(JsonNode messageNode, String requestId) {
        try {
            String accountId = messageNode.path("accountId").asText();
            String packageType = messageNode.path("packageType").asText();
            String deliveryMethod = messageNode.path("deliveryMethod").asText();
            JsonNode packageContents = messageNode.path("packageContents");
            
            WelcomePackage welcomePackage = WelcomePackage.builder()
                .id(UUID.randomUUID().toString())
                .accountId(accountId)
                .packageType(packageType)
                .deliveryMethod(deliveryMethod)
                .packageContents(extractStringList(packageContents))
                .status("DELIVERING")
                .initiatedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            boolean delivered = onboardingService.deliverWelcomePackage(welcomePackage);
            
            welcomePackage.setStatus(delivered ? "DELIVERED" : "FAILED");
            welcomePackage.setCompletedAt(LocalDateTime.now());
            
            onboardingService.updateWelcomePackage(welcomePackage);
            
            logger.info("Delivered welcome package: accountId={}, type={}, method={}, delivered={}", 
                accountId, packageType, deliveryMethod, delivered);
            
            return delivered;
            
        } catch (Exception e) {
            logger.error("Error processing welcome package delivery", e);
            return false;
        }
    }
    
    private List<String> extractStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(node -> list.add(node.asText()));
        }
        return list;
    }
}