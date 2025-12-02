package com.waqiti.crypto.consumer;

import com.waqiti.common.events.CryptocurrencyTransactionEvent;
import com.waqiti.crypto.service.BlockchainValidationService;
import com.waqiti.crypto.service.TravelRuleService;
import com.waqiti.crypto.service.AMLScreeningService;
import com.waqiti.crypto.service.TaxReportingService;
import com.waqiti.crypto.service.CustodyService;
import com.waqiti.crypto.service.ComplianceService;
import com.waqiti.crypto.service.NotificationService;
import com.waqiti.crypto.repository.ProcessedEventRepository;
import com.waqiti.crypto.repository.CryptoTransactionRepository;
import com.waqiti.crypto.model.ProcessedEvent;
import com.waqiti.crypto.model.CryptoTransaction;
import com.waqiti.crypto.model.TransactionStatus;
import com.waqiti.crypto.model.ComplianceLevel;
import com.waqiti.crypto.model.TravelRuleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Consumer for CryptocurrencyTransactionEvent - Critical for digital asset compliance
 * Handles blockchain validation, Travel Rule compliance, AML screening, and custody
 * ZERO TOLERANCE: All crypto transactions must comply with BSA, Travel Rule, and FinCEN guidance
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CryptocurrencyTransactionEventConsumer {
    
    private final BlockchainValidationService blockchainValidationService;
    private final TravelRuleService travelRuleService;
    private final AMLScreeningService amlScreeningService;
    private final TaxReportingService taxReportingService;
    private final CustodyService custodyService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final CryptoTransactionRepository cryptoTransactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal TRAVEL_RULE_THRESHOLD = new BigDecimal("3000");
    private static final BigDecimal LARGE_CRYPTO_THRESHOLD = new BigDecimal("10000");
    private static final Set<String> HIGH_RISK_CRYPTOCURRENCIES = Set.of(
        "XMR", "ZEC", "DASH", "BEAM", "GRIN" // Privacy coins
    );
    private static final Set<String> SANCTIONED_ADDRESSES = Set.of(
        // OFAC sanctioned crypto addresses would be loaded from external service
    );
    
    @KafkaListener(
        topics = "crypto.transaction.initiated",
        groupId = "crypto-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for crypto transactions
    public void handleCryptocurrencyTransaction(CryptocurrencyTransactionEvent event) {
        log.info("Processing crypto transaction: {} - Currency: {} - Amount: {} - Type: {} - From: {} - To: {}", 
            event.getTransactionId(), event.getCryptocurrency(), event.getAmount(), 
            event.getTransactionType(), maskAddress(event.getFromAddress()), maskAddress(event.getToAddress()));
        
        // IDEMPOTENCY CHECK - Prevent duplicate crypto transaction processing
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Crypto transaction already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // Create crypto transaction record
            CryptoTransaction transaction = createCryptoTransactionRecord(event);
            
            // STEP 1: Perform blockchain address validation and sanction screening
            performBlockchainAddressValidation(transaction, event);
            
            // STEP 2: Execute Travel Rule compliance for transfers >= $3000
            executeTravelRuleCompliance(transaction, event);
            
            // STEP 3: Conduct AML screening for high-risk patterns
            conductCryptoAMLScreening(transaction, event);
            
            // STEP 4: Validate custody and wallet security requirements
            validateCustodyAndWalletSecurity(transaction, event);
            
            // STEP 5: Perform blockchain transaction verification
            performBlockchainTransactionVerification(transaction, event);
            
            // STEP 6: Apply privacy coin and mixer analysis
            applyPrivacyCoinAndMixerAnalysis(transaction, event);
            
            // STEP 7: Execute cross-border crypto compliance
            executeCrossBorderCryptoCompliance(transaction, event);
            
            // STEP 8: Generate IRS Form 8300 for large crypto transactions
            generateIRSForm8300ForLargeCrypto(transaction, event);
            
            // STEP 9: Update crypto portfolio and tax basis
            updateCryptoPortfolioAndTaxBasis(transaction, event);
            
            // STEP 10: Monitor for suspicious trading patterns
            monitorSuspiciousTradingPatterns(transaction, event);
            
            // STEP 11: Send compliance notifications and alerts
            sendComplianceNotificationsAndAlerts(transaction, event);
            
            // STEP 12: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("CryptocurrencyTransactionEvent")
                .processedAt(Instant.now())
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .cryptocurrency(event.getCryptocurrency())
                .amount(event.getAmount())
                .usdValue(event.getUsdValue())
                .transactionType(event.getTransactionType())
                .transactionStatus(transaction.getStatus())
                .complianceLevel(transaction.getComplianceLevel())
                .travelRuleStatus(transaction.getTravelRuleStatus())
                .blockchainConfirmed(transaction.isBlockchainConfirmed())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed crypto transaction: {} - Status: {}, Compliance: {}, Travel Rule: {}", 
                event.getTransactionId(), transaction.getStatus(), transaction.getComplianceLevel(),
                transaction.getTravelRuleStatus());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process crypto transaction: {}", 
                event.getTransactionId(), e);
                
            // Create manual intervention record
            createManualInterventionRecord(event, e);
            
            throw new RuntimeException("Crypto transaction processing failed", e);
        }
    }
    
    private CryptoTransaction createCryptoTransactionRecord(CryptocurrencyTransactionEvent event) {
        CryptoTransaction transaction = CryptoTransaction.builder()
            .id(event.getTransactionId())
            .userId(event.getUserId())
            .walletId(event.getWalletId())
            .cryptocurrency(event.getCryptocurrency())
            .amount(event.getAmount())
            .usdValue(event.getUsdValue())
            .transactionType(event.getTransactionType())
            .fromAddress(event.getFromAddress())
            .toAddress(event.getToAddress())
            .blockchainNetwork(event.getBlockchainNetwork())
            .transactionHash(event.getTransactionHash())
            .status(TransactionStatus.PENDING_VALIDATION)
            .initiatedAt(LocalDateTime.now())
            .complianceFlags(new ArrayList<>())
            .riskFlags(new ArrayList<>())
            .build();
        
        return cryptoTransactionRepository.save(transaction);
    }
    
    private void performBlockchainAddressValidation(CryptoTransaction transaction, CryptocurrencyTransactionEvent event) {
        // Validate blockchain addresses for proper format and network
        boolean fromAddressValid = blockchainValidationService.validateAddress(
            event.getFromAddress(),
            event.getCryptocurrency(),
            event.getBlockchainNetwork()
        );
        
        boolean toAddressValid = blockchainValidationService.validateAddress(
            event.getToAddress(),
            event.getCryptocurrency(),
            event.getBlockchainNetwork()
        );
        
        if (!fromAddressValid) {
            transaction.setStatus(TransactionStatus.REJECTED);
            transaction.setRejectionReason("Invalid from address format");
            transaction.addComplianceFlag("INVALID_FROM_ADDRESS");
            
            cryptoTransactionRepository.save(transaction);
            
            log.error("Crypto transaction {} rejected - invalid from address: {}", 
                event.getTransactionId(), maskAddress(event.getFromAddress()));
            throw new RuntimeException("Invalid from address format");
        }
        
        if (!toAddressValid) {
            transaction.setStatus(TransactionStatus.REJECTED);
            transaction.setRejectionReason("Invalid to address format");
            transaction.addComplianceFlag("INVALID_TO_ADDRESS");
            
            cryptoTransactionRepository.save(transaction);
            
            log.error("Crypto transaction {} rejected - invalid to address: {}", 
                event.getTransactionId(), maskAddress(event.getToAddress()));
            throw new RuntimeException("Invalid to address format");
        }
        
        // Check against OFAC sanctioned crypto addresses
        boolean fromAddressSanctioned = SANCTIONED_ADDRESSES.contains(event.getFromAddress()) ||
            complianceService.checkOFACSanctionedAddress(event.getFromAddress());
        
        boolean toAddressSanctioned = SANCTIONED_ADDRESSES.contains(event.getToAddress()) ||
            complianceService.checkOFACSanctionedAddress(event.getToAddress());
        
        if (fromAddressSanctioned || toAddressSanctioned) {
            transaction.setStatus(TransactionStatus.BLOCKED_SANCTIONS);
            transaction.setRejectionReason("Address on OFAC sanctions list");
            transaction.addComplianceFlag("SANCTIONED_ADDRESS");
            
            // File immediate SAR for sanctioned address
            String sarId = amlScreeningService.fileSARForSanctionedCrypto(
                event.getUserId(),
                event.getTransactionId(),
                event.getFromAddress(),
                event.getToAddress(),
                event.getUsdValue()
            );
            transaction.setSarId(sarId);
            
            cryptoTransactionRepository.save(transaction);
            
            log.error("CRITICAL: Sanctioned crypto address detected in transaction {}", 
                event.getTransactionId());
            throw new RuntimeException("Transaction involves sanctioned cryptocurrency address");
        }
        
        // Check for high-risk exchange addresses
        boolean fromHighRiskExchange = complianceService.checkHighRiskExchange(event.getFromAddress());
        boolean toHighRiskExchange = complianceService.checkHighRiskExchange(event.getToAddress());
        
        if (fromHighRiskExchange || toHighRiskExchange) {
            transaction.addComplianceFlag("HIGH_RISK_EXCHANGE");
            transaction.setComplianceLevel(ComplianceLevel.ENHANCED);
        }
        
        transaction.setFromAddressVerified(fromAddressValid);
        transaction.setToAddressVerified(toAddressValid);
        
        cryptoTransactionRepository.save(transaction);
        
        log.info("Blockchain address validation completed for transaction {}: From valid: {}, To valid: {}", 
            event.getTransactionId(), fromAddressValid, toAddressValid);
    }
    
    private void executeTravelRuleCompliance(CryptoTransaction transaction, CryptocurrencyTransactionEvent event) {
        // Travel Rule applies to crypto transfers >= $3,000 USD
        if (event.getUsdValue().compareTo(TRAVEL_RULE_THRESHOLD) < 0) {
            transaction.setTravelRuleStatus(TravelRuleStatus.NOT_REQUIRED);
            cryptoTransactionRepository.save(transaction);
            return;
        }
        
        transaction.setTravelRuleStatus(TravelRuleStatus.REQUIRED);
        transaction.addComplianceFlag("TRAVEL_RULE_REQUIRED");
        
        // Collect Travel Rule information for originator and beneficiary
        Map<String, Object> originatorInfo = travelRuleService.collectOriginatorInformation(
            event.getUserId(),
            event.getFromAddress()
        );
        
        transaction.setOriginatorInfo(originatorInfo);
        
        Map<String, Object> beneficiaryInfo = travelRuleService.collectBeneficiaryInformation(
            event.getToAddress(),
            event.getTransactionId()
        );
        
        transaction.setBeneficiaryInfo(beneficiaryInfo);
        
        // Validate Travel Rule data completeness
        boolean originatorComplete = travelRuleService.validateOriginatorData(originatorInfo);
        boolean beneficiaryComplete = travelRuleService.validateBeneficiaryData(beneficiaryInfo);
        
        if (!originatorComplete) {
            transaction.setStatus(TransactionStatus.PENDING_TRAVEL_RULE);
            transaction.addComplianceFlag("INCOMPLETE_ORIGINATOR_DATA");
            transaction.setTravelRuleStatus(TravelRuleStatus.INCOMPLETE);
        }
        
        if (!beneficiaryComplete) {
            transaction.setStatus(TransactionStatus.PENDING_TRAVEL_RULE);
            transaction.addComplianceFlag("INCOMPLETE_BENEFICIARY_DATA");
            transaction.setTravelRuleStatus(TravelRuleStatus.INCOMPLETE);
        }
        
        if (originatorComplete && beneficiaryComplete) {
            // Generate Travel Rule message
            String travelRuleMessageId = travelRuleService.generateTravelRuleMessage(
                event.getTransactionId(),
                originatorInfo,
                beneficiaryInfo,
                event.getCryptocurrency(),
                event.getAmount(),
                event.getUsdValue()
            );
            
            transaction.setTravelRuleMessageId(travelRuleMessageId);
            transaction.setTravelRuleStatus(TravelRuleStatus.SUBMITTED);
            
            // Send Travel Rule message to receiving VASP
            boolean travelRuleSent = travelRuleService.sendTravelRuleMessage(
                travelRuleMessageId,
                event.getToAddress()
            );
            
            if (travelRuleSent) {
                transaction.setTravelRuleStatus(TravelRuleStatus.TRANSMITTED);
            } else {
                transaction.setTravelRuleStatus(TravelRuleStatus.TRANSMISSION_FAILED);
                transaction.addComplianceFlag("TRAVEL_RULE_TRANSMISSION_FAILED");
            }
        }
        
        cryptoTransactionRepository.save(transaction);
        
        log.info("Travel Rule processing completed for transaction {}: Status: {}, Amount: ${}", 
            event.getTransactionId(), transaction.getTravelRuleStatus(), event.getUsdValue());
    }
    
    private void conductCryptoAMLScreening(CryptoTransaction transaction, CryptocurrencyTransactionEvent event) {
        // Screen for suspicious crypto patterns
        Map<String, Object> amlAnalysis = amlScreeningService.analyzeCryptoTransaction(
            event.getUserId(),
            event.getTransactionId(),
            event.getCryptocurrency(),
            event.getAmount(),
            event.getUsdValue(),
            event.getFromAddress(),
            event.getToAddress()
        );
        
        transaction.setAmlAnalysisData(amlAnalysis);
        
        double suspicionScore = (Double) amlAnalysis.get("suspicionScore");
        List<String> riskFactors = (List<String>) amlAnalysis.get("riskFactors");
        
        transaction.setSuspicionScore(suspicionScore);
        transaction.addRiskFlags(riskFactors);
        
        // High suspicion score triggers enhanced review
        if (suspicionScore > 0.7) {
            transaction.addComplianceFlag("HIGH_AML_SUSPICION_SCORE");
            transaction.setRequiresManualReview(true);
            transaction.setComplianceLevel(ComplianceLevel.ENHANCED);
        }
        
        // Check for crypto mixing services
        boolean fromMixer = amlScreeningService.checkCryptoMixer(event.getFromAddress());
        boolean toMixer = amlScreeningService.checkCryptoMixer(event.getToAddress());
        
        if (fromMixer || toMixer) {
            transaction.addComplianceFlag("CRYPTO_MIXER_DETECTED");
            transaction.setRequiresManualReview(true);
            
            // Automatically file SAR for mixer usage
            String sarId = amlScreeningService.fileSARForCryptoMixer(
                event.getUserId(),
                event.getTransactionId(),
                event.getUsdValue(),
                fromMixer ? event.getFromAddress() : event.getToAddress()
            );
            transaction.setSarId(sarId);
        }
        
        // Screen for darknet marketplace addresses
        boolean darknetAddress = amlScreeningService.checkDarknetMarketplace(
            event.getFromAddress(),
            event.getToAddress()
        );
        
        if (darknetAddress) {
            transaction.addComplianceFlag("DARKNET_MARKETPLACE");
            transaction.setRequiresManualReview(true);
            transaction.setComplianceLevel(ComplianceLevel.ENHANCED);
            
            log.warn("Darknet marketplace address detected in transaction {}", event.getTransactionId());
        }
        
        // Check for ransomware addresses
        boolean ransomwareAddress = amlScreeningService.checkRansomwareAddress(
            event.getFromAddress(),
            event.getToAddress()
        );
        
        if (ransomwareAddress) {
            transaction.setStatus(TransactionStatus.BLOCKED_SUSPICIOUS);
            transaction.addComplianceFlag("RANSOMWARE_ADDRESS");
            
            // File immediate SAR for ransomware
            String ransomwareSarId = amlScreeningService.fileSARForRansomware(
                event.getUserId(),
                event.getTransactionId(),
                event.getFromAddress(),
                event.getToAddress(),
                event.getUsdValue()
            );
            transaction.setSarId(ransomwareSarId);
            
            cryptoTransactionRepository.save(transaction);
            
            log.error("CRITICAL: Ransomware address detected in transaction {}", event.getTransactionId());
            throw new RuntimeException("Transaction blocked - ransomware address detected");
        }
        
        cryptoTransactionRepository.save(transaction);
        
        log.info("Crypto AML screening completed for transaction {}: Suspicion score: {}, Risk factors: {}", 
            event.getTransactionId(), suspicionScore, riskFactors.size());
    }
    
    private void validateCustodyAndWalletSecurity(CryptoTransaction transaction, CryptocurrencyTransactionEvent event) {
        // Validate wallet security and custody requirements
        Map<String, Object> walletValidation = custodyService.validateWalletSecurity(
            event.getWalletId(),
            event.getCryptocurrency(),
            event.getAmount()
        );
        
        transaction.setWalletValidationData(walletValidation);
        
        boolean walletSecure = (Boolean) walletValidation.get("secure");
        String custodyType = (String) walletValidation.get("custodyType");
        boolean multiSigEnabled = (Boolean) walletValidation.get("multiSigEnabled");
        
        if (!walletSecure) {
            transaction.addComplianceFlag("WALLET_SECURITY_INSUFFICIENT");
            transaction.setRequiresManualReview(true);
        }
        
        // Check custody requirements for large amounts
        if (event.getUsdValue().compareTo(LARGE_CRYPTO_THRESHOLD) > 0) {
            boolean custodyCompliant = custodyService.checkCustodyCompliance(
                event.getWalletId(),
                event.getUsdValue(),
                custodyType
            );
            
            if (!custodyCompliant) {
                transaction.addComplianceFlag("CUSTODY_REQUIREMENTS_NOT_MET");
                transaction.setRequiresManualReview(true);
            }
            
            transaction.setRequiresCustodyCompliance(true);
        }
        
        // Verify private key security
        boolean privateKeySecure = custodyService.validatePrivateKeySecurity(
            event.getWalletId(),
            event.getFromAddress()
        );
        
        if (!privateKeySecure) {
            transaction.addComplianceFlag("PRIVATE_KEY_SECURITY_RISK");
            transaction.setRequiresManualReview(true);
        }
        
        transaction.setWalletSecure(walletSecure);
        transaction.setCustodyType(custodyType);
        transaction.setMultiSigEnabled(multiSigEnabled);
        transaction.setPrivateKeySecure(privateKeySecure);
        
        cryptoTransactionRepository.save(transaction);
        
        log.info("Custody validation completed for transaction {}: Secure: {}, Custody type: {}, MultiSig: {}", 
            event.getTransactionId(), walletSecure, custodyType, multiSigEnabled);
    }
    
    private void performBlockchainTransactionVerification(CryptoTransaction transaction, CryptocurrencyTransactionEvent event) {
        // Verify transaction on blockchain
        Map<String, Object> blockchainVerification = blockchainValidationService.verifyTransactionOnChain(
            event.getTransactionHash(),
            event.getBlockchainNetwork(),
            event.getCryptocurrency()
        );
        
        transaction.setBlockchainVerificationData(blockchainVerification);
        
        boolean transactionExists = (Boolean) blockchainVerification.get("exists");
        boolean confirmed = (Boolean) blockchainVerification.get("confirmed");
        Integer confirmations = (Integer) blockchainVerification.get("confirmations");
        String blockHash = (String) blockchainVerification.get("blockHash");
        
        transaction.setBlockchainConfirmed(confirmed);
        transaction.setConfirmations(confirmations);
        transaction.setBlockHash(blockHash);
        
        if (!transactionExists) {
            transaction.setStatus(TransactionStatus.BLOCKCHAIN_NOT_FOUND);
            transaction.addComplianceFlag("TRANSACTION_NOT_ON_BLOCKCHAIN");
            
            log.error("Transaction {} not found on blockchain: {}", 
                event.getTransactionId(), event.getTransactionHash());
        }
        
        // Check for required confirmations based on amount
        int requiredConfirmations = custodyService.getRequiredConfirmations(
            event.getCryptocurrency(),
            event.getUsdValue()
        );
        
        transaction.setRequiredConfirmations(requiredConfirmations);
        
        if (confirmed && confirmations >= requiredConfirmations) {
            transaction.setStatus(TransactionStatus.BLOCKCHAIN_CONFIRMED);
        } else if (confirmed) {
            transaction.setStatus(TransactionStatus.PENDING_CONFIRMATIONS);
        }
        
        // Verify transaction amounts match
        BigDecimal blockchainAmount = (BigDecimal) blockchainVerification.get("amount");
        
        if (blockchainAmount != null && blockchainAmount.compareTo(event.getAmount()) != 0) {
            transaction.addComplianceFlag("AMOUNT_MISMATCH");
            transaction.setAmountMismatch(true);
            
            log.warn("Amount mismatch in transaction {}: Event: {}, Blockchain: {}", 
                event.getTransactionId(), event.getAmount(), blockchainAmount);
        }
        
        cryptoTransactionRepository.save(transaction);
        
        log.info("Blockchain verification completed for transaction {}: Confirmed: {}, Confirmations: {}/{}", 
            event.getTransactionId(), confirmed, confirmations, requiredConfirmations);
    }
    
    private void applyPrivacyCoinAndMixerAnalysis(CryptoTransaction transaction, CryptocurrencyTransactionEvent event) {
        // Enhanced analysis for privacy coins
        if (HIGH_RISK_CRYPTOCURRENCIES.contains(event.getCryptocurrency())) {
            transaction.addComplianceFlag("PRIVACY_COIN");
            transaction.setComplianceLevel(ComplianceLevel.ENHANCED);
            transaction.setRequiresManualReview(true);
            
            // Privacy coins require additional documentation
            boolean privacyCoinDocumentation = complianceService.hasPrivacyCoinDocumentation(
                event.getUserId(),
                event.getCryptocurrency()
            );
            
            if (!privacyCoinDocumentation) {
                transaction.addComplianceFlag("PRIVACY_COIN_DOCUMENTATION_MISSING");
            }
            
            // Automatically file CTR for large privacy coin transactions
            if (event.getUsdValue().compareTo(LARGE_CRYPTO_THRESHOLD) > 0) {
                String ctrId = complianceService.fileCTRForPrivacyCoin(
                    event.getUserId(),
                    event.getTransactionId(),
                    event.getCryptocurrency(),
                    event.getUsdValue()
                );
                transaction.setCtrId(ctrId);
            }
        }
        
        // Analyze transaction for mixing patterns
        Map<String, Object> mixingAnalysis = amlScreeningService.analyzeMixingPatterns(
            event.getTransactionHash(),
            event.getFromAddress(),
            event.getToAddress(),
            event.getBlockchainNetwork()
        );
        
        transaction.setMixingAnalysisData(mixingAnalysis);
        
        boolean mixingDetected = (Boolean) mixingAnalysis.get("mixingDetected");
        double mixingScore = (Double) mixingAnalysis.get("mixingScore");
        
        if (mixingDetected || mixingScore > 0.5) {
            transaction.addComplianceFlag("MIXING_PATTERN_DETECTED");
            transaction.setRequiresManualReview(true);
            
            log.warn("Mixing pattern detected in transaction {}: Score: {}", 
                event.getTransactionId(), mixingScore);
        }
        
        // Check for tumbling services
        boolean tumblingService = amlScreeningService.checkTumblingService(
            event.getFromAddress(),
            event.getToAddress()
        );
        
        if (tumblingService) {
            transaction.addComplianceFlag("TUMBLING_SERVICE");
            transaction.setRequiresManualReview(true);
            transaction.setComplianceLevel(ComplianceLevel.ENHANCED);
        }
        
        cryptoTransactionRepository.save(transaction);
        
        log.info("Privacy coin analysis completed for transaction {}: Privacy coin: {}, Mixing score: {}", 
            event.getTransactionId(), HIGH_RISK_CRYPTOCURRENCIES.contains(event.getCryptocurrency()), mixingScore);
    }
    
    private void executeCrossBorderCryptoCompliance(CryptoTransaction transaction, CryptocurrencyTransactionEvent event) {
        // Determine transaction jurisdictions
        String originJurisdiction = complianceService.determineAddressJurisdiction(event.getFromAddress());
        String destinationJurisdiction = complianceService.determineAddressJurisdiction(event.getToAddress());
        
        transaction.setOriginJurisdiction(originJurisdiction);
        transaction.setDestinationJurisdiction(destinationJurisdiction);
        
        // Check for cross-border compliance requirements
        boolean crossBorder = !originJurisdiction.equals(destinationJurisdiction);
        transaction.setCrossBorder(crossBorder);
        
        if (crossBorder) {
            transaction.addComplianceFlag("CROSS_BORDER_CRYPTO");
            
            // Apply enhanced due diligence for cross-border crypto
            boolean enhancedDueDiligence = complianceService.requiresEnhancedDueDiligence(
                originJurisdiction,
                destinationJurisdiction,
                event.getUsdValue()
            );
            
            if (enhancedDueDiligence) {
                transaction.addComplianceFlag("ENHANCED_DUE_DILIGENCE_REQUIRED");
                transaction.setComplianceLevel(ComplianceLevel.ENHANCED);
            }
            
            // Check for sanctioned jurisdictions
            boolean sanctionedJurisdiction = complianceService.checkSanctionedJurisdiction(
                originJurisdiction,
                destinationJurisdiction
            );
            
            if (sanctionedJurisdiction) {
                transaction.setStatus(TransactionStatus.BLOCKED_SANCTIONS);
                transaction.addComplianceFlag("SANCTIONED_JURISDICTION");
                
                cryptoTransactionRepository.save(transaction);
                
                log.error("Transaction {} blocked - involves sanctioned jurisdiction", event.getTransactionId());
                throw new RuntimeException("Transaction involves sanctioned jurisdiction");
            }
        }
        
        // Apply international crypto reporting requirements
        if (event.getUsdValue().compareTo(LARGE_CRYPTO_THRESHOLD) > 0 && crossBorder) {
            boolean internationalReporting = complianceService.requiresInternationalReporting(
                originJurisdiction,
                destinationJurisdiction,
                event.getUsdValue()
            );
            
            if (internationalReporting) {
                String internationalReportId = complianceService.fileInternationalCryptoReport(
                    event.getUserId(),
                    event.getTransactionId(),
                    originJurisdiction,
                    destinationJurisdiction,
                    event.getUsdValue()
                );
                
                transaction.setInternationalReportId(internationalReportId);
                transaction.addComplianceFlag("INTERNATIONAL_REPORTING_FILED");
            }
        }
        
        cryptoTransactionRepository.save(transaction);
        
        log.info("Cross-border compliance completed for transaction {}: Cross-border: {}, From: {} To: {}", 
            event.getTransactionId(), crossBorder, originJurisdiction, destinationJurisdiction);
    }
    
    private void generateIRSForm8300ForLargeCrypto(CryptoTransaction transaction, CryptocurrencyTransactionEvent event) {
        // Form 8300 required for crypto transactions > $10,000
        if (event.getUsdValue().compareTo(LARGE_CRYPTO_THRESHOLD) <= 0) {
            return;
        }
        
        // Generate Form 8300 for large crypto transaction
        String form8300Id = taxReportingService.generateForm8300ForCrypto(
            event.getUserId(),
            event.getTransactionId(),
            event.getCryptocurrency(),
            event.getAmount(),
            event.getUsdValue(),
            event.getFromAddress(),
            event.getToAddress()
        );
        
        transaction.setForm8300Id(form8300Id);
        transaction.addComplianceFlag("FORM_8300_FILED");
        
        // Schedule IRS filing
        taxReportingService.scheduleIRSFiling(
            form8300Id,
            LocalDateTime.now().plusDays(15) // 15 day filing requirement
        );
        
        cryptoTransactionRepository.save(transaction);
        
        log.info("Form 8300 generated for transaction {}: Form ID: {}, Amount: ${}", 
            event.getTransactionId(), form8300Id, event.getUsdValue());
    }
    
    private void updateCryptoPortfolioAndTaxBasis(CryptoTransaction transaction, CryptocurrencyTransactionEvent event) {
        // Update crypto portfolio composition
        taxReportingService.updateCryptoPortfolio(
            event.getUserId(),
            event.getCryptocurrency(),
            event.getAmount(),
            event.getUsdValue(),
            event.getTransactionType()
        );
        
        // Update tax basis for crypto holdings
        taxReportingService.updateCryptoTaxBasis(
            event.getUserId(),
            event.getCryptocurrency(),
            event.getAmount(),
            event.getUsdValue(),
            event.getTransactionType(),
            LocalDateTime.now()
        );
        
        // Calculate potential tax implications
        Map<String, Object> taxImplications = taxReportingService.calculateCryptoTaxImplications(
            event.getUserId(),
            event.getTransactionId(),
            event.getCryptocurrency(),
            event.getAmount(),
            event.getUsdValue(),
            event.getTransactionType()
        );
        
        transaction.setTaxImplicationData(taxImplications);
        
        boolean taxableEvent = (Boolean) taxImplications.get("taxableEvent");
        BigDecimal estimatedTaxLiability = (BigDecimal) taxImplications.get("estimatedTaxLiability");
        
        if (taxableEvent) {
            transaction.addComplianceFlag("TAXABLE_EVENT");
            transaction.setTaxableEvent(true);
            transaction.setEstimatedTaxLiability(estimatedTaxLiability);
        }
        
        cryptoTransactionRepository.save(transaction);
        
        log.info("Crypto portfolio updated for transaction {}: Taxable event: {}, Tax liability: ${}", 
            event.getTransactionId(), taxableEvent, estimatedTaxLiability);
    }
    
    private void monitorSuspiciousTradingPatterns(CryptoTransaction transaction, CryptocurrencyTransactionEvent event) {
        // Monitor for suspicious crypto trading patterns
        Map<String, Object> patternAnalysis = amlScreeningService.analyzeCryptoTradingPatterns(
            event.getUserId(),
            event.getCryptocurrency(),
            event.getAmount(),
            event.getUsdValue(),
            LocalDateTime.now().minusDays(30)
        );
        
        transaction.setTradingPatternData(patternAnalysis);
        
        List<String> suspiciousPatterns = (List<String>) patternAnalysis.get("suspiciousPatterns");
        double patternRiskScore = (Double) patternAnalysis.get("riskScore");
        
        transaction.addRiskFlags(suspiciousPatterns);
        transaction.setPatternRiskScore(patternRiskScore);
        
        // High-frequency trading detection
        boolean highFrequencyTrading = (Boolean) patternAnalysis.get("highFrequencyTrading");
        
        if (highFrequencyTrading) {
            transaction.addComplianceFlag("HIGH_FREQUENCY_CRYPTO_TRADING");
        }
        
        // Structured transaction detection
        boolean structuringDetected = (Boolean) patternAnalysis.get("structuringDetected");
        
        if (structuringDetected) {
            transaction.addComplianceFlag("CRYPTO_STRUCTURING_DETECTED");
            transaction.setRequiresManualReview(true);
            
            // File SAR for structuring
            String sarId = amlScreeningService.fileSARForCryptoStructuring(
                event.getUserId(),
                event.getTransactionId(),
                event.getUsdValue(),
                "Crypto transaction structuring pattern detected"
            );
            transaction.setSarId(sarId);
        }
        
        // Wash trading detection
        boolean washTrading = (Boolean) patternAnalysis.get("washTradingDetected");
        
        if (washTrading) {
            transaction.addComplianceFlag("WASH_TRADING_DETECTED");
            transaction.setRequiresManualReview(true);
        }
        
        cryptoTransactionRepository.save(transaction);
        
        log.info("Trading pattern monitoring completed for transaction {}: Risk score: {}, Patterns: {}", 
            event.getTransactionId(), patternRiskScore, suspiciousPatterns.size());
    }
    
    private void sendComplianceNotificationsAndAlerts(CryptoTransaction transaction, CryptocurrencyTransactionEvent event) {
        // Send transaction confirmation to customer
        notificationService.sendCryptoTransactionConfirmation(
            event.getUserId(),
            event.getTransactionId(),
            event.getCryptocurrency(),
            event.getAmount(),
            event.getUsdValue(),
            transaction.getStatus()
        );
        
        // Send compliance alerts for high-risk transactions
        if (transaction.getComplianceLevel() == ComplianceLevel.ENHANCED ||
            transaction.isRequiresManualReview()) {
            
            notificationService.sendCryptoComplianceAlert(
                event.getTransactionId(),
                event.getCryptocurrency(),
                event.getUsdValue(),
                transaction.getComplianceFlags(),
                transaction.getRiskFlags()
            );
        }
        
        // Alert for sanctioned addresses
        if (transaction.getStatus() == TransactionStatus.BLOCKED_SANCTIONS) {
            notificationService.sendSanctionedCryptoAlert(
                event.getTransactionId(),
                event.getFromAddress(),
                event.getToAddress(),
                event.getUsdValue()
            );
        }
        
        // Travel Rule notifications
        if (transaction.getTravelRuleStatus() == TravelRuleStatus.INCOMPLETE) {
            notificationService.sendTravelRuleIncompleteNotification(
                event.getUserId(),
                event.getTransactionId(),
                event.getUsdValue()
            );
        }
        
        // Large crypto transaction alerts
        if (event.getUsdValue().compareTo(LARGE_CRYPTO_THRESHOLD) > 0) {
            notificationService.sendLargeCryptoTransactionAlert(
                event.getTransactionId(),
                event.getCryptocurrency(),
                event.getUsdValue(),
                transaction.isBlockchainConfirmed()
            );
        }
        
        // Privacy coin notifications
        if (HIGH_RISK_CRYPTOCURRENCIES.contains(event.getCryptocurrency())) {
            notificationService.sendPrivacyCoinNotification(
                event.getUserId(),
                event.getCryptocurrency(),
                event.getUsdValue(),
                transaction.getComplianceFlags()
            );
        }
        
        log.info("Compliance notifications sent for crypto transaction {}", event.getTransactionId());
    }
    
    private String maskAddress(String address) {
        if (address == null || address.length() < 8) {
            return "***";
        }
        return address.substring(0, 4) + "***" + address.substring(address.length() - 4);
    }
    
    private void createManualInterventionRecord(CryptocurrencyTransactionEvent event, Exception exception) {
        manualInterventionService.createTask(
            "CRYPTO_TRANSACTION_PROCESSING_FAILED",
            String.format(
                "Failed to process cryptocurrency transaction. " +
                "Transaction ID: %s, User ID: %s, Currency: %s, Amount: %s, USD Value: $%.2f. " +
                "Blockchain verification may be incomplete. Travel Rule compliance may be violated. " +
                "Exception: %s. Manual intervention required.",
                event.getTransactionId(),
                event.getUserId(),
                event.getCryptocurrency(),
                event.getAmount(),
                event.getUsdValue(),
                exception.getMessage()
            ),
            "CRITICAL",
            event,
            exception
        );
    }
}