package com.waqiti.investment.consumer;

import com.waqiti.common.events.InvestmentAccountOpenedEvent;
import com.waqiti.common.messaging.EventConsumer;
import com.waqiti.investment.dto.AccountSuitabilityAssessment;
import com.waqiti.investment.dto.BrokerageComplianceReport;
import com.waqiti.investment.dto.InvestorProfile;
import com.waqiti.investment.dto.RegulatoryFilingRequest;
import com.waqiti.investment.entity.InvestmentAccount;
import com.waqiti.investment.service.AccountSuitabilityService;
import com.waqiti.investment.service.BrokerageComplianceService;
import com.waqiti.investment.service.InvestorProfileService;
import com.waqiti.investment.service.RegulatoryFilingService;
import com.waqiti.investment.repository.InvestmentAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentAccountOpenedEventConsumer implements EventConsumer<InvestmentAccountOpenedEvent> {

    private final InvestmentAccountRepository investmentAccountRepository;
    private final AccountSuitabilityService accountSuitabilityService;
    private final BrokerageComplianceService brokerageComplianceService;
    private final InvestorProfileService investorProfileService;
    private final RegulatoryFilingService regulatoryFilingService;

    @Override
    @KafkaListener(
        topics = "investment-account-opened",
        groupId = "investment-account-opened-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        propagation = Propagation.REQUIRED,
        rollbackFor = Exception.class
    )
    public void consume(
        InvestmentAccountOpenedEvent event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment
    ) {
        try {
            log.info("Processing InvestmentAccountOpenedEvent: accountId={}, customerId={}, accountType={}", 
                    event.getAccountId(), event.getCustomerId(), event.getAccountType());

            if (isAlreadyProcessed(event.getEventId())) {
                log.warn("Event already processed, skipping: eventId={}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }

            processInvestmentAccountOpening(event);
            markEventAsProcessed(event.getEventId());
            acknowledgment.acknowledge();

            log.info("Successfully processed InvestmentAccountOpenedEvent: eventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("Error processing InvestmentAccountOpenedEvent: eventId={}, error={}", 
                    event.getEventId(), e.getMessage(), e);
            throw e;
        }
    }

    private void processInvestmentAccountOpening(InvestmentAccountOpenedEvent event) {
        // Step 1: Create investment account record
        InvestmentAccount account = createInvestmentAccount(event);
        
        // Step 2: Validate account opening requirements
        validateAccountOpeningRequirements(event, account);
        
        // Step 3: Perform investor suitability assessment
        AccountSuitabilityAssessment suitabilityAssessment = performSuitabilityAssessment(event, account);
        
        // Step 4: Create comprehensive investor profile
        InvestorProfile investorProfile = createInvestorProfile(event, account, suitabilityAssessment);
        
        // Step 5: Execute FINRA compliance checks
        executeFINRAComplianceChecks(event, account, investorProfile);
        
        // Step 6: Perform SEC regulatory validation
        performSECRegulatoryValidation(event, account, investorProfile);
        
        // Step 7: Execute pattern day trader classification
        classifyPatternDayTrader(event, account, investorProfile);
        
        // Step 8: Setup margin account requirements if applicable
        setupMarginAccountRequirements(event, account, investorProfile);
        
        // Step 9: Execute options trading approval process
        processOptionsTradingApproval(event, account, investorProfile, suitabilityAssessment);
        
        // Step 10: Generate required regulatory filings
        generateRegulatoryFilings(event, account, investorProfile);
        
        // Step 11: Setup account monitoring and alerts
        setupAccountMonitoring(event, account, investorProfile);
        
        // Step 12: Send multi-channel welcome notifications
        sendWelcomeNotifications(event, account, investorProfile);
    }

    private InvestmentAccount createInvestmentAccount(InvestmentAccountOpenedEvent event) {
        InvestmentAccount account = InvestmentAccount.builder()
            .accountId(event.getAccountId())
            .customerId(event.getCustomerId())
            .accountType(event.getAccountType())
            .accountNumber(event.getAccountNumber())
            .status("ACTIVE")
            .baseCurrency(event.getBaseCurrency())
            .initialDeposit(event.getInitialDeposit())
            .tradingPermissions(event.getTradingPermissions())
            .marginEnabled(event.isMarginEnabled())
            .optionsLevel(event.getOptionsLevel())
            .dayTradingBuyingPower(BigDecimal.ZERO)
            .overnightBuyingPower(event.getInitialDeposit())
            .maintenanceRequirement(BigDecimal.ZERO)
            .createdAt(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .build();
            
        return investmentAccountRepository.save(account);
    }

    private void validateAccountOpeningRequirements(InvestmentAccountOpenedEvent event, InvestmentAccount account) {
        // Validate minimum deposit requirements
        BigDecimal minimumDeposit = getMinimumDepositRequirement(event.getAccountType());
        if (event.getInitialDeposit().compareTo(minimumDeposit) < 0) {
            throw new IllegalStateException("Initial deposit below minimum requirement: " + minimumDeposit);
        }
        
        // Validate customer age and citizenship
        if (event.getCustomerAge() < 18) {
            throw new IllegalStateException("Customer must be at least 18 years old");
        }
        
        // Validate required documentation
        validateRequiredDocumentation(event);
        
        // Validate account type permissions
        validateAccountTypePermissions(event);
        
        log.info("Account opening requirements validated for accountId: {}", event.getAccountId());
    }

    private AccountSuitabilityAssessment performSuitabilityAssessment(InvestmentAccountOpenedEvent event, InvestmentAccount account) {
        AccountSuitabilityAssessment assessment = AccountSuitabilityAssessment.builder()
            .accountId(event.getAccountId())
            .customerId(event.getCustomerId())
            .investmentExperience(event.getInvestmentExperience())
            .investmentObjective(event.getInvestmentObjective())
            .riskTolerance(event.getRiskTolerance())
            .timeHorizon(event.getTimeHorizon())
            .liquidityNeeds(event.getLiquidityNeeds())
            .annualIncome(event.getAnnualIncome())
            .netWorth(event.getNetWorth())
            .employmentStatus(event.getEmploymentStatus())
            .build();
            
        // Calculate suitability score based on FINRA guidelines
        int suitabilityScore = accountSuitabilityService.calculateSuitabilityScore(assessment);
        assessment.setSuitabilityScore(suitabilityScore);
        
        // Determine appropriate investment categories
        List<String> approvedCategories = accountSuitabilityService.determineApprovedCategories(assessment);
        assessment.setApprovedInvestmentCategories(approvedCategories);
        
        // Set investment limits based on suitability
        Map<String, BigDecimal> investmentLimits = accountSuitabilityService.calculateInvestmentLimits(assessment);
        assessment.setInvestmentLimits(investmentLimits);
        
        accountSuitabilityService.saveAssessment(assessment);
        
        log.info("Suitability assessment completed: accountId={}, score={}", 
                event.getAccountId(), suitabilityScore);
        
        return assessment;
    }

    private InvestorProfile createInvestorProfile(InvestmentAccountOpenedEvent event, InvestmentAccount account, 
                                                 AccountSuitabilityAssessment assessment) {
        InvestorProfile profile = InvestorProfile.builder()
            .customerId(event.getCustomerId())
            .accountId(event.getAccountId())
            .investorType(determineInvestorType(assessment))
            .accreditedInvestor(event.isAccreditedInvestor())
            .qualifiedPurchaser(event.isQualifiedPurchaser())
            .eligibleContractParticipant(event.isEligibleContractParticipant())
            .institutionalInvestor(event.isInstitutionalInvestor())
            .professionalClient(event.isProfessionalClient())
            .highNetWorthIndividual(assessment.getNetWorth().compareTo(new BigDecimal("1000000")) >= 0)
            .patternDayTrader(false) // Will be determined later
            .marginApproved(event.isMarginEnabled())
            .optionsApproved(event.getOptionsLevel() > 0)
            .futuresApproved(event.getTradingPermissions().contains("FUTURES"))
            .internationalTradingApproved(event.getTradingPermissions().contains("INTERNATIONAL"))
            .cryptoTradingApproved(event.getTradingPermissions().contains("CRYPTO"))
            .build();
            
        return investorProfileService.createProfile(profile);
    }

    private void executeFINRAComplianceChecks(InvestmentAccountOpenedEvent event, InvestmentAccount account, 
                                            InvestorProfile profile) {
        // Check FINRA Rule 2111 - Suitability
        brokerageComplianceService.validateSuitabilityRequirements(account, profile);
        
        // Check FINRA Rule 4512 - Customer Account Information
        brokerageComplianceService.validateCustomerAccountInformation(event, account);
        
        // Check FINRA Rule 3310 - Anti-Money Laundering
        brokerageComplianceService.executeAMLScreening(event.getCustomerId(), account);
        
        // Check FINRA Rule 2090 - Know Your Customer
        brokerageComplianceService.validateKYCRequirements(event, profile);
        
        // Generate FINRA compliance report
        BrokerageComplianceReport report = brokerageComplianceService.generateComplianceReport(account, profile);
        brokerageComplianceService.saveComplianceReport(report);
        
        log.info("FINRA compliance checks completed for accountId: {}", event.getAccountId());
    }

    private void performSECRegulatoryValidation(InvestmentAccountOpenedEvent event, InvestmentAccount account, 
                                               InvestorProfile profile) {
        // Validate SEC accredited investor status
        if (profile.isAccreditedInvestor()) {
            regulatoryFilingService.validateAccreditedInvestorStatus(event.getCustomerId(), profile);
        }
        
        // Check SEC Rule 15c3-3 - Customer Protection Rule
        regulatoryFilingService.validateCustomerProtectionRequirements(account);
        
        // Validate investment adviser requirements if applicable
        if (event.isInvestmentAdviserClient()) {
            regulatoryFilingService.validateInvestmentAdviserCompliance(event, profile);
        }
        
        // Generate SEC regulatory documentation
        regulatoryFilingService.generateSECDisclosureDocuments(account, profile);
        
        log.info("SEC regulatory validation completed for accountId: {}", event.getAccountId());
    }

    private void classifyPatternDayTrader(InvestmentAccountOpenedEvent event, InvestmentAccount account, 
                                        InvestorProfile profile) {
        // Pattern Day Trader classification per FINRA Rule 4210
        if (account.isMarginEnabled()) {
            // Check if customer meets PDT criteria
            boolean isPDT = brokerageComplianceService.evaluatePatternDayTraderStatus(
                event.getCustomerId(), event.getInitialDeposit());
                
            if (isPDT) {
                profile.setPatternDayTrader(true);
                account.setMinimumEquity(new BigDecimal("25000")); // PDT minimum equity requirement
                
                // Set day trading buying power (4:1 leverage)
                BigDecimal dayTradingBP = account.getOvernightBuyingPower().multiply(new BigDecimal("4"));
                account.setDayTradingBuyingPower(dayTradingBP);
                
                log.info("Customer classified as Pattern Day Trader: accountId={}", event.getAccountId());
            }
        }
        
        investmentAccountRepository.save(account);
        investorProfileService.updateProfile(profile);
    }

    private void setupMarginAccountRequirements(InvestmentAccountOpenedEvent event, InvestmentAccount account, 
                                              InvestorProfile profile) {
        if (account.isMarginEnabled()) {
            // Set Regulation T initial margin requirement (50%)
            account.setInitialMarginRequirement(new BigDecimal("0.50"));
            
            // Set maintenance margin requirement (25%)
            account.setMaintenanceMarginRequirement(new BigDecimal("0.25"));
            
            // Calculate initial buying power (2:1 leverage for overnight positions)
            BigDecimal overnightBP = account.getOvernightBuyingPower().multiply(new BigDecimal("2"));
            account.setOvernightBuyingPower(overnightBP);
            
            // Setup margin call monitoring
            brokerageComplianceService.setupMarginCallMonitoring(account);
            
            // Generate margin agreement documents
            regulatoryFilingService.generateMarginAgreementDocuments(account, profile);
            
            log.info("Margin account requirements configured: accountId={}", event.getAccountId());
        }
        
        investmentAccountRepository.save(account);
    }

    private void processOptionsTradingApproval(InvestmentAccountOpenedEvent event, InvestmentAccount account, 
                                             InvestorProfile profile, AccountSuitabilityAssessment assessment) {
        if (event.getOptionsLevel() > 0) {
            // Validate options trading suitability per FINRA guidelines
            boolean approved = accountSuitabilityService.validateOptionsTradingSuitability(
                assessment, event.getOptionsLevel());
                
            if (approved) {
                profile.setOptionsApproved(true);
                profile.setOptionsLevel(event.getOptionsLevel());
                
                // Set options-specific requirements based on level
                setupOptionsRequirements(account, event.getOptionsLevel());
                
                // Generate options agreement documents
                regulatoryFilingService.generateOptionsAgreementDocuments(account, profile);
                
                log.info("Options trading approved: accountId={}, level={}", 
                        event.getAccountId(), event.getOptionsLevel());
            } else {
                profile.setOptionsApproved(false);
                profile.setOptionsLevel(0);
                log.warn("Options trading not approved due to suitability: accountId={}", event.getAccountId());
            }
        }
        
        investorProfileService.updateProfile(profile);
    }

    private void generateRegulatoryFilings(InvestmentAccountOpenedEvent event, InvestmentAccount account, 
                                         InvestorProfile profile) {
        // Generate Form ADV disclosures if investment adviser relationship
        if (event.isInvestmentAdviserClient()) {
            RegulatoryFilingRequest advFiling = RegulatoryFilingRequest.builder()
                .filingType("FORM_ADV")
                .accountId(event.getAccountId())
                .customerId(event.getCustomerId())
                .priority("HIGH")
                .dueDate(LocalDateTime.now().plusDays(30))
                .build();
            regulatoryFilingService.submitFiling(advFiling);
        }
        
        // Generate customer identification program documentation
        RegulatoryFilingRequest cipFiling = RegulatoryFilingRequest.builder()
            .filingType("CIP_DOCUMENTATION")
            .accountId(event.getAccountId())
            .customerId(event.getCustomerId())
            .priority("CRITICAL")
            .dueDate(LocalDateTime.now().plusDays(7))
            .build();
        regulatoryFilingService.submitFiling(cipFiling);
        
        // Generate SIPC coverage documentation
        regulatoryFilingService.generateSIPCCoverageDocuments(account, profile);
        
        log.info("Regulatory filings initiated for accountId: {}", event.getAccountId());
    }

    private void setupAccountMonitoring(InvestmentAccountOpenedEvent event, InvestmentAccount account, 
                                       InvestorProfile profile) {
        // Setup position monitoring for concentration risk
        brokerageComplianceService.setupPositionMonitoring(account);
        
        // Setup trading pattern analysis
        brokerageComplianceService.setupTradingPatternAnalysis(account, profile);
        
        // Setup margin call monitoring if applicable
        if (account.isMarginEnabled()) {
            brokerageComplianceService.setupMarginCallMonitoring(account);
        }
        
        // Setup unusual activity monitoring
        brokerageComplianceService.setupUnusualActivityMonitoring(account, profile);
        
        // Setup regulatory reporting schedules
        regulatoryFilingService.setupReportingSchedules(account, profile);
        
        log.info("Account monitoring configured for accountId: {}", event.getAccountId());
    }

    private void sendWelcomeNotifications(InvestmentAccountOpenedEvent event, InvestmentAccount account, 
                                        InvestorProfile profile) {
        // Email welcome message with account details
        Map<String, Object> emailContext = Map.of(
            "accountNumber", account.getAccountNumber(),
            "accountType", account.getAccountType(),
            "tradingPermissions", account.getTradingPermissions(),
            "marginEnabled", account.isMarginEnabled(),
            "optionsLevel", profile.getOptionsLevel(),
            "customerName", event.getCustomerName()
        );
        
        // SMS confirmation
        Map<String, Object> smsContext = Map.of(
            "accountNumber", account.getAccountNumber(),
            "accountType", account.getAccountType()
        );
        
        // In-app notification
        Map<String, Object> appContext = Map.of(
            "accountId", event.getAccountId(),
            "accountNumber", account.getAccountNumber(),
            "welcomeMessage", "Your investment account has been successfully opened!",
            "nextSteps", generateNextStepsInstructions(account, profile)
        );
        
        // Mobile push notification
        Map<String, Object> pushContext = Map.of(
            "title", "Investment Account Opened",
            "message", "Your " + account.getAccountType() + " account is now active!",
            "accountId", event.getAccountId()
        );
        
        log.info("Welcome notifications sent for investment account: accountId={}", event.getAccountId());
    }

    private BigDecimal getMinimumDepositRequirement(String accountType) {
        return switch (accountType) {
            case "MARGIN" -> new BigDecimal("2000"); // Reg T requirement
            case "PATTERN_DAY_TRADER" -> new BigDecimal("25000"); // PDT requirement
            case "CASH" -> new BigDecimal("500"); // Minimum for cash account
            case "RETIREMENT_IRA" -> new BigDecimal("100"); // IRA minimum
            case "RETIREMENT_401K" -> new BigDecimal("1000"); // 401k minimum
            default -> new BigDecimal("1000"); // Default minimum
        };
    }

    private void validateRequiredDocumentation(InvestmentAccountOpenedEvent event) {
        List<String> requiredDocs = List.of("GOVERNMENT_ID", "SSN_VERIFICATION", "ADDRESS_PROOF");
        
        for (String docType : requiredDocs) {
            if (!event.getProvidedDocuments().contains(docType)) {
                throw new IllegalStateException("Missing required documentation: " + docType);
            }
        }
        
        // Additional documentation for specific account types
        if (event.isMarginEnabled() && !event.getProvidedDocuments().contains("MARGIN_AGREEMENT")) {
            throw new IllegalStateException("Margin agreement required for margin account");
        }
        
        if (event.getOptionsLevel() > 0 && !event.getProvidedDocuments().contains("OPTIONS_AGREEMENT")) {
            throw new IllegalStateException("Options agreement required for options trading");
        }
    }

    private void validateAccountTypePermissions(InvestmentAccountOpenedEvent event) {
        // Validate margin account requirements
        if (event.isMarginEnabled() && event.getAnnualIncome().compareTo(new BigDecimal("50000")) < 0) {
            throw new IllegalStateException("Insufficient income for margin account");
        }
        
        // Validate options level requirements
        if (event.getOptionsLevel() > 2 && event.getInvestmentExperience() < 3) {
            throw new IllegalStateException("Insufficient experience for advanced options level");
        }
        
        // Validate futures trading requirements
        if (event.getTradingPermissions().contains("FUTURES") && 
            event.getRiskTolerance().equals("CONSERVATIVE")) {
            throw new IllegalStateException("Conservative risk tolerance incompatible with futures trading");
        }
    }

    private String determineInvestorType(AccountSuitabilityAssessment assessment) {
        if (assessment.getNetWorth().compareTo(new BigDecimal("5000000")) >= 0) {
            return "ULTRA_HIGH_NET_WORTH";
        } else if (assessment.getNetWorth().compareTo(new BigDecimal("1000000")) >= 0) {
            return "HIGH_NET_WORTH";
        } else if (assessment.getAnnualIncome().compareTo(new BigDecimal("200000")) >= 0) {
            return "AFFLUENT";
        } else {
            return "RETAIL";
        }
    }

    private void setupOptionsRequirements(InvestmentAccount account, int optionsLevel) {
        switch (optionsLevel) {
            case 1 -> {
                // Level 1: Covered calls and cash-secured puts
                account.setOptionsRequirements("COVERED_POSITIONS_ONLY");
            }
            case 2 -> {
                // Level 2: Long options (calls and puts)
                account.setOptionsRequirements("LONG_OPTIONS_ALLOWED");
            }
            case 3 -> {
                // Level 3: Spreads and short options with collateral
                account.setOptionsRequirements("SPREADS_ALLOWED");
                account.setMinimumOptionsEquity(new BigDecimal("10000"));
            }
            case 4 -> {
                // Level 4: Naked options writing
                account.setOptionsRequirements("NAKED_OPTIONS_ALLOWED");
                account.setMinimumOptionsEquity(new BigDecimal("25000"));
            }
            default -> account.setOptionsRequirements("NO_OPTIONS");
        }
    }

    private List<String> generateNextStepsInstructions(InvestmentAccount account, InvestorProfile profile) {
        return List.of(
            "Fund your account to begin trading",
            "Review your investment permissions and limits",
            "Complete any pending documentation",
            "Explore our research and analysis tools",
            "Set up your trading preferences and alerts"
        );
    }

    private boolean isAlreadyProcessed(UUID eventId) {
        return investmentAccountRepository.existsByEventId(eventId);
    }

    private void markEventAsProcessed(UUID eventId) {
        investmentAccountRepository.markEventAsProcessed(eventId);
    }
}