package com.waqiti.lending.kafka;

import com.waqiti.common.events.MortgageEvent;
import com.waqiti.lending.domain.Mortgage;
import com.waqiti.lending.domain.PropertyCollateral;
import com.waqiti.lending.repository.MortgageRepository;
import com.waqiti.lending.repository.PropertyCollateralRepository;
import com.waqiti.lending.service.MortgageService;
import com.waqiti.lending.service.PropertyAppraisalService;
import com.waqiti.lending.service.TitleInsuranceService;
import com.waqiti.lending.metrics.LendingMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class MortgageEventsConsumer {
    
    private final MortgageRepository mortgageRepository;
    private final PropertyCollateralRepository propertyCollateralRepository;
    private final MortgageService mortgageService;
    private final PropertyAppraisalService propertyAppraisalService;
    private final TitleInsuranceService titleInsuranceService;
    private final LendingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal MAX_LTV_RATIO = new BigDecimal("0.95");
    private static final BigDecimal MAX_DTI_RATIO = new BigDecimal("0.43");
    private static final BigDecimal PMI_THRESHOLD_LTV = new BigDecimal("0.80");
    private static final int MAX_MORTGAGE_TERM_YEARS = 30;
    
    @KafkaListener(
        topics = {"mortgage-events", "home-loan-events", "property-financing-events"},
        groupId = "lending-mortgage-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 180)
    public void handleMortgageEvent(
            @Payload MortgageEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("mortgage-%s-p%d-o%d", 
            event.getMortgageId() != null ? event.getMortgageId() : event.getBorrowerId(), 
            partition, offset);
        
        log.info("Processing mortgage event: mortgageId={}, type={}, propertyValue={}", 
            event.getMortgageId(), event.getEventType(), event.getPropertyValue());
        
        try {
            switch (event.getEventType()) {
                case MORTGAGE_APPLICATION_SUBMITTED:
                    processMortgageApplicationSubmitted(event, correlationId);
                    break;
                case PROPERTY_APPRAISED:
                    processPropertyAppraised(event, correlationId);
                    break;
                case HOME_INSPECTION_COMPLETED:
                    processHomeInspectionCompleted(event, correlationId);
                    break;
                case TITLE_SEARCH_COMPLETED:
                    processTitleSearchCompleted(event, correlationId);
                    break;
                case MORTGAGE_UNDERWRITING_COMPLETED:
                    processMortgageUnderwritingCompleted(event, correlationId);
                    break;
                case MORTGAGE_APPROVED:
                    processMortgageApproved(event, correlationId);
                    break;
                case PMI_REQUIRED:
                    processPmiRequired(event, correlationId);
                    break;
                case CLOSING_SCHEDULED:
                    processClosingScheduled(event, correlationId);
                    break;
                case MORTGAGE_CLOSED:
                    processMortgageClosed(event, correlationId);
                    break;
                case ESCROW_ACCOUNT_OPENED:
                    processEscrowAccountOpened(event, correlationId);
                    break;
                case PROPERTY_TAX_PAID:
                    processPropertyTaxPaid(event, correlationId);
                    break;
                case MORTGAGE_REFINANCED:
                    processMortgageRefinanced(event, correlationId);
                    break;
                default:
                    log.warn("Unknown mortgage event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logLendingEvent(
                "MORTGAGE_EVENT_PROCESSED",
                event.getMortgageId() != null ? event.getMortgageId() : "N/A",
                Map.of(
                    "eventType", event.getEventType(),
                    "borrowerId", event.getBorrowerId() != null ? event.getBorrowerId() : "N/A",
                    "propertyAddress", event.getPropertyAddress() != null ? event.getPropertyAddress() : "N/A",
                    "loanAmount", event.getLoanAmount() != null ? event.getLoanAmount().toString() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process mortgage event: {}", e.getMessage(), e);
            kafkaTemplate.send("mortgage-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processMortgageApplicationSubmitted(MortgageEvent event, String correlationId) {
        log.info("Mortgage application submitted: borrowerId={}, loanAmount={}, property={}, downPayment={}", 
            event.getBorrowerId(), event.getLoanAmount(), event.getPropertyAddress(), event.getDownPayment());
        
        BigDecimal ltvRatio = event.getLoanAmount().divide(event.getPropertyValue(), 4, 
            java.math.RoundingMode.HALF_UP);
        
        Mortgage mortgage = Mortgage.builder()
            .id(UUID.randomUUID().toString())
            .borrowerId(event.getBorrowerId())
            .loanAmount(event.getLoanAmount())
            .propertyAddress(event.getPropertyAddress())
            .propertyValue(event.getPropertyValue())
            .downPayment(event.getDownPayment())
            .ltvRatio(ltvRatio)
            .mortgageType(event.getMortgageType())
            .termYears(event.getTermYears())
            .annualIncome(event.getAnnualIncome())
            .applicationDate(LocalDateTime.now())
            .status("SUBMITTED")
            .correlationId(correlationId)
            .build();
        
        mortgageRepository.save(mortgage);
        
        propertyAppraisalService.orderAppraisal(mortgage.getId(), event.getPropertyAddress());
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Mortgage Application Received",
            String.format("Your mortgage application for %s has been received. " +
                "Property: %s, Loan: %s, Down payment: %s (%s%%). " +
                "We're ordering a property appraisal and will keep you updated.",
                event.getLoanAmount(), event.getPropertyAddress(), 
                event.getLoanAmount(), event.getDownPayment(), 
                event.getDownPayment().divide(event.getPropertyValue(), 2, 
                    java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100"))),
            correlationId
        );
        
        metricsService.recordMortgageApplicationSubmitted(
            event.getMortgageType(), event.getLoanAmount(), ltvRatio);
    }
    
    private void processPropertyAppraised(MortgageEvent event, String correlationId) {
        log.info("Property appraised: mortgageId={}, appraisedValue={}, marketValue={}", 
            event.getMortgageId(), event.getAppraisedValue(), event.getMarketValue());
        
        Mortgage mortgage = mortgageRepository.findById(event.getMortgageId())
            .orElseThrow();
        
        PropertyCollateral property = PropertyCollateral.builder()
            .id(UUID.randomUUID().toString())
            .mortgageId(event.getMortgageId())
            .address(mortgage.getPropertyAddress())
            .appraisedValue(event.getAppraisedValue())
            .marketValue(event.getMarketValue())
            .propertyType(event.getPropertyType())
            .squareFootage(event.getSquareFootage())
            .yearBuilt(event.getYearBuilt())
            .bedrooms(event.getBedrooms())
            .bathrooms(event.getBathrooms())
            .appraisalDate(LocalDateTime.now())
            .appraiser(event.getAppraiser())
            .build();
        
        propertyCollateralRepository.save(property);
        
        BigDecimal newLtvRatio = mortgage.getLoanAmount().divide(
            event.getAppraisedValue(), 4, java.math.RoundingMode.HALF_UP);
        
        mortgage.setAppraisedValue(event.getAppraisedValue());
        mortgage.setLtvRatio(newLtvRatio);
        mortgage.setPropertyAppraised(true);
        mortgageRepository.save(mortgage);
        
        if (event.getAppraisedValue().compareTo(mortgage.getPropertyValue()) < 0) {
            log.warn("Appraisal came in low: expected={}, appraised={}", 
                mortgage.getPropertyValue(), event.getAppraisedValue());
            
            notificationService.sendNotification(
                mortgage.getBorrowerId(),
                "Property Appraisal Update",
                String.format("The property appraised at %s, which is lower than the purchase price of %s. " +
                    "This may affect your loan terms. We'll contact you to discuss options.",
                    event.getAppraisedValue(), mortgage.getPropertyValue()),
                correlationId
            );
        }
        
        metricsService.recordPropertyAppraised(event.getAppraisedValue(), newLtvRatio);
    }
    
    private void processHomeInspectionCompleted(MortgageEvent event, String correlationId) {
        log.info("Home inspection completed: mortgageId={}, result={}, issues={}", 
            event.getMortgageId(), event.getInspectionResult(), event.getInspectionIssues());
        
        Mortgage mortgage = mortgageRepository.findById(event.getMortgageId())
            .orElseThrow();
        
        mortgage.setHomeInspected(true);
        mortgage.setInspectionDate(LocalDateTime.now());
        mortgage.setInspectionResult(event.getInspectionResult());
        mortgage.setInspectionIssues(event.getInspectionIssues());
        mortgageRepository.save(mortgage);
        
        PropertyCollateral property = propertyCollateralRepository.findByMortgageId(event.getMortgageId())
            .orElseThrow();
        
        property.setInspectionResult(event.getInspectionResult());
        property.setInspectionIssues(event.getInspectionIssues());
        propertyCollateralRepository.save(property);
        
        if ("MAJOR_ISSUES".equals(event.getInspectionResult())) {
            notificationService.sendNotification(
                mortgage.getBorrowerId(),
                "Home Inspection - Issues Found",
                String.format("The home inspection found major issues: %s. " +
                    "You may want to renegotiate with the seller or request repairs before closing.",
                    String.join(", ", event.getInspectionIssues())),
                correlationId
            );
        }
        
        metricsService.recordHomeInspectionCompleted(event.getInspectionResult());
    }
    
    private void processTitleSearchCompleted(MortgageEvent event, String correlationId) {
        log.info("Title search completed: mortgageId={}, titleClear={}, liens={}", 
            event.getMortgageId(), event.isTitleClear(), event.getExistingLiens());
        
        Mortgage mortgage = mortgageRepository.findById(event.getMortgageId())
            .orElseThrow();
        
        mortgage.setTitleSearchCompleted(true);
        mortgage.setTitleClear(event.isTitleClear());
        mortgage.setExistingLiens(event.getExistingLiens());
        mortgageRepository.save(mortgage);
        
        if (!event.isTitleClear()) {
            log.error("Title issues found: mortgageId={}, liens={}", 
                event.getMortgageId(), event.getExistingLiens());
            
            notificationService.sendNotification(
                mortgage.getBorrowerId(),
                "Title Search - Issues Found",
                String.format("The title search found issues that must be resolved: %s. " +
                    "These must be cleared before closing.",
                    String.join(", ", event.getExistingLiens())),
                correlationId
            );
            return;
        }
        
        titleInsuranceService.issuePolicy(mortgage.getId());
        
        metricsService.recordTitleSearchCompleted(event.isTitleClear());
    }
    
    private void processMortgageUnderwritingCompleted(MortgageEvent event, String correlationId) {
        log.info("Mortgage underwriting completed: mortgageId={}, decision={}, dtiRatio={}", 
            event.getMortgageId(), event.getUnderwritingDecision(), event.getDtiRatio());
        
        Mortgage mortgage = mortgageRepository.findById(event.getMortgageId())
            .orElseThrow();
        
        mortgage.setUnderwritingCompleted(true);
        mortgage.setUnderwritingDecision(event.getUnderwritingDecision());
        mortgage.setDtiRatio(event.getDtiRatio());
        mortgage.setCreditScore(event.getCreditScore());
        mortgageRepository.save(mortgage);
        
        if (event.getDtiRatio().compareTo(MAX_DTI_RATIO) > 0) {
            log.warn("DTI ratio too high: dti={}%, max={}%", 
                event.getDtiRatio().multiply(new BigDecimal("100")),
                MAX_DTI_RATIO.multiply(new BigDecimal("100")));
        }
        
        if ("APPROVED".equals(event.getUnderwritingDecision())) {
            mortgageService.processApproval(mortgage.getId());
        } else if ("CONDITIONAL".equals(event.getUnderwritingDecision())) {
            notificationService.sendNotification(
                mortgage.getBorrowerId(),
                "Mortgage - Conditional Approval",
                String.format("Your mortgage has conditional approval. Required documents: %s",
                    String.join(", ", event.getRequiredDocuments())),
                correlationId
            );
        }
        
        metricsService.recordMortgageUnderwritingCompleted(
            event.getUnderwritingDecision(), event.getDtiRatio());
    }
    
    private void processMortgageApproved(MortgageEvent event, String correlationId) {
        log.info("Mortgage approved: mortgageId={}, approvedAmount={}, rate={}, monthlyPayment={}", 
            event.getMortgageId(), event.getApprovedAmount(), event.getInterestRate(), event.getMonthlyPayment());
        
        Mortgage mortgage = mortgageRepository.findById(event.getMortgageId())
            .orElseThrow();
        
        mortgage.setApprovedAmount(event.getApprovedAmount());
        mortgage.setInterestRate(event.getInterestRate());
        mortgage.setMonthlyPayment(event.getMonthlyPayment());
        mortgage.setApprovedAt(LocalDateTime.now());
        mortgage.setStatus("APPROVED");
        mortgageRepository.save(mortgage);
        
        BigDecimal totalPayments = event.getMonthlyPayment().multiply(
            new BigDecimal(mortgage.getTermYears() * 12));
        BigDecimal totalInterest = totalPayments.subtract(event.getApprovedAmount());
        
        notificationService.sendNotification(
            mortgage.getBorrowerId(),
            "Mortgage Approved!",
            String.format("Congratulations! Your mortgage has been approved! " +
                "Loan: %s at %s%% APR for %d years. " +
                "Monthly payment: %s (includes principal & interest). " +
                "Total interest over life of loan: %s. " +
                "We'll contact you to schedule closing.",
                event.getApprovedAmount(), event.getInterestRate(), mortgage.getTermYears(),
                event.getMonthlyPayment(), totalInterest),
            correlationId
        );
        
        metricsService.recordMortgageApproved(
            event.getApprovedAmount(), event.getInterestRate(), mortgage.getTermYears());
    }
    
    private void processPmiRequired(MortgageEvent event, String correlationId) {
        log.info("PMI required: mortgageId={}, pmiMonthly={}, ltvRatio={}", 
            event.getMortgageId(), event.getPmiMonthlyPremium(), event.getLtvRatio());
        
        Mortgage mortgage = mortgageRepository.findById(event.getMortgageId())
            .orElseThrow();
        
        mortgage.setRequiresPmi(true);
        mortgage.setPmiMonthlyPremium(event.getPmiMonthlyPremium());
        mortgage.setMonthlyPayment(
            mortgage.getMonthlyPayment().add(event.getPmiMonthlyPremium()));
        mortgageRepository.save(mortgage);
        
        BigDecimal ltvPercentage = event.getLtvRatio().multiply(new BigDecimal("100"));
        BigDecimal targetEquity = new BigDecimal("20");
        
        notificationService.sendNotification(
            mortgage.getBorrowerId(),
            "PMI Required",
            String.format("Since your down payment is less than 20%% (current LTV: %s%%), " +
                "Private Mortgage Insurance (PMI) of %s/month is required. " +
                "PMI will be removed when you reach 20%% equity (80%% LTV) through payments or appreciation.",
                ltvPercentage, event.getPmiMonthlyPremium()),
            correlationId
        );
        
        metricsService.recordPmiRequired(event.getPmiMonthlyPremium(), event.getLtvRatio());
    }
    
    private void processClosingScheduled(MortgageEvent event, String correlationId) {
        log.info("Closing scheduled: mortgageId={}, closingDate={}, location={}", 
            event.getMortgageId(), event.getClosingDate(), event.getClosingLocation());
        
        Mortgage mortgage = mortgageRepository.findById(event.getMortgageId())
            .orElseThrow();
        
        mortgage.setClosingScheduled(true);
        mortgage.setClosingDate(event.getClosingDate());
        mortgage.setClosingLocation(event.getClosingLocation());
        mortgage.setClosingCosts(event.getClosingCosts());
        mortgageRepository.save(mortgage);
        
        notificationService.sendNotification(
            mortgage.getBorrowerId(),
            "Closing Scheduled",
            String.format("Your mortgage closing is scheduled for %s at %s. " +
                "Closing costs: %s. Please bring: Valid ID, cashier's check for closing costs, " +
                "proof of homeowner's insurance. See you soon!",
                event.getClosingDate(), event.getClosingLocation(), event.getClosingCosts()),
            correlationId
        );
        
        metricsService.recordClosingScheduled(event.getClosingDate());
    }
    
    private void processMortgageClosed(MortgageEvent event, String correlationId) {
        log.info("Mortgage closed: mortgageId={}, closedDate={}, finalAmount={}", 
            event.getMortgageId(), event.getClosedDate(), event.getFinalLoanAmount());
        
        Mortgage mortgage = mortgageRepository.findById(event.getMortgageId())
            .orElseThrow();
        
        mortgage.setClosedAt(event.getClosedDate());
        mortgage.setFinalLoanAmount(event.getFinalLoanAmount());
        mortgage.setStatus("ACTIVE");
        mortgage.setFirstPaymentDue(event.getFirstPaymentDue());
        mortgage.setRemainingBalance(event.getFinalLoanAmount());
        mortgageRepository.save(mortgage);
        
        PropertyCollateral property = propertyCollateralRepository.findByMortgageId(event.getMortgageId())
            .orElseThrow();
        
        property.setOwnerId(mortgage.getBorrowerId());
        property.setRecordedAt(LocalDateTime.now());
        propertyCollateralRepository.save(property);
        
        notificationService.sendNotification(
            mortgage.getBorrowerId(),
            "Congratulations - You're a Homeowner!",
            String.format("Your mortgage has closed successfully! Welcome to homeownership! " +
                "Final loan amount: %s at %s%% APR. " +
                "First payment of %s (including escrow) is due on %s. " +
                "You'll receive your payment coupon book in 7-10 days.",
                event.getFinalLoanAmount(), mortgage.getInterestRate(),
                mortgage.getMonthlyPayment(), event.getFirstPaymentDue()),
            correlationId
        );
        
        metricsService.recordMortgageClosed(event.getFinalLoanAmount());
    }
    
    private void processEscrowAccountOpened(MortgageEvent event, String correlationId) {
        log.info("Escrow account opened: mortgageId={}, initialDeposit={}, monthlyEscrow={}", 
            event.getMortgageId(), event.getEscrowInitialDeposit(), event.getMonthlyEscrowPayment());
        
        Mortgage mortgage = mortgageRepository.findById(event.getMortgageId())
            .orElseThrow();
        
        mortgage.setHasEscrowAccount(true);
        mortgage.setEscrowBalance(event.getEscrowInitialDeposit());
        mortgage.setMonthlyEscrowPayment(event.getMonthlyEscrowPayment());
        mortgage.setMonthlyPayment(
            mortgage.getMonthlyPayment().add(event.getMonthlyEscrowPayment()));
        mortgageRepository.save(mortgage);
        
        notificationService.sendNotification(
            mortgage.getBorrowerId(),
            "Escrow Account Opened",
            String.format("Your escrow account has been opened with %s. " +
                "Monthly escrow payment: %s (covers property taxes and homeowner's insurance). " +
                "Total monthly payment: %s",
                event.getEscrowInitialDeposit(), event.getMonthlyEscrowPayment(),
                mortgage.getMonthlyPayment()),
            correlationId
        );
        
        metricsService.recordEscrowAccountOpened(event.getMonthlyEscrowPayment());
    }
    
    private void processPropertyTaxPaid(MortgageEvent event, String correlationId) {
        log.info("Property tax paid from escrow: mortgageId={}, taxAmount={}, taxYear={}", 
            event.getMortgageId(), event.getPropertyTaxAmount(), event.getTaxYear());
        
        Mortgage mortgage = mortgageRepository.findById(event.getMortgageId())
            .orElseThrow();
        
        mortgage.setEscrowBalance(
            mortgage.getEscrowBalance().subtract(event.getPropertyTaxAmount()));
        mortgage.setLastPropertyTaxPaid(LocalDateTime.now());
        mortgageRepository.save(mortgage);
        
        notificationService.sendNotification(
            mortgage.getBorrowerId(),
            "Property Tax Paid",
            String.format("Your property tax of %s for tax year %s has been paid from your escrow account. " +
                "Remaining escrow balance: %s",
                event.getPropertyTaxAmount(), event.getTaxYear(), mortgage.getEscrowBalance()),
            correlationId
        );
        
        metricsService.recordPropertyTaxPaid(event.getPropertyTaxAmount());
    }
    
    private void processMortgageRefinanced(MortgageEvent event, String correlationId) {
        log.info("Mortgage refinanced: oldMortgageId={}, newMortgageId={}, newRate={}, savings={}", 
            event.getMortgageId(), event.getNewMortgageId(), 
            event.getNewInterestRate(), event.getMonthlySavings());
        
        Mortgage oldMortgage = mortgageRepository.findById(event.getMortgageId())
            .orElseThrow();
        
        oldMortgage.setStatus("REFINANCED");
        oldMortgage.setRefinancedAt(LocalDateTime.now());
        oldMortgage.setRefinancedToMortgageId(event.getNewMortgageId());
        mortgageRepository.save(oldMortgage);
        
        Mortgage newMortgage = Mortgage.builder()
            .id(event.getNewMortgageId())
            .borrowerId(oldMortgage.getBorrowerId())
            .loanAmount(event.getNewLoanAmount())
            .propertyAddress(oldMortgage.getPropertyAddress())
            .interestRate(event.getNewInterestRate())
            .monthlyPayment(event.getNewMonthlyPayment())
            .termYears(event.getNewTermYears())
            .mortgageType("REFINANCE")
            .status("ACTIVE")
            .refinancedFromMortgageId(event.getMortgageId())
            .closedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        mortgageRepository.save(newMortgage);
        
        notificationService.sendNotification(
            oldMortgage.getBorrowerId(),
            "Mortgage Refinanced Successfully",
            String.format("Your mortgage refinance is complete! " +
                "New rate: %s%% (was %s%%), New payment: %s (was %s). " +
                "Monthly savings: %s. Lifetime interest savings: %s",
                event.getNewInterestRate(), oldMortgage.getInterestRate(),
                event.getNewMonthlyPayment(), oldMortgage.getMonthlyPayment(),
                event.getMonthlySavings(), event.getLifetimeSavings()),
            correlationId
        );
        
        metricsService.recordMortgageRefinanced(
            event.getNewInterestRate(), oldMortgage.getInterestRate(), event.getMonthlySavings());
    }
}