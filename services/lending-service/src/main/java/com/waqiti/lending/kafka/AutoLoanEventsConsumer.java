package com.waqiti.lending.kafka;

import com.waqiti.common.events.AutoLoanEvent;
import com.waqiti.lending.domain.AutoLoan;
import com.waqiti.lending.domain.VehicleCollateral;
import com.waqiti.lending.repository.AutoLoanRepository;
import com.waqiti.lending.repository.VehicleCollateralRepository;
import com.waqiti.lending.service.AutoLoanService;
import com.waqiti.lending.service.VehicleValuationService;
import com.waqiti.lending.service.TitleManagementService;
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
public class AutoLoanEventsConsumer {
    
    private final AutoLoanRepository autoLoanRepository;
    private final VehicleCollateralRepository vehicleCollateralRepository;
    private final AutoLoanService autoLoanService;
    private final VehicleValuationService vehicleValuationService;
    private final TitleManagementService titleManagementService;
    private final LendingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal MAX_LTV_RATIO = new BigDecimal("1.20");
    private static final BigDecimal MAX_AUTO_LOAN_TERM_MONTHS = new BigDecimal("84");
    private static final BigDecimal GAP_INSURANCE_COST = new BigDecimal("500");
    
    @KafkaListener(
        topics = {"auto-loan-events", "vehicle-financing-events", "car-loan-events"},
        groupId = "lending-auto-loan-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
    public void handleAutoLoanEvent(
            @Payload AutoLoanEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("auto-loan-%s-p%d-o%d", 
            event.getLoanId() != null ? event.getLoanId() : event.getBorrowerId(), 
            partition, offset);
        
        log.info("Processing auto loan event: loanId={}, type={}, vehicle={}", 
            event.getLoanId(), event.getEventType(), event.getVehicleMake() + " " + event.getVehicleModel());
        
        try {
            switch (event.getEventType()) {
                case AUTO_LOAN_APPLICATION_SUBMITTED:
                    processAutoLoanApplicationSubmitted(event, correlationId);
                    break;
                case VEHICLE_INSPECTED:
                    processVehicleInspected(event, correlationId);
                    break;
                case VEHICLE_APPRAISED:
                    processVehicleAppraised(event, correlationId);
                    break;
                case AUTO_LOAN_APPROVED:
                    processAutoLoanApproved(event, correlationId);
                    break;
                case VEHICLE_TITLE_RECEIVED:
                    processVehicleTitleReceived(event, correlationId);
                    break;
                case LIEN_RECORDED:
                    processLienRecorded(event, correlationId);
                    break;
                case GAP_INSURANCE_PURCHASED:
                    processGapInsurancePurchased(event, correlationId);
                    break;
                case AUTO_LOAN_FUNDED:
                    processAutoLoanFunded(event, correlationId);
                    break;
                case VEHICLE_DELIVERY_CONFIRMED:
                    processVehicleDeliveryConfirmed(event, correlationId);
                    break;
                case LIEN_RELEASED:
                    processLienReleased(event, correlationId);
                    break;
                case VEHICLE_REPOSSESSED:
                    processVehicleRepossessed(event, correlationId);
                    break;
                default:
                    log.warn("Unknown auto loan event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logLendingEvent(
                "AUTO_LOAN_EVENT_PROCESSED",
                event.getLoanId() != null ? event.getLoanId() : "N/A",
                Map.of(
                    "eventType", event.getEventType(),
                    "borrowerId", event.getBorrowerId() != null ? event.getBorrowerId() : "N/A",
                    "vin", event.getVin() != null ? event.getVin() : "N/A",
                    "vehicleInfo", event.getVehicleYear() + " " + event.getVehicleMake() + " " + event.getVehicleModel(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process auto loan event: {}", e.getMessage(), e);
            kafkaTemplate.send("auto-loan-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processAutoLoanApplicationSubmitted(AutoLoanEvent event, String correlationId) {
        log.info("Auto loan application submitted: borrowerId={}, loanAmount={}, vehicle={} {} {}", 
            event.getBorrowerId(), event.getLoanAmount(), 
            event.getVehicleYear(), event.getVehicleMake(), event.getVehicleModel());
        
        AutoLoan loan = AutoLoan.builder()
            .id(UUID.randomUUID().toString())
            .borrowerId(event.getBorrowerId())
            .loanAmount(event.getLoanAmount())
            .vehicleMake(event.getVehicleMake())
            .vehicleModel(event.getVehicleModel())
            .vehicleYear(event.getVehicleYear())
            .vin(event.getVin())
            .vehicleCondition(event.getVehicleCondition())
            .vehicleMileage(event.getVehicleMileage())
            .downPayment(event.getDownPayment())
            .termMonths(event.getTermMonths())
            .applicationDate(LocalDateTime.now())
            .status("SUBMITTED")
            .correlationId(correlationId)
            .build();
        
        autoLoanRepository.save(loan);
        
        vehicleValuationService.requestAppraisal(loan.getId(), event.getVin());
        
        notificationService.sendNotification(
            event.getBorrowerId(),
            "Auto Loan Application Received",
            String.format("Your auto loan application for a %d %s %s has been received. " +
                "Loan: %s, Down payment: %s, Term: %d months. We're appraising the vehicle.",
                event.getVehicleYear(), event.getVehicleMake(), event.getVehicleModel(),
                event.getLoanAmount(), event.getDownPayment(), event.getTermMonths()),
            correlationId
        );
        
        metricsService.recordAutoLoanApplicationSubmitted(
            event.getVehicleMake(), event.getVehicleYear(), event.getLoanAmount());
    }
    
    private void processVehicleInspected(AutoLoanEvent event, String correlationId) {
        log.info("Vehicle inspected: loanId={}, vin={}, condition={}, issues={}", 
            event.getLoanId(), event.getVin(), event.getInspectionResult(), event.getInspectionIssues());
        
        AutoLoan loan = autoLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setVehicleInspected(true);
        loan.setInspectionDate(LocalDateTime.now());
        loan.setInspectionResult(event.getInspectionResult());
        loan.setInspectionIssues(event.getInspectionIssues());
        autoLoanRepository.save(loan);
        
        if ("FAILED".equals(event.getInspectionResult())) {
            log.error("Vehicle inspection failed: loanId={}, issues={}", 
                event.getLoanId(), event.getInspectionIssues());
            
            autoLoanService.rejectLoan(loan.getId(), "INSPECTION_FAILED");
            return;
        }
        
        metricsService.recordVehicleInspected(event.getInspectionResult());
    }
    
    private void processVehicleAppraised(AutoLoanEvent event, String correlationId) {
        log.info("Vehicle appraised: loanId={}, appraisedValue={}, marketValue={}, ltvRatio={}", 
            event.getLoanId(), event.getAppraisedValue(), event.getMarketValue(), event.getLtvRatio());
        
        AutoLoan loan = autoLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        VehicleCollateral collateral = VehicleCollateral.builder()
            .id(UUID.randomUUID().toString())
            .loanId(event.getLoanId())
            .vin(event.getVin())
            .make(loan.getVehicleMake())
            .model(loan.getVehicleModel())
            .year(loan.getVehicleYear())
            .appraisedValue(event.getAppraisedValue())
            .marketValue(event.getMarketValue())
            .appraisalDate(LocalDateTime.now())
            .appraisalSource(event.getAppraisalSource())
            .build();
        
        vehicleCollateralRepository.save(collateral);
        
        loan.setAppraisedValue(event.getAppraisedValue());
        loan.setMarketValue(event.getMarketValue());
        loan.setLtvRatio(event.getLtvRatio());
        autoLoanRepository.save(loan);
        
        if (event.getLtvRatio().compareTo(MAX_LTV_RATIO) > 0) {
            log.warn("LTV ratio too high: loanId={}, ltv={}%, max={}%", 
                event.getLoanId(), event.getLtvRatio().multiply(new BigDecimal("100")), 
                MAX_LTV_RATIO.multiply(new BigDecimal("100")));
            
            BigDecimal maxLoanAmount = event.getAppraisedValue().multiply(MAX_LTV_RATIO);
            autoLoanService.counterOffer(loan.getId(), maxLoanAmount);
            return;
        }
        
        autoLoanService.processLoanApproval(loan.getId());
        
        metricsService.recordVehicleAppraised(event.getAppraisedValue(), event.getLtvRatio());
    }
    
    private void processAutoLoanApproved(AutoLoanEvent event, String correlationId) {
        log.info("Auto loan approved: loanId={}, approvedAmount={}, rate={}, monthlyPayment={}", 
            event.getLoanId(), event.getApprovedAmount(), event.getInterestRate(), event.getMonthlyPayment());
        
        AutoLoan loan = autoLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setApprovedAmount(event.getApprovedAmount());
        loan.setInterestRate(event.getInterestRate());
        loan.setMonthlyPayment(event.getMonthlyPayment());
        loan.setApprovedAt(LocalDateTime.now());
        loan.setStatus("APPROVED");
        autoLoanRepository.save(loan);
        
        notificationService.sendNotification(
            loan.getBorrowerId(),
            "Auto Loan Approved",
            String.format("Your auto loan for the %d %s %s has been approved! " +
                "Loan: %s at %s%% APR, Monthly payment: %s for %d months. " +
                "Next step: Submit vehicle title for lien processing.",
                loan.getVehicleYear(), loan.getVehicleMake(), loan.getVehicleModel(),
                event.getApprovedAmount(), event.getInterestRate(), 
                event.getMonthlyPayment(), loan.getTermMonths()),
            correlationId
        );
        
        metricsService.recordAutoLoanApproved(event.getApprovedAmount(), event.getInterestRate());
    }
    
    private void processVehicleTitleReceived(AutoLoanEvent event, String correlationId) {
        log.info("Vehicle title received: loanId={}, titleNumber={}, state={}", 
            event.getLoanId(), event.getTitleNumber(), event.getTitleState());
        
        AutoLoan loan = autoLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setTitleNumber(event.getTitleNumber());
        loan.setTitleState(event.getTitleState());
        loan.setTitleReceivedAt(LocalDateTime.now());
        autoLoanRepository.save(loan);
        
        VehicleCollateral collateral = vehicleCollateralRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        collateral.setTitleNumber(event.getTitleNumber());
        collateral.setTitleState(event.getTitleState());
        vehicleCollateralRepository.save(collateral);
        
        titleManagementService.recordLien(loan.getId(), event.getTitleNumber(), event.getTitleState());
        
        metricsService.recordVehicleTitleReceived(event.getTitleState());
    }
    
    private void processLienRecorded(AutoLoanEvent event, String correlationId) {
        log.info("Lien recorded: loanId={}, lienHolder={}, recordedDate={}", 
            event.getLoanId(), event.getLienHolder(), event.getLienRecordedDate());
        
        AutoLoan loan = autoLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setLienRecorded(true);
        loan.setLienHolder(event.getLienHolder());
        loan.setLienRecordedDate(event.getLienRecordedDate());
        autoLoanRepository.save(loan);
        
        VehicleCollateral collateral = vehicleCollateralRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        collateral.setLienRecorded(true);
        collateral.setLienHolder(event.getLienHolder());
        vehicleCollateralRepository.save(collateral);
        
        autoLoanService.proceedToFunding(loan.getId());
        
        metricsService.recordLienRecorded();
    }
    
    private void processGapInsurancePurchased(AutoLoanEvent event, String correlationId) {
        log.info("GAP insurance purchased: loanId={}, premium={}, provider={}", 
            event.getLoanId(), event.getGapInsurancePremium(), event.getGapInsuranceProvider());
        
        AutoLoan loan = autoLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setHasGapInsurance(true);
        loan.setGapInsurancePremium(event.getGapInsurancePremium());
        loan.setGapInsuranceProvider(event.getGapInsuranceProvider());
        loan.setGapInsurancePurchasedAt(LocalDateTime.now());
        autoLoanRepository.save(loan);
        
        notificationService.sendNotification(
            loan.getBorrowerId(),
            "GAP Insurance Added",
            String.format("GAP insurance has been added to your auto loan for %s. " +
                "This protects you if the vehicle is totaled and you owe more than its value.",
                event.getGapInsurancePremium()),
            correlationId
        );
        
        metricsService.recordGapInsurancePurchased(event.getGapInsurancePremium());
    }
    
    private void processAutoLoanFunded(AutoLoanEvent event, String correlationId) {
        log.info("Auto loan funded: loanId={}, fundedAmount={}, payee={}", 
            event.getLoanId(), event.getFundedAmount(), event.getPayee());
        
        AutoLoan loan = autoLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setFundedAmount(event.getFundedAmount());
        loan.setFundedAt(LocalDateTime.now());
        loan.setPayee(event.getPayee());
        loan.setStatus("FUNDED");
        loan.setFirstPaymentDue(LocalDateTime.now().plusMonths(1));
        loan.setRemainingBalance(event.getFundedAmount());
        autoLoanRepository.save(loan);
        
        notificationService.sendNotification(
            loan.getBorrowerId(),
            "Auto Loan Funded",
            String.format("Your auto loan of %s has been funded and paid to %s. " +
                "First payment of %s is due on %s. Enjoy your %d %s %s!",
                event.getFundedAmount(), event.getPayee(),
                loan.getMonthlyPayment(), loan.getFirstPaymentDue().toLocalDate(),
                loan.getVehicleYear(), loan.getVehicleMake(), loan.getVehicleModel()),
            correlationId
        );
        
        metricsService.recordAutoLoanFunded(event.getFundedAmount());
    }
    
    private void processVehicleDeliveryConfirmed(AutoLoanEvent event, String correlationId) {
        log.info("Vehicle delivery confirmed: loanId={}, deliveredAt={}, odometerReading={}", 
            event.getLoanId(), event.getDeliveryDate(), event.getDeliveryOdometerReading());
        
        AutoLoan loan = autoLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setVehicleDelivered(true);
        loan.setDeliveryDate(event.getDeliveryDate());
        loan.setDeliveryOdometerReading(event.getDeliveryOdometerReading());
        loan.setStatus("ACTIVE");
        autoLoanRepository.save(loan);
        
        VehicleCollateral collateral = vehicleCollateralRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        collateral.setCurrentMileage(event.getDeliveryOdometerReading());
        vehicleCollateralRepository.save(collateral);
        
        metricsService.recordVehicleDeliveryConfirmed();
    }
    
    private void processLienReleased(AutoLoanEvent event, String correlationId) {
        log.info("Lien released: loanId={}, releasedDate={}, titleSentTo={}", 
            event.getLoanId(), event.getLienReleaseDate(), event.getTitleRecipient());
        
        AutoLoan loan = autoLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setLienReleased(true);
        loan.setLienReleaseDate(event.getLienReleaseDate());
        loan.setStatus("PAID_OFF");
        loan.setRemainingBalance(BigDecimal.ZERO);
        autoLoanRepository.save(loan);
        
        VehicleCollateral collateral = vehicleCollateralRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        collateral.setLienReleased(true);
        collateral.setLienReleaseDate(event.getLienReleaseDate());
        vehicleCollateralRepository.save(collateral);
        
        notificationService.sendNotification(
            loan.getBorrowerId(),
            "Congratulations - Auto Loan Paid Off!",
            String.format("You've paid off your auto loan for the %d %s %s! " +
                "The lien has been released and the vehicle title will be mailed to you within 7-10 business days. " +
                "You now own the vehicle free and clear!",
                loan.getVehicleYear(), loan.getVehicleMake(), loan.getVehicleModel()),
            correlationId
        );
        
        metricsService.recordLienReleased();
    }
    
    private void processVehicleRepossessed(AutoLoanEvent event, String correlationId) {
        log.error("Vehicle repossessed: loanId={}, repossessionDate={}, outstandingBalance={}, daysPastDue={}", 
            event.getLoanId(), event.getRepossessionDate(), event.getOutstandingBalance(), event.getDaysPastDue());
        
        AutoLoan loan = autoLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setVehicleRepossessed(true);
        loan.setRepossessionDate(event.getRepossessionDate());
        loan.setStatus("REPOSSESSED");
        loan.setDaysPastDue(event.getDaysPastDue());
        autoLoanRepository.save(loan);
        
        VehicleCollateral collateral = vehicleCollateralRepository.findByLoanId(event.getLoanId())
            .orElseThrow();
        
        collateral.setRepossessed(true);
        collateral.setRepossessionDate(event.getRepossessionDate());
        vehicleCollateralRepository.save(collateral);
        
        autoLoanService.initiateVehicleAuction(loan.getId());
        
        notificationService.sendNotification(
            loan.getBorrowerId(),
            "Vehicle Repossession Notice",
            String.format("Your %d %s %s has been repossessed due to non-payment (%d days past due). " +
                "Outstanding balance: %s. The vehicle will be sold at auction. " +
                "Contact us immediately to discuss options.",
                loan.getVehicleYear(), loan.getVehicleMake(), loan.getVehicleModel(),
                event.getDaysPastDue(), event.getOutstandingBalance()),
            correlationId
        );
        
        metricsService.recordVehicleRepossessed(event.getOutstandingBalance(), event.getDaysPastDue());
    }
}