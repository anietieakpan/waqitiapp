package com.waqiti.tax.consumer;

import com.waqiti.common.events.TaxReportingRequiredEvent;
import com.waqiti.tax.service.TaxFormGenerationService;
import com.waqiti.tax.service.IRSReportingService;
import com.waqiti.tax.service.CustomerNotificationService;
import com.waqiti.tax.service.TaxCalculationService;
import com.waqiti.tax.service.ComplianceService;
import com.waqiti.tax.repository.ProcessedEventRepository;
import com.waqiti.tax.repository.TaxReportingRepository;
import com.waqiti.tax.model.ProcessedEvent;
import com.waqiti.tax.model.TaxReporting;
import com.waqiti.tax.model.TaxFormType;
import com.waqiti.tax.model.ReportingStatus;
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
 * Consumer for TaxReportingRequiredEvent - Critical for IRS compliance
 * Handles tax form generation, IRS reporting, backup withholding, and customer notifications
 * ZERO TOLERANCE: All tax reporting must comply with IRS regulations and deadlines
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TaxReportingRequiredEventConsumer {
    
    private final TaxFormGenerationService taxFormGenerationService;
    private final IRSReportingService irsReportingService;
    private final CustomerNotificationService customerNotificationService;
    private final TaxCalculationService taxCalculationService;
    private final ComplianceService complianceService;
    private final ProcessedEventRepository processedEventRepository;
    private final TaxReportingRepository taxReportingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal FORM_1099_THRESHOLD = new BigDecimal("10.00");
    private static final BigDecimal FORM_1099_MISC_THRESHOLD = new BigDecimal("600.00");
    private static final BigDecimal BACKUP_WITHHOLDING_RATE = new BigDecimal("0.24"); // 24%
    private static final BigDecimal LARGE_REPORTING_THRESHOLD = new BigDecimal("50000.00");
    
    @KafkaListener(
        topics = "tax.reporting.required",
        groupId = "tax-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for tax compliance
    public void handleTaxReportingRequired(TaxReportingRequiredEvent event) {
        log.info("Processing tax reporting requirement: {} - Customer: {} - Form: {} - Amount: ${} - Year: {}", 
            event.getReportingId(), event.getCustomerId(), event.getTaxFormType(), 
            event.getReportableAmount(), event.getTaxYear());
        
        // IDEMPOTENCY CHECK - Prevent duplicate tax reporting
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Tax reporting already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // Create tax reporting record
            TaxReporting taxReporting = createTaxReportingRecord(event);
            
            // STEP 1: Validate reporting thresholds and requirements
            validateReportingThresholdsAndRequirements(taxReporting, event);
            
            // STEP 2: Verify customer TIN (Tax Identification Number)
            verifyCustomerTIN(taxReporting, event);
            
            // STEP 3: Calculate reportable amounts and tax implications
            calculateReportableAmountsAndTaxes(taxReporting, event);
            
            // STEP 4: Apply backup withholding if required
            applyBackupWithholdingIfRequired(taxReporting, event);
            
            // STEP 5: Generate required tax forms (1099, 1042-S, etc.)
            generateRequiredTaxForms(taxReporting, event);
            
            // STEP 6: Perform IRS electronic filing (e-filing)
            performIRSElectronicFiling(taxReporting, event);
            
            // STEP 7: Handle state tax reporting requirements
            handleStateTaxReporting(taxReporting, event);
            
            // STEP 8: Generate customer tax documents
            generateCustomerTaxDocuments(taxReporting, event);
            
            // STEP 9: Send required customer notifications
            sendRequiredCustomerNotifications(taxReporting, event);
            
            // STEP 10: Create audit trail for tax compliance
            createTaxComplianceAuditTrail(taxReporting, event);
            
            // STEP 11: Schedule follow-up actions and deadlines
            scheduleFollowupActionsAndDeadlines(taxReporting, event);
            
            // STEP 12: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("TaxReportingRequiredEvent")
                .processedAt(Instant.now())
                .reportingId(event.getReportingId())
                .customerId(event.getCustomerId())
                .taxFormType(event.getTaxFormType())
                .taxYear(event.getTaxYear())
                .reportableAmount(event.getReportableAmount())
                .reportingStatus(taxReporting.getStatus())
                .irsFilingId(taxReporting.getIrsFilingId())
                .backupWithholdingApplied(taxReporting.isBackupWithholdingApplied())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed tax reporting: {} - Status: {}, IRS Filing: {}, Forms: {}", 
                event.getReportingId(), taxReporting.getStatus(), taxReporting.getIrsFilingId(),
                taxReporting.getGeneratedFormIds().size());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process tax reporting requirement: {}", 
                event.getReportingId(), e);
                
            // Create manual intervention record
            createManualInterventionRecord(event, e);
            
            throw new RuntimeException("Tax reporting processing failed", e);
        }
    }
    
    private TaxReporting createTaxReportingRecord(TaxReportingRequiredEvent event) {
        TaxReporting taxReporting = TaxReporting.builder()
            .id(event.getReportingId())
            .customerId(event.getCustomerId())
            .taxFormType(mapTaxFormType(event.getTaxFormType()))
            .taxYear(event.getTaxYear())
            .reportableAmount(event.getReportableAmount())
            .reportingReason(event.getReportingReason())
            .dueDate(event.getDueDate())
            .status(ReportingStatus.INITIATED)
            .createdAt(LocalDateTime.now())
            .generatedFormIds(new ArrayList<>())
            .complianceChecks(new ArrayList<>())
            .build();
        
        return taxReportingRepository.save(taxReporting);
    }
    
    private void validateReportingThresholdsAndRequirements(TaxReporting taxReporting, TaxReportingRequiredEvent event) {
        // Validate reporting thresholds for different form types
        boolean reportingRequired = switch (taxReporting.getTaxFormType()) {
            case FORM_1099_INT -> event.getReportableAmount().compareTo(FORM_1099_THRESHOLD) >= 0;
            case FORM_1099_DIV -> event.getReportableAmount().compareTo(FORM_1099_THRESHOLD) >= 0;
            case FORM_1099_MISC -> event.getReportableAmount().compareTo(FORM_1099_MISC_THRESHOLD) >= 0;
            case FORM_1042_S -> true; // Always required for non-resident aliens
            case FORM_8300 -> event.getReportableAmount().compareTo(new BigDecimal("10000")) > 0;
            case FORM_3520 -> event.getReportableAmount().compareTo(new BigDecimal("100000")) > 0;
            default -> false;
        };
        
        if (!reportingRequired) {
            taxReporting.setStatus(ReportingStatus.NOT_REQUIRED);
            taxReporting.setNotRequiredReason("BELOW_REPORTING_THRESHOLD");
            
            taxReportingRepository.save(taxReporting);
            
            log.info("Tax reporting not required for {}: Amount ${} below threshold", 
                event.getReportingId(), event.getReportableAmount());
            return;
        }
        
        // Check if reporting period is valid
        if (event.getTaxYear() < 2020 || event.getTaxYear() > LocalDateTime.now().getYear()) {
            taxReporting.setStatus(ReportingStatus.INVALID);
            taxReporting.setInvalidReason("INVALID_TAX_YEAR");
            
            taxReportingRepository.save(taxReporting);
            
            log.error("Invalid tax year for reporting {}: {}", event.getReportingId(), event.getTaxYear());
            throw new RuntimeException("Invalid tax year for reporting");
        }
        
        // Validate due date hasn't passed
        if (event.getDueDate().isBefore(LocalDateTime.now())) {
            taxReporting.addComplianceCheck("PAST_DUE_DATE");
            taxReporting.setLateFilingPenalty(true);
            
            // Calculate late filing penalty
            BigDecimal penalty = taxCalculationService.calculateLateFilingPenalty(
                event.getReportableAmount(),
                event.getDueDate(),
                LocalDateTime.now()
            );
            taxReporting.setLateFilingPenaltyAmount(penalty);
        }
        
        taxReportingRepository.save(taxReporting);
        
        log.info("Reporting requirements validated for {}: Required: {}, Tax year: {}", 
            event.getReportingId(), reportingRequired, event.getTaxYear());
    }
    
    private void verifyCustomerTIN(TaxReporting taxReporting, TaxReportingRequiredEvent event) {
        // Verify Tax Identification Number (SSN or EIN)
        String customerTIN = complianceService.getCustomerTIN(event.getCustomerId());
        
        if (customerTIN == null || customerTIN.trim().isEmpty()) {
            taxReporting.setStatus(ReportingStatus.TIN_MISSING);
            taxReporting.setRequiresBackupWithholding(true);
            taxReporting.addComplianceCheck("TIN_MISSING");
            
            log.warn("Missing TIN for customer {} - backup withholding required", event.getCustomerId());
        } else {
            // Validate TIN format and checksum
            boolean tinValid = complianceService.validateTIN(customerTIN);
            
            if (!tinValid) {
                taxReporting.setStatus(ReportingStatus.TIN_INVALID);
                taxReporting.setRequiresBackupWithholding(true);
                taxReporting.addComplianceCheck("TIN_INVALID");
                
                log.warn("Invalid TIN for customer {} - backup withholding required", event.getCustomerId());
            } else {
                taxReporting.setCustomerTIN(customerTIN);
                taxReporting.setTinVerified(true);
            }
        }
        
        // Check IRS TIN matching program results
        if (taxReporting.isTinVerified()) {
            Map<String, Object> tinMatchResult = irsReportingService.checkTINMatching(
                event.getCustomerId(),
                customerTIN,
                event.getCustomerName()
            );
            
            taxReporting.setTinMatchingData(tinMatchResult);
            
            boolean nameMatches = (Boolean) tinMatchResult.get("nameMatches");
            
            if (!nameMatches) {
                taxReporting.setRequiresBackupWithholding(true);
                taxReporting.addComplianceCheck("TIN_NAME_MISMATCH");
                
                log.warn("TIN/Name mismatch for customer {} - backup withholding required", 
                    event.getCustomerId());
            }
        }
        
        taxReportingRepository.save(taxReporting);
        
        log.info("TIN verification completed for {}: Valid: {}, Backup withholding required: {}", 
            event.getReportingId(), taxReporting.isTinVerified(), taxReporting.isRequiresBackupWithholding());
    }
    
    private void calculateReportableAmountsAndTaxes(TaxReporting taxReporting, TaxReportingRequiredEvent event) {
        // Calculate detailed breakdown of reportable amounts
        Map<String, BigDecimal> reportableBreakdown = taxCalculationService.calculateReportableBreakdown(
            event.getCustomerId(),
            event.getTaxYear(),
            event.getTaxFormType(),
            event.getIncomeType()
        );
        
        taxReporting.setReportableBreakdown(reportableBreakdown);
        
        // Calculate total federal withholding
        BigDecimal federalWithholding = taxCalculationService.calculateFederalWithholding(
            event.getCustomerId(),
            event.getTaxYear(),
            reportableBreakdown
        );
        
        taxReporting.setFederalWithholding(federalWithholding);
        
        // Calculate state withholding if applicable
        String customerState = complianceService.getCustomerState(event.getCustomerId());
        
        if (customerState != null && complianceService.hasStateIncomeTax(customerState)) {
            BigDecimal stateWithholding = taxCalculationService.calculateStateWithholding(
                customerState,
                event.getTaxYear(),
                reportableBreakdown
            );
            
            taxReporting.setStateWithholding(stateWithholding);
            taxReporting.setStateWithholdingState(customerState);
        }
        
        // Check for foreign tax implications
        boolean isForeignPerson = complianceService.isForeignPerson(event.getCustomerId());
        
        if (isForeignPerson) {
            taxReporting.setForeignPerson(true);
            
            // Calculate Chapter 3 withholding for non-resident aliens
            BigDecimal chapter3Withholding = taxCalculationService.calculateChapter3Withholding(
                event.getCustomerId(),
                reportableBreakdown,
                event.getIncomeType()
            );
            
            taxReporting.setChapter3Withholding(chapter3Withholding);
            
            // Check for tax treaty benefits
            String treatyCountry = complianceService.getTreatyCountry(event.getCustomerId());
            
            if (treatyCountry != null) {
                BigDecimal treatyBenefits = taxCalculationService.calculateTreatyBenefits(
                    treatyCountry,
                    event.getIncomeType(),
                    reportableBreakdown
                );
                
                taxReporting.setTreatyBenefits(treatyBenefits);
                taxReporting.setTreatyCountry(treatyCountry);
            }
        }
        
        taxReportingRepository.save(taxReporting);
        
        log.info("Tax calculations completed for {}: Reportable: ${}, Federal withholding: ${}, Foreign person: {}", 
            event.getReportingId(), event.getReportableAmount(), federalWithholding, isForeignPerson);
    }
    
    private void applyBackupWithholdingIfRequired(TaxReporting taxReporting, TaxReportingRequiredEvent event) {
        if (!taxReporting.isRequiresBackupWithholding()) {
            return;
        }
        
        // Calculate backup withholding amount (24% rate)
        BigDecimal backupWithholdingAmount = event.getReportableAmount()
            .multiply(BACKUP_WITHHOLDING_RATE);
        
        taxReporting.setBackupWithholdingAmount(backupWithholdingAmount);
        
        // Apply backup withholding to customer account
        String witholdingId = taxCalculationService.applyBackupWithholding(
            event.getCustomerId(),
            backupWithholdingAmount,
            event.getReportingId(),
            taxReporting.getTaxFormType()
        );
        
        taxReporting.setBackupWithholdingId(witholdingId);
        taxReporting.setBackupWithholdingApplied(true);
        taxReporting.setBackupWithholdingAppliedAt(LocalDateTime.now());
        
        // Generate backup withholding notification
        customerNotificationService.sendBackupWithholdingNotification(
            event.getCustomerId(),
            backupWithholdingAmount,
            taxReporting.getTinVerified() ? "TIN_NAME_MISMATCH" : "TIN_MISSING"
        );
        
        taxReportingRepository.save(taxReporting);
        
        log.warn("Backup withholding applied for {}: Amount: ${}, Reason: TIN issues", 
            event.getReportingId(), backupWithholdingAmount);
    }
    
    private void generateRequiredTaxForms(TaxReporting taxReporting, TaxReportingRequiredEvent event) {
        List<String> generatedFormIds = new ArrayList<>();
        
        switch (taxReporting.getTaxFormType()) {
            case FORM_1099_INT -> {
                String form1099IntId = taxFormGenerationService.generate1099INT(
                    event.getCustomerId(),
                    event.getTaxYear(),
                    taxReporting.getReportableBreakdown(),
                    taxReporting.getFederalWithholding()
                );
                generatedFormIds.add(form1099IntId);
                taxReporting.setForm1099IntId(form1099IntId);
            }
            
            case FORM_1099_DIV -> {
                String form1099DivId = taxFormGenerationService.generate1099DIV(
                    event.getCustomerId(),
                    event.getTaxYear(),
                    taxReporting.getReportableBreakdown(),
                    taxReporting.getFederalWithholding()
                );
                generatedFormIds.add(form1099DivId);
                taxReporting.setForm1099DivId(form1099DivId);
            }
            
            case FORM_1099_MISC -> {
                String form1099MiscId = taxFormGenerationService.generate1099MISC(
                    event.getCustomerId(),
                    event.getTaxYear(),
                    taxReporting.getReportableBreakdown(),
                    taxReporting.getFederalWithholding()
                );
                generatedFormIds.add(form1099MiscId);
                taxReporting.setForm1099MiscId(form1099MiscId);
            }
            
            case FORM_1042_S -> {
                String form1042SId = taxFormGenerationService.generate1042S(
                    event.getCustomerId(),
                    event.getTaxYear(),
                    taxReporting.getReportableBreakdown(),
                    taxReporting.getChapter3Withholding(),
                    taxReporting.getTreatyCountry()
                );
                generatedFormIds.add(form1042SId);
                taxReporting.setForm1042SId(form1042SId);
            }
            
            case FORM_8300 -> {
                String form8300Id = taxFormGenerationService.generate8300(
                    event.getCustomerId(),
                    event.getTaxYear(),
                    event.getReportableAmount(),
                    event.getTransactionDetails()
                );
                generatedFormIds.add(form8300Id);
                taxReporting.setForm8300Id(form8300Id);
            }
        }
        
        // Generate backup withholding forms if applicable
        if (taxReporting.isBackupWithholdingApplied()) {
            String backupWithholdingFormId = taxFormGenerationService.generateBackupWithholdingForm(
                event.getCustomerId(),
                event.getTaxYear(),
                taxReporting.getBackupWithholdingAmount()
            );
            generatedFormIds.add(backupWithholdingFormId);
            taxReporting.setBackupWithholdingFormId(backupWithholdingFormId);
        }
        
        taxReporting.setGeneratedFormIds(generatedFormIds);
        taxReporting.setFormsGeneratedAt(LocalDateTime.now());
        
        taxReportingRepository.save(taxReporting);
        
        log.info("Tax forms generated for {}: {} forms", 
            event.getReportingId(), generatedFormIds.size());
    }
    
    private void performIRSElectronicFiling(TaxReporting taxReporting, TaxReportingRequiredEvent event) {
        // Prepare forms for electronic filing
        List<Map<String, Object>> filingData = taxFormGenerationService.prepareForElectronicFiling(
            taxReporting.getGeneratedFormIds(),
            event.getTaxYear()
        );
        
        // Submit to IRS e-filing system
        String irsFilingId = irsReportingService.submitElectronicFiling(
            event.getTaxYear(),
            taxReporting.getTaxFormType(),
            filingData,
            taxReporting.getId()
        );
        
        taxReporting.setIrsFilingId(irsFilingId);
        taxReporting.setIrsFilingSubmittedAt(LocalDateTime.now());
        
        // Check for acknowledgment from IRS
        boolean acknowledgmentReceived = irsReportingService.checkFilingAcknowledgment(
            irsFilingId,
            30 // seconds to wait for acknowledgment
        );
        
        if (acknowledgmentReceived) {
            taxReporting.setIrsFilingStatus("ACCEPTED");
            taxReporting.setStatus(ReportingStatus.FILED_SUCCESSFULLY);
        } else {
            taxReporting.setIrsFilingStatus("PENDING_ACKNOWLEDGMENT");
            taxReporting.setStatus(ReportingStatus.FILING_PENDING);
            
            // Schedule acknowledgment check
            irsReportingService.scheduleAcknowledgmentCheck(
                irsFilingId,
                LocalDateTime.now().plusHours(24)
            );
        }
        
        // Handle rejection scenarios
        if ("REJECTED".equals(taxReporting.getIrsFilingStatus())) {
            List<String> rejectionReasons = irsReportingService.getFilingRejectionReasons(irsFilingId);
            
            taxReporting.setIrsRejectionReasons(rejectionReasons);
            taxReporting.setStatus(ReportingStatus.FILING_REJECTED);
            taxReporting.setRequiresCorrection(true);
        }
        
        taxReportingRepository.save(taxReporting);
        
        log.info("IRS electronic filing completed for {}: Filing ID: {}, Status: {}", 
            event.getReportingId(), irsFilingId, taxReporting.getIrsFilingStatus());
    }
    
    private void handleStateTaxReporting(TaxReporting taxReporting, TaxReportingRequiredEvent event) {
        String customerState = taxReporting.getStateWithholdingState();
        
        if (customerState == null || !complianceService.requiresStateTaxReporting(customerState)) {
            return;
        }
        
        // Generate state tax forms
        String stateTaxFormId = taxFormGenerationService.generateStateTaxForm(
            customerState,
            event.getCustomerId(),
            event.getTaxYear(),
            taxReporting.getReportableBreakdown(),
            taxReporting.getStateWithholding()
        );
        
        taxReporting.setStateTaxFormId(stateTaxFormId);
        
        // Submit to state tax authority
        String stateFilingId = irsReportingService.submitStateTaxFiling(
            customerState,
            event.getTaxYear(),
            stateTaxFormId,
            taxReporting.getId()
        );
        
        taxReporting.setStateFilingId(stateFilingId);
        taxReporting.setStateFilingSubmittedAt(LocalDateTime.now());
        
        taxReportingRepository.save(taxReporting);
        
        log.info("State tax reporting completed for {}: State: {}, Filing ID: {}", 
            event.getReportingId(), customerState, stateFilingId);
    }
    
    private void generateCustomerTaxDocuments(TaxReporting taxReporting, TaxReportingRequiredEvent event) {
        // Generate customer copies of tax documents
        List<String> customerDocumentIds = new ArrayList<>();
        
        for (String formId : taxReporting.getGeneratedFormIds()) {
            String customerCopyId = taxFormGenerationService.generateCustomerCopy(
                formId,
                event.getCustomerId(),
                event.getTaxYear()
            );
            customerDocumentIds.add(customerCopyId);
        }
        
        taxReporting.setCustomerDocumentIds(customerDocumentIds);
        
        // Generate tax summary document
        String taxSummaryId = taxFormGenerationService.generateTaxSummary(
            event.getCustomerId(),
            event.getTaxYear(),
            taxReporting.getReportableBreakdown(),
            taxReporting.getFederalWithholding(),
            taxReporting.getStateWithholding(),
            taxReporting.getBackupWithholdingAmount()
        );
        
        taxReporting.setTaxSummaryId(taxSummaryId);
        
        // Prepare documents for secure delivery
        String secureDocumentPackageId = customerNotificationService.createSecureDocumentPackage(
            event.getCustomerId(),
            customerDocumentIds,
            taxSummaryId,
            event.getTaxYear()
        );
        
        taxReporting.setSecureDocumentPackageId(secureDocumentPackageId);
        
        taxReportingRepository.save(taxReporting);
        
        log.info("Customer tax documents generated for {}: {} documents", 
            event.getReportingId(), customerDocumentIds.size());
    }
    
    private void sendRequiredCustomerNotifications(TaxReporting taxReporting, TaxReportingRequiredEvent event) {
        // Send primary tax document notification
        customerNotificationService.sendTaxDocumentAvailableNotification(
            event.getCustomerId(),
            event.getTaxYear(),
            taxReporting.getTaxFormType(),
            taxReporting.getSecureDocumentPackageId()
        );
        
        // Send email with tax document access
        customerNotificationService.sendTaxDocumentEmail(
            event.getCustomerId(),
            event.getTaxYear(),
            taxReporting.getTaxFormType(),
            taxReporting.getReportableAmount(),
            taxReporting.getSecureDocumentPackageId()
        );
        
        // Send backup withholding notification if applicable
        if (taxReporting.isBackupWithholdingApplied()) {
            customerNotificationService.sendBackupWithholdingAppliedNotification(
                event.getCustomerId(),
                taxReporting.getBackupWithholdingAmount(),
                event.getTaxYear()
            );
        }
        
        // Send correction notice if filing was rejected
        if (taxReporting.isRequiresCorrection()) {
            customerNotificationService.sendFilingCorrectionNotice(
                event.getCustomerId(),
                taxReporting.getIrsRejectionReasons(),
                event.getTaxYear()
            );
        }
        
        // Schedule year-end tax statement reminder
        if (event.getTaxYear() == LocalDateTime.now().getYear()) {
            customerNotificationService.scheduleYearEndTaxReminder(
                event.getCustomerId(),
                LocalDateTime.of(event.getTaxYear() + 1, 1, 15, 9, 0) // January 15th
            );
        }
        
        log.info("Customer notifications sent for tax reporting {}", event.getReportingId());
    }
    
    private void createTaxComplianceAuditTrail(TaxReporting taxReporting, TaxReportingRequiredEvent event) {
        // Create comprehensive audit record
        Map<String, Object> auditData = Map.of(
            "reportingId", taxReporting.getId(),
            "customerId", event.getCustomerId(),
            "taxFormType", taxReporting.getTaxFormType(),
            "taxYear", event.getTaxYear(),
            "reportableAmount", event.getReportableAmount(),
            "federalWithholding", taxReporting.getFederalWithholding(),
            "stateWithholding", taxReporting.getStateWithholding(),
            "backupWithholding", taxReporting.getBackupWithholdingAmount(),
            "irsFilingId", taxReporting.getIrsFilingId(),
            "generatedForms", taxReporting.getGeneratedFormIds(),
            "processedAt", LocalDateTime.now()
        );
        
        String auditId = complianceService.createTaxAuditRecord(
            "TAX_REPORTING_PROCESSED",
            auditData,
            "SYSTEM"
        );
        
        taxReporting.setAuditRecordId(auditId);
        
        // Create IRS audit support documentation
        String irsAuditSupportId = complianceService.createIRSAuditSupport(
            event.getCustomerId(),
            event.getTaxYear(),
            taxReporting.getReportableBreakdown(),
            taxReporting.getGeneratedFormIds()
        );
        
        taxReporting.setIrsAuditSupportId(irsAuditSupportId);
        
        taxReportingRepository.save(taxReporting);
        
        log.info("Tax compliance audit trail created for {}: Audit ID: {}", 
            event.getReportingId(), auditId);
    }
    
    private void scheduleFollowupActionsAndDeadlines(TaxReporting taxReporting, TaxReportingRequiredEvent event) {
        // Schedule IRS acknowledgment follow-up if needed
        if ("PENDING_ACKNOWLEDGMENT".equals(taxReporting.getIrsFilingStatus())) {
            irsReportingService.scheduleAcknowledgmentFollowUp(
                taxReporting.getIrsFilingId(),
                LocalDateTime.now().plusDays(3)
            );
        }
        
        // Schedule correction deadline if filing was rejected
        if (taxReporting.isRequiresCorrection()) {
            LocalDateTime correctionDeadline = event.getDueDate().plusDays(30);
            
            complianceService.scheduleFilingCorrectionDeadline(
                taxReporting.getId(),
                correctionDeadline
            );
        }
        
        // Schedule next year's reporting preparation
        if (event.isRecurringReporting()) {
            LocalDateTime nextYearPrep = LocalDateTime.of(
                event.getTaxYear() + 1, 12, 1, 9, 0
            );
            
            complianceService.scheduleNextYearTaxPreparation(
                event.getCustomerId(),
                nextYearPrep,
                taxReporting.getTaxFormType()
            );
        }
        
        // Schedule document retention period
        LocalDateTime documentRetentionEnd = LocalDateTime.of(
            event.getTaxYear() + 7, 12, 31, 23, 59 // 7 years retention
        );
        
        complianceService.scheduleDocumentRetention(
            taxReporting.getGeneratedFormIds(),
            documentRetentionEnd
        );
        
        log.info("Follow-up actions scheduled for tax reporting {}", event.getReportingId());
    }
    
    private TaxFormType mapTaxFormType(String formTypeStr) {
        try {
            return TaxFormType.valueOf(formTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TaxFormType.FORM_1099_MISC; // Default
        }
    }
    
    private void createManualInterventionRecord(TaxReportingRequiredEvent event, Exception exception) {
        manualInterventionService.createTask(
            "TAX_REPORTING_PROCESSING_FAILED",
            String.format(
                "Failed to process tax reporting requirement. " +
                "Reporting ID: %s, Customer ID: %s, Form: %s, Year: %d, Amount: $%.2f. " +
                "IRS filing may not be completed. Customer not notified. " +
                "Exception: %s. Manual intervention required.",
                event.getReportingId(),
                event.getCustomerId(),
                event.getTaxFormType(),
                event.getTaxYear(),
                event.getReportableAmount(),
                exception.getMessage()
            ),
            "CRITICAL",
            event,
            exception
        );
    }
}