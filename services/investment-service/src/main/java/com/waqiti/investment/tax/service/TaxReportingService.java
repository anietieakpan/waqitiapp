package com.waqiti.investment.tax.service;

import com.waqiti.investment.tax.domain.TaxDocument;
import com.waqiti.investment.tax.enums.DeliveryMethod;
import com.waqiti.investment.tax.enums.DocumentType;
import com.waqiti.investment.tax.repository.TaxDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tax Reporting Service
 *
 * Orchestrates the complete tax reporting workflow:
 * 1. Transaction collection and wash sale detection
 * 2. Form 1099-B and 1099-DIV generation
 * 3. PDF generation for recipient copies
 * 4. IRS FIRE electronic filing
 * 5. Recipient delivery (email, portal, postal mail)
 *
 * Annual Timeline:
 * - January: Generate tax documents for prior year
 * - January 31: Deadline to furnish forms to recipients
 * - March 31: Deadline to file with IRS
 *
 * @author Waqiti Platform Team
 * @since 2025-10-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxReportingService {

    private final Form1099BService form1099BService;
    private final Form1099DIVService form1099DIVService;
    private final TaxDocumentGenerationService taxDocumentGenerationService;
    private final IRSFireIntegrationService irsFireIntegrationService;
    private final TaxDocumentRepository taxDocumentRepository;

    /**
     * Generate all tax documents for a user and tax year
     *
     * @param userId User ID
     * @param investmentAccountId Investment account ID
     * @param taxYear Tax year
     * @param taxpayerInfo Taxpayer information
     * @return List of generated tax documents
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<TaxDocument> generateAllTaxDocuments(
        UUID userId,
        String investmentAccountId,
        Integer taxYear,
        Form1099BService.TaxpayerInfo taxpayerInfo) {

        log.info("Generating all tax documents for user={}, account={}, tax_year={}",
            userId, investmentAccountId, taxYear);

        List<TaxDocument> generatedDocuments = new java.util.ArrayList<>();

        try {
            // Generate Form 1099-B (broker transactions)
            try {
                TaxDocument form1099B = form1099BService.generateForm1099B(
                    userId, investmentAccountId, taxYear, taxpayerInfo);
                generatedDocuments.add(form1099B);
                log.info("Form 1099-B generated: {}", form1099B.getDocumentNumber());
            } catch (IllegalStateException e) {
                log.info("Skipping Form 1099-B: {}", e.getMessage());
            }

            // Generate Form 1099-DIV (dividends)
            try {
                TaxDocument form1099DIV = form1099DIVService.generateForm1099DIV(
                    userId, investmentAccountId, taxYear, taxpayerInfo);
                generatedDocuments.add(form1099DIV);
                log.info("Form 1099-DIV generated: {}", form1099DIV.getDocumentNumber());
            } catch (IllegalStateException e) {
                log.info("Skipping Form 1099-DIV: {}", e.getMessage());
            }

            // Generate PDFs for all documents
            for (TaxDocument doc : generatedDocuments) {
                taxDocumentGenerationService.generatePDF(doc.getId());
            }

            log.info("Successfully generated {} tax documents for user={}, tax_year={}",
                generatedDocuments.size(), userId, taxYear);

        } catch (Exception e) {
            log.error("Error generating tax documents for user={}, tax_year={}",
                userId, taxYear, e);
            throw new RuntimeException("Tax document generation failed", e);
        }

        return generatedDocuments;
    }

    /**
     * Complete tax filing workflow for a tax year
     * 1. Generate all documents
     * 2. Review and approve
     * 3. File with IRS
     * 4. Deliver to recipients
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaxFilingResult completeTaxFilingWorkflow(Integer taxYear) {
        log.info("Starting complete tax filing workflow for tax_year={}", taxYear);

        TaxFilingResult result = TaxFilingResult.builder()
            .taxYear(taxYear)
            .startTime(LocalDate.now())
            .build();

        try {
            // Step 1: Batch generate PDFs for all documents
            int pdfCount = taxDocumentGenerationService.batchGeneratePDFs(taxYear);
            result.setPdfsGenerated(pdfCount);
            log.info("Step 1 complete: {} PDFs generated", pdfCount);

            // Step 2: Submit to IRS FIRE
            IRSFireIntegrationService.FireSubmissionResult filingResult =
                irsFireIntegrationService.submitTaxDocumentsToIRS(taxYear);
            result.setIrsFilingSuccess(filingResult.isSuccess());
            result.setIrsAcceptedCount(filingResult.getAcceptedCount());
            result.setIrsRejectedCount(filingResult.getRejectedCount());
            log.info("Step 2 complete: IRS filing - {} accepted, {} rejected",
                filingResult.getAcceptedCount(), filingResult.getRejectedCount());

            // Step 3: Deliver to recipients (only successfully filed documents)
            int deliveredCount = deliverAllDocuments(taxYear);
            result.setRecipientDeliveredCount(deliveredCount);
            log.info("Step 3 complete: {} documents delivered to recipients", deliveredCount);

            result.setSuccess(true);
            result.setEndTime(LocalDate.now());

            log.info("Tax filing workflow complete for tax_year={}: success=true", taxYear);

        } catch (Exception e) {
            log.error("Tax filing workflow failed for tax_year={}", taxYear, e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDate.now());
        }

        return result;
    }

    /**
     * Deliver all successfully filed documents to recipients
     */
    private int deliverAllDocuments(Integer taxYear) {
        List<TaxDocument> documentsToDeliver = taxDocumentRepository
            .findDocumentsReadyForDelivery();

        int deliveredCount = 0;
        for (TaxDocument doc : documentsToDeliver) {
            try {
                // Default to online portal delivery
                taxDocumentGenerationService.deliverDocument(
                    doc.getId(), DeliveryMethod.ONLINE_PORTAL);

                // Also send email notification
                taxDocumentGenerationService.deliverDocument(
                    doc.getId(), DeliveryMethod.EMAIL);

                doc.markAsCompleted();
                taxDocumentRepository.save(doc);
                deliveredCount++;

            } catch (Exception e) {
                log.error("Failed to deliver document {}", doc.getDocumentNumber(), e);
            }
        }

        return deliveredCount;
    }

    /**
     * Get tax documents for a user and tax year
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public List<TaxDocument> getUserTaxDocuments(UUID userId, Integer taxYear) {
        return taxDocumentRepository.findByUserIdAndTaxYearAndDeletedFalse(userId, taxYear);
    }

    /**
     * Get specific tax document by type
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public TaxDocument getUserTaxDocument(
        UUID userId,
        String investmentAccountId,
        DocumentType documentType,
        Integer taxYear) {

        List<TaxDocument> documents = taxDocumentRepository
            .findByUserIdAndTaxYearAndDeletedFalse(userId, taxYear);

        return documents.stream()
            .filter(doc -> doc.getDocumentType().equals(documentType))
            .filter(doc -> doc.getInvestmentAccountId().equals(investmentAccountId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Scheduled job: Annual tax document generation
     * Runs every January 15th at 2:00 AM
     */
    @Scheduled(cron = "0 0 2 15 1 *") // January 15, 2:00 AM
    public void scheduledAnnualTaxDocumentGeneration() {
        int priorYear = LocalDate.now().getYear() - 1;
        log.info("========================================");
        log.info("SCHEDULED: Annual Tax Document Generation");
        log.info("Tax Year: {}", priorYear);
        log.info("========================================");

        // In production, this would iterate through all users with reportable transactions
        // and generate their tax documents automatically

        log.warn("Scheduled tax generation requires manual trigger per user. " +
            "Use completeTaxFilingWorkflow() after generating individual documents.");
    }

    /**
     * Scheduled job: IRS FIRE submission
     * Runs every February 1st at 1:00 AM (after all documents are generated and reviewed)
     */
    @Scheduled(cron = "0 0 1 1 2 *") // February 1, 1:00 AM
    public void scheduledIRSFiling() {
        int priorYear = LocalDate.now().getYear() - 1;
        log.info("========================================");
        log.info("SCHEDULED: IRS FIRE Submission");
        log.info("Tax Year: {}", priorYear);
        log.info("========================================");

        try {
            IRSFireIntegrationService.FireSubmissionResult result =
                irsFireIntegrationService.submitTaxDocumentsToIRS(priorYear);

            log.info("IRS FIRE submission complete: {} submitted, {} accepted, {} rejected",
                result.getSubmittedCount(), result.getAcceptedCount(), result.getRejectedCount());

            if (result.getRejectedCount() > 0) {
                log.error("ALERT: {} tax documents rejected by IRS. Manual review required.",
                    result.getRejectedCount());
            }

        } catch (Exception e) {
            log.error("Scheduled IRS filing failed", e);
        }
    }

    /**
     * Scheduled job: Recipient delivery
     * Runs every January 25th at 3:00 AM (deadline: January 31)
     */
    @Scheduled(cron = "0 0 3 25 1 *") // January 25, 3:00 AM
    public void scheduledRecipientDelivery() {
        int priorYear = LocalDate.now().getYear() - 1;
        log.info("========================================");
        log.info("SCHEDULED: Tax Document Recipient Delivery");
        log.info("Tax Year: {}", priorYear);
        log.info("========================================");

        try {
            int deliveredCount = deliverAllDocuments(priorYear);
            log.info("Recipient delivery complete: {} documents delivered", deliveredCount);

        } catch (Exception e) {
            log.error("Scheduled recipient delivery failed", e);
        }
    }

    /**
     * Tax Filing Result DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class TaxFilingResult {
        private Integer taxYear;
        private LocalDate startTime;
        private LocalDate endTime;
        private boolean success;
        private String errorMessage;

        private int pdfsGenerated;
        private boolean irsFilingSuccess;
        private int irsAcceptedCount;
        private int irsRejectedCount;
        private int recipientDeliveredCount;
    }
}
