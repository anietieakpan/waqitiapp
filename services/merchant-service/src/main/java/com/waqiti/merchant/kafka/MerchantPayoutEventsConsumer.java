package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.merchant.service.MerchantPayoutService;
import com.waqiti.merchant.service.PayoutCalculationService;
import com.waqiti.merchant.service.MerchantAccountService;
import com.waqiti.merchant.service.TaxReportingService;
import com.waqiti.merchant.service.AuditService;
import com.waqiti.merchant.entity.MerchantPayout;
import com.waqiti.merchant.entity.PayoutCalculation;
import com.waqiti.merchant.entity.TaxDocument;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

/**
 * Critical Event Consumer #4: Merchant Payout Events Consumer
 * Processes merchant payouts, fee calculations, and tax reporting
 * Implements 12-step zero-tolerance processing for merchant payout management
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantPayoutEventsConsumer extends BaseKafkaConsumer {

    private final MerchantPayoutService merchantPayoutService;
    private final PayoutCalculationService payoutCalculationService;
    private final MerchantAccountService merchantAccountService;
    private final TaxReportingService taxReportingService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "merchant-payout-events", 
        groupId = "merchant-payout-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "merchant-payout-consumer")
    @Retry(name = "merchant-payout-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleMerchantPayoutEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "merchant-payout-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing merchant payout event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String merchantId = eventData.path("merchantId").asText();
            String payoutPeriodId = eventData.path("payoutPeriodId").asText();
            LocalDateTime payoutPeriodStart = LocalDateTime.parse(eventData.path("payoutPeriodStart").asText());
            LocalDateTime payoutPeriodEnd = LocalDateTime.parse(eventData.path("payoutPeriodEnd").asText());
            String payoutSchedule = eventData.path("payoutSchedule").asText(); // DAILY, WEEKLY, MONTHLY
            BigDecimal totalSalesVolume = new BigDecimal(eventData.path("totalSalesVolume").asText());
            String currency = eventData.path("currency").asText();
            int transactionCount = eventData.path("transactionCount").asInt();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted payout details: merchantId={}, period={}, volume={} {}, transactions={}", 
                    merchantId, payoutPeriodId, totalSalesVolume, currency, transactionCount);
            
            // Step 3: Merchant payout eligibility and validation
            log.info("Step 3: Validating merchant payout eligibility and account status");
            MerchantPayout payout = merchantPayoutService.createMerchantPayout(eventData);
            
            merchantPayoutService.validateMerchantEligibility(merchantId);
            merchantPayoutService.validatePayoutSchedule(merchantId, payoutSchedule);
            merchantAccountService.validateAccountStatus(merchantId);
            merchantAccountService.checkAccountSuspension(merchantId);
            
            if (!merchantPayoutService.isPayoutPeriodValid(payoutPeriodStart, payoutPeriodEnd)) {
                throw new IllegalStateException("Invalid payout period configuration");
            }
            
            // Step 4: Transaction aggregation and reconciliation
            log.info("Step 4: Aggregating transactions and performing reconciliation");
            PayoutCalculation calculation = payoutCalculationService.createPayoutCalculation(payout);
            
            List<String> transactionIds = payoutCalculationService.getTransactionIds(merchantId, payoutPeriodStart, payoutPeriodEnd);
            payoutCalculationService.reconcileTransactionVolume(totalSalesVolume, transactionIds);
            payoutCalculationService.validateTransactionCount(transactionCount, transactionIds.size());
            
            BigDecimal grossSales = payoutCalculationService.calculateGrossSales(transactionIds);
            BigDecimal refundAmount = payoutCalculationService.calculateRefunds(transactionIds);
            BigDecimal chargebackAmount = payoutCalculationService.calculateChargebacks(transactionIds);
            
            calculation.setGrossSales(grossSales);
            calculation.setRefunds(refundAmount);
            calculation.setChargebacks(chargebackAmount);
            
            // Step 5: Fee calculation and deduction processing
            log.info("Step 5: Calculating merchant fees and processing deductions");
            BigDecimal processingFees = payoutCalculationService.calculateProcessingFees(merchantId, grossSales, transactionCount);
            BigDecimal monthlyFees = payoutCalculationService.calculateMonthlyFees(merchantId);
            BigDecimal chargebackFees = payoutCalculationService.calculateChargebackFees(merchantId, chargebackAmount);
            BigDecimal reserveAmount = payoutCalculationService.calculateReserveAmount(merchantId, grossSales);
            
            calculation.setProcessingFees(processingFees);
            calculation.setMonthlyFees(monthlyFees);
            calculation.setChargebackFees(chargebackFees);
            calculation.setReserveAmount(reserveAmount);
            
            payoutCalculationService.validateFeeStructure(merchantId, calculation);
            
            // Step 6: Net payout calculation and validation
            log.info("Step 6: Calculating net payout amount and validating calculations");
            BigDecimal totalDeductions = processingFees.add(monthlyFees).add(chargebackFees).add(reserveAmount);
            BigDecimal adjustments = payoutCalculationService.calculateAdjustments(merchantId, payoutPeriodId);
            
            BigDecimal netPayout = grossSales.subtract(refundAmount).subtract(chargebackAmount)
                    .subtract(totalDeductions).add(adjustments);
            
            calculation.setTotalDeductions(totalDeductions);
            calculation.setAdjustments(adjustments);
            calculation.setNetPayoutAmount(netPayout);
            
            payoutCalculationService.validatePayoutAmount(netPayout);
            
            // Step 7: Reserve and hold management
            log.info("Step 7: Managing merchant reserves and transaction holds");
            merchantPayoutService.updateReserveBalance(merchantId, reserveAmount);
            merchantPayoutService.processRollingReserve(merchantId, calculation);
            merchantPayoutService.releaseExpiredHolds(merchantId);
            
            BigDecimal holdAmount = merchantPayoutService.calculateTransactionHolds(merchantId);
            if (holdAmount.compareTo(BigDecimal.ZERO) > 0) {
                merchantPayoutService.applyHoldDeduction(calculation, holdAmount);
            }
            
            merchantPayoutService.updateReserveMetrics(merchantId, calculation);
            
            // Step 8: Tax calculation and reporting obligations
            log.info("Step 8: Processing tax calculations and reporting requirements");
            TaxDocument taxDoc = taxReportingService.createTaxDocument(merchantId, calculation);
            
            taxReportingService.calculate1099KRequirements(merchantId, grossSales, transactionCount);
            taxReportingService.processStateReporting(merchantId, calculation);
            taxReportingService.calculateWithholdingTax(merchantId, calculation);
            
            if (taxReportingService.requires1099KReporting(merchantId, grossSales, transactionCount)) {
                taxReportingService.generate1099KDocument(taxDoc);
            }
            
            taxReportingService.updateAnnualTaxTotals(merchantId, calculation);
            
            // Step 9: Payout method validation and bank account verification
            log.info("Step 9: Validating payout methods and verifying bank account details");
            String payoutMethodId = merchantAccountService.getPreferredPayoutMethod(merchantId);
            merchantPayoutService.validatePayoutMethod(payoutMethodId);
            merchantPayoutService.verifyBankAccountActive(payoutMethodId);
            
            merchantPayoutService.validateBankingDetails(payoutMethodId);
            merchantPayoutService.checkDailyPayoutLimits(merchantId, netPayout);
            
            if (!merchantPayoutService.canProcessPayout(merchantId, netPayout)) {
                merchantPayoutService.schedulePayoutRetry(payout);
                return;
            }
            
            // Step 10: Payout execution and fund transfer
            log.info("Step 10: Executing payout and initiating fund transfer");
            if (netPayout.compareTo(BigDecimal.ZERO) > 0) {
                merchantPayoutService.initiateBankTransfer(payout, payoutMethodId);
                merchantPayoutService.updateMerchantBalance(merchantId, netPayout);
                merchantPayoutService.generatePayoutConfirmation(payout);
                
                payout.setStatus("PROCESSING");
                payout.setPayoutDate(LocalDateTime.now());
            } else {
                merchantPayoutService.handleNegativePayout(payout);
                payout.setStatus("NEGATIVE_BALANCE");
            }
            
            merchantPayoutService.updatePayoutHistory(merchantId, payout);
            
            // Step 11: Notification and merchant communication
            log.info("Step 11: Generating notifications and merchant communications");
            merchantPayoutService.generatePayoutStatement(payout);
            merchantPayoutService.sendPayoutNotification(merchantId, payout);
            merchantPayoutService.updateMerchantDashboard(merchantId, calculation);
            
            if (netPayout.compareTo(BigDecimal.ZERO) <= 0) {
                merchantPayoutService.sendNegativeBalanceAlert(merchantId, payout);
            }
            
            merchantPayoutService.archivePayoutDocuments(payout);
            
            // Step 12: Audit trail and regulatory reporting
            log.info("Step 12: Completing audit trail and regulatory compliance reporting");
            auditService.logMerchantPayout(payout);
            auditService.logPayoutCalculation(calculation);
            auditService.logTaxDocument(taxDoc);
            
            merchantPayoutService.updatePayoutMetrics(payout);
            payoutCalculationService.updateCalculationStatistics(calculation);
            
            auditService.generatePayoutReport(payout);
            auditService.updateRegulatoryReporting(payout);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed merchant payout: merchantId={}, eventId={}, netAmount={} {}", 
                    merchantId, eventId, netPayout, currency);
            
        } catch (Exception e) {
            log.error("Error processing merchant payout event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("merchantId") || 
            !eventData.has("payoutPeriodId") || !eventData.has("payoutPeriodStart") ||
            !eventData.has("payoutPeriodEnd") || !eventData.has("payoutSchedule") ||
            !eventData.has("totalSalesVolume") || !eventData.has("currency") ||
            !eventData.has("transactionCount") || !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid merchant payout event structure");
        }
    }
}