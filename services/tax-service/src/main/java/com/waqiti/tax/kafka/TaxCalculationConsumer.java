package com.waqiti.tax.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.tax.service.TaxCalculationService;
import com.waqiti.tax.service.TaxReportingService;
import com.waqiti.tax.service.TaxNotificationService;
import com.waqiti.common.audit.AuditService;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class TaxCalculationConsumer {
    
    private final TaxCalculationService taxCalculationService;
    private final TaxReportingService taxReportingService;
    private final TaxNotificationService taxNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"tax-calculation-events", "tax-calculations"},
        groupId = "tax-service-calculation-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleTaxCalculation(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("TAX CALCULATION: Processing tax calculation - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID calculationId = null;
        UUID userId = null;
        String calculationType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            calculationId = UUID.fromString((String) event.get("calculationId"));
            userId = UUID.fromString((String) event.get("userId"));
            calculationType = (String) event.get("calculationType");
            String calculationStatus = (String) event.get("calculationStatus");
            Integer taxYear = (Integer) event.get("taxYear");
            String taxJurisdiction = (String) event.get("taxJurisdiction");
            String taxType = (String) event.get("taxType");
            BigDecimal grossAmount = new BigDecimal(event.get("grossAmount").toString());
            BigDecimal taxableAmount = new BigDecimal(event.get("taxableAmount").toString());
            BigDecimal taxAmount = new BigDecimal(event.get("taxAmount").toString());
            BigDecimal taxRate = new BigDecimal(event.get("taxRate").toString());
            String currency = (String) event.get("currency");
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            String transactionType = (String) event.get("transactionType");
            LocalDateTime calculationTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            Boolean requiresReporting = (Boolean) event.getOrDefault("requiresReporting", false);
            Boolean requiresWithholding = (Boolean) event.getOrDefault("requiresWithholding", false);
            String taxIdNumber = (String) event.get("taxIdNumber");
            
            log.info("Tax calculation - CalculationId: {}, UserId: {}, Type: {}, Status: {}, Year: {}, Jurisdiction: {}, Gross: {} {}, Tax: {} {}, Rate: {}%", 
                    calculationId, userId, calculationType, calculationStatus, taxYear, taxJurisdiction, 
                    grossAmount, currency, taxAmount, currency, taxRate.multiply(new BigDecimal("100")));
            
            validateTaxCalculation(calculationId, userId, calculationType, calculationStatus, 
                    taxYear, taxJurisdiction, grossAmount, taxAmount);
            
            processCalculationByType(calculationId, userId, calculationType, calculationStatus, 
                    taxYear, taxJurisdiction, taxType, grossAmount, taxableAmount, taxAmount, 
                    taxRate, currency, transactionId, transactionType, calculationTimestamp, 
                    requiresReporting, requiresWithholding, taxIdNumber);
            
            if ("CALCULATED".equals(calculationStatus)) {
                handleCalculatedTax(calculationId, userId, calculationType, taxYear, taxJurisdiction, 
                        taxType, taxableAmount, taxAmount, taxRate, currency, transactionId, 
                        requiresWithholding);
            } else if ("ADJUSTED".equals(calculationStatus)) {
                handleAdjustedTax(calculationId, userId, calculationType, taxYear, taxAmount, 
                        currency);
            }
            
            if (requiresReporting) {
                recordForTaxReporting(calculationId, userId, taxYear, taxJurisdiction, taxType, 
                        grossAmount, taxableAmount, taxAmount, currency, transactionId, taxIdNumber);
            }
            
            if (requiresWithholding) {
                processWithholding(calculationId, userId, taxAmount, currency, transactionId, 
                        taxJurisdiction);
            }
            
            notifyUser(userId, calculationId, calculationType, calculationStatus, taxAmount, 
                    currency, taxYear);
            
            updateTaxMetrics(calculationType, calculationStatus, taxType, taxJurisdiction, 
                    taxAmount, taxYear);
            
            auditTaxCalculation(calculationId, userId, calculationType, calculationStatus, taxYear, 
                    taxJurisdiction, grossAmount, taxAmount, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Tax calculation processed - CalculationId: {}, Type: {}, Status: {}, ProcessingTime: {}ms", 
                    calculationId, calculationType, calculationStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Tax calculation processing failed - CalculationId: {}, UserId: {}, Type: {}, Error: {}", 
                    calculationId, userId, calculationType, e.getMessage(), e);
            
            if (calculationId != null && userId != null) {
                handleCalculationFailure(calculationId, userId, calculationType, e);
            }
            
            throw new RuntimeException("Tax calculation processing failed", e);
        }
    }
    
    private void validateTaxCalculation(UUID calculationId, UUID userId, String calculationType,
                                       String calculationStatus, Integer taxYear, String taxJurisdiction,
                                       BigDecimal grossAmount, BigDecimal taxAmount) {
        if (calculationId == null || userId == null) {
            throw new IllegalArgumentException("Calculation ID and User ID are required");
        }
        
        if (calculationType == null || calculationType.trim().isEmpty()) {
            throw new IllegalArgumentException("Calculation type is required");
        }
        
        if (calculationStatus == null || calculationStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Calculation status is required");
        }
        
        if (taxYear == null || taxYear < 2000 || taxYear > 2100) {
            throw new IllegalArgumentException("Invalid tax year");
        }
        
        if (taxJurisdiction == null || taxJurisdiction.trim().isEmpty()) {
            throw new IllegalArgumentException("Tax jurisdiction is required");
        }
        
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid gross amount");
        }
        
        if (taxAmount == null || taxAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid tax amount");
        }
        
        log.debug("Tax calculation validation passed - CalculationId: {}", calculationId);
    }
    
    private void processCalculationByType(UUID calculationId, UUID userId, String calculationType,
                                         String calculationStatus, Integer taxYear, String taxJurisdiction,
                                         String taxType, BigDecimal grossAmount, BigDecimal taxableAmount,
                                         BigDecimal taxAmount, BigDecimal taxRate, String currency,
                                         UUID transactionId, String transactionType,
                                         LocalDateTime calculationTimestamp, Boolean requiresReporting,
                                         Boolean requiresWithholding, String taxIdNumber) {
        try {
            switch (calculationType) {
                case "INCOME_TAX" -> processIncomeTax(calculationId, userId, taxYear, taxJurisdiction, 
                        taxableAmount, taxAmount, taxRate, currency, taxIdNumber);
                
                case "CAPITAL_GAINS_TAX" -> processCapitalGainsTax(calculationId, userId, taxYear, 
                        taxJurisdiction, grossAmount, taxableAmount, taxAmount, taxRate, currency, 
                        transactionId);
                
                case "WITHHOLDING_TAX" -> processWithholdingTax(calculationId, userId, taxYear, 
                        taxJurisdiction, grossAmount, taxAmount, taxRate, currency, transactionId);
                
                case "SALES_TAX" -> processSalesTax(calculationId, userId, taxJurisdiction, 
                        grossAmount, taxAmount, taxRate, currency, transactionId);
                
                case "VAT" -> processVAT(calculationId, userId, taxJurisdiction, grossAmount, 
                        taxAmount, taxRate, currency, transactionId);
                
                case "CRYPTO_TAX" -> processCryptoTax(calculationId, userId, taxYear, taxJurisdiction, 
                        grossAmount, taxableAmount, taxAmount, taxRate, currency, transactionId);
                
                case "DIVIDEND_TAX" -> processDividendTax(calculationId, userId, taxYear, 
                        taxJurisdiction, grossAmount, taxAmount, taxRate, currency, transactionId);
                
                case "INTEREST_TAX" -> processInterestTax(calculationId, userId, taxYear, 
                        taxJurisdiction, grossAmount, taxAmount, taxRate, currency, transactionId);
                
                default -> {
                    log.warn("Unknown tax calculation type: {}", calculationType);
                    processGenericTaxCalculation(calculationId, userId, calculationType);
                }
            }
            
            log.debug("Calculation type processing completed - CalculationId: {}, Type: {}", 
                    calculationId, calculationType);
            
        } catch (Exception e) {
            log.error("Failed to process calculation by type - CalculationId: {}, Type: {}", 
                    calculationId, calculationType, e);
            throw new RuntimeException("Calculation type processing failed", e);
        }
    }
    
    private void processIncomeTax(UUID calculationId, UUID userId, Integer taxYear, String taxJurisdiction,
                                 BigDecimal taxableAmount, BigDecimal taxAmount, BigDecimal taxRate,
                                 String currency, String taxIdNumber) {
        log.info("Processing INCOME TAX calculation - CalculationId: {}, Year: {}, Jurisdiction: {}, Taxable: {} {}, Tax: {} {}", 
                calculationId, taxYear, taxJurisdiction, taxableAmount, currency, taxAmount, currency);
        
        taxCalculationService.processIncomeTax(calculationId, userId, taxYear, taxJurisdiction, 
                taxableAmount, taxAmount, taxRate, currency, taxIdNumber);
    }
    
    private void processCapitalGainsTax(UUID calculationId, UUID userId, Integer taxYear,
                                       String taxJurisdiction, BigDecimal grossAmount,
                                       BigDecimal taxableAmount, BigDecimal taxAmount,
                                       BigDecimal taxRate, String currency, UUID transactionId) {
        log.info("Processing CAPITAL GAINS TAX calculation - CalculationId: {}, Year: {}, Gain: {} {}, Tax: {} {}", 
                calculationId, taxYear, taxableAmount, currency, taxAmount, currency);
        
        taxCalculationService.processCapitalGainsTax(calculationId, userId, taxYear, taxJurisdiction, 
                grossAmount, taxableAmount, taxAmount, taxRate, currency, transactionId);
    }
    
    private void processWithholdingTax(UUID calculationId, UUID userId, Integer taxYear,
                                      String taxJurisdiction, BigDecimal grossAmount,
                                      BigDecimal taxAmount, BigDecimal taxRate, String currency,
                                      UUID transactionId) {
        log.info("Processing WITHHOLDING TAX calculation - CalculationId: {}, Year: {}, Gross: {} {}, Tax: {} {}", 
                calculationId, taxYear, grossAmount, currency, taxAmount, currency);
        
        taxCalculationService.processWithholdingTax(calculationId, userId, taxYear, taxJurisdiction, 
                grossAmount, taxAmount, taxRate, currency, transactionId);
    }
    
    private void processSalesTax(UUID calculationId, UUID userId, String taxJurisdiction,
                                BigDecimal grossAmount, BigDecimal taxAmount, BigDecimal taxRate,
                                String currency, UUID transactionId) {
        log.info("Processing SALES TAX calculation - CalculationId: {}, Jurisdiction: {}, Amount: {} {}, Tax: {} {}", 
                calculationId, taxJurisdiction, grossAmount, currency, taxAmount, currency);
        
        taxCalculationService.processSalesTax(calculationId, userId, taxJurisdiction, grossAmount, 
                taxAmount, taxRate, currency, transactionId);
    }
    
    private void processVAT(UUID calculationId, UUID userId, String taxJurisdiction,
                           BigDecimal grossAmount, BigDecimal taxAmount, BigDecimal taxRate,
                           String currency, UUID transactionId) {
        log.info("Processing VAT calculation - CalculationId: {}, Jurisdiction: {}, Amount: {} {}, VAT: {} {}", 
                calculationId, taxJurisdiction, grossAmount, currency, taxAmount, currency);
        
        taxCalculationService.processVAT(calculationId, userId, taxJurisdiction, grossAmount, 
                taxAmount, taxRate, currency, transactionId);
    }
    
    private void processCryptoTax(UUID calculationId, UUID userId, Integer taxYear,
                                 String taxJurisdiction, BigDecimal grossAmount, BigDecimal taxableAmount,
                                 BigDecimal taxAmount, BigDecimal taxRate, String currency,
                                 UUID transactionId) {
        log.info("Processing CRYPTO TAX calculation - CalculationId: {}, Year: {}, Gain: {} {}, Tax: {} {}", 
                calculationId, taxYear, taxableAmount, currency, taxAmount, currency);
        
        taxCalculationService.processCryptoTax(calculationId, userId, taxYear, taxJurisdiction, 
                grossAmount, taxableAmount, taxAmount, taxRate, currency, transactionId);
    }
    
    private void processDividendTax(UUID calculationId, UUID userId, Integer taxYear,
                                   String taxJurisdiction, BigDecimal grossAmount, BigDecimal taxAmount,
                                   BigDecimal taxRate, String currency, UUID transactionId) {
        log.info("Processing DIVIDEND TAX calculation - CalculationId: {}, Year: {}, Dividend: {} {}, Tax: {} {}", 
                calculationId, taxYear, grossAmount, currency, taxAmount, currency);
        
        taxCalculationService.processDividendTax(calculationId, userId, taxYear, taxJurisdiction, 
                grossAmount, taxAmount, taxRate, currency, transactionId);
    }
    
    private void processInterestTax(UUID calculationId, UUID userId, Integer taxYear,
                                   String taxJurisdiction, BigDecimal grossAmount, BigDecimal taxAmount,
                                   BigDecimal taxRate, String currency, UUID transactionId) {
        log.info("Processing INTEREST TAX calculation - CalculationId: {}, Year: {}, Interest: {} {}, Tax: {} {}", 
                calculationId, taxYear, grossAmount, currency, taxAmount, currency);
        
        taxCalculationService.processInterestTax(calculationId, userId, taxYear, taxJurisdiction, 
                grossAmount, taxAmount, taxRate, currency, transactionId);
    }
    
    private void processGenericTaxCalculation(UUID calculationId, UUID userId, String calculationType) {
        log.info("Processing generic tax calculation - CalculationId: {}, Type: {}", 
                calculationId, calculationType);
        
        taxCalculationService.processGeneric(calculationId, userId, calculationType);
    }
    
    private void handleCalculatedTax(UUID calculationId, UUID userId, String calculationType,
                                    Integer taxYear, String taxJurisdiction, String taxType,
                                    BigDecimal taxableAmount, BigDecimal taxAmount, BigDecimal taxRate,
                                    String currency, UUID transactionId, Boolean requiresWithholding) {
        try {
            log.info("Processing calculated tax - CalculationId: {}, Type: {}, Tax: {} {}", 
                    calculationId, calculationType, taxAmount, currency);
            
            taxCalculationService.recordCalculatedTax(calculationId, userId, calculationType, 
                    taxYear, taxJurisdiction, taxType, taxableAmount, taxAmount, taxRate, currency, 
                    transactionId);
            
        } catch (Exception e) {
            log.error("Failed to handle calculated tax - CalculationId: {}", calculationId, e);
        }
    }
    
    private void handleAdjustedTax(UUID calculationId, UUID userId, String calculationType,
                                  Integer taxYear, BigDecimal taxAmount, String currency) {
        try {
            log.info("Processing adjusted tax - CalculationId: {}, Type: {}, Tax: {} {}", 
                    calculationId, calculationType, taxAmount, currency);
            
            taxCalculationService.recordAdjustedTax(calculationId, userId, calculationType, taxYear, 
                    taxAmount, currency);
            
        } catch (Exception e) {
            log.error("Failed to handle adjusted tax - CalculationId: {}", calculationId, e);
        }
    }
    
    private void recordForTaxReporting(UUID calculationId, UUID userId, Integer taxYear,
                                      String taxJurisdiction, String taxType, BigDecimal grossAmount,
                                      BigDecimal taxableAmount, BigDecimal taxAmount, String currency,
                                      UUID transactionId, String taxIdNumber) {
        try {
            log.info("Recording for tax reporting - CalculationId: {}, Year: {}, Jurisdiction: {}, Tax: {} {}", 
                    calculationId, taxYear, taxJurisdiction, taxAmount, currency);
            
            taxReportingService.recordTaxableEvent(calculationId, userId, taxYear, taxJurisdiction, 
                    taxType, grossAmount, taxableAmount, taxAmount, currency, transactionId, taxIdNumber);
            
        } catch (Exception e) {
            log.error("Failed to record for tax reporting - CalculationId: {}", calculationId, e);
        }
    }
    
    private void processWithholding(UUID calculationId, UUID userId, BigDecimal taxAmount,
                                   String currency, UUID transactionId, String taxJurisdiction) {
        try {
            log.info("Processing tax withholding - CalculationId: {}, Amount: {} {}, TransactionId: {}", 
                    calculationId, taxAmount, currency, transactionId);
            
            taxCalculationService.processWithholding(calculationId, userId, taxAmount, currency, 
                    transactionId, taxJurisdiction);
            
        } catch (Exception e) {
            log.error("Failed to process withholding - CalculationId: {}", calculationId, e);
        }
    }
    
    private void notifyUser(UUID userId, UUID calculationId, String calculationType,
                           String calculationStatus, BigDecimal taxAmount, String currency,
                           Integer taxYear) {
        try {
            taxNotificationService.sendTaxCalculationNotification(userId, calculationId, 
                    calculationType, calculationStatus, taxAmount, currency, taxYear);
            
            log.info("User notified of tax calculation - UserId: {}, CalculationId: {}, Type: {}", 
                    userId, calculationId, calculationType);
            
        } catch (Exception e) {
            log.error("Failed to notify user - UserId: {}, CalculationId: {}", userId, calculationId, e);
        }
    }
    
    private void updateTaxMetrics(String calculationType, String calculationStatus, String taxType,
                                 String taxJurisdiction, BigDecimal taxAmount, Integer taxYear) {
        try {
            taxCalculationService.updateCalculationMetrics(calculationType, calculationStatus, 
                    taxType, taxJurisdiction, taxAmount, taxYear);
        } catch (Exception e) {
            log.error("Failed to update tax metrics - Type: {}, Status: {}", calculationType, 
                    calculationStatus, e);
        }
    }
    
    private void auditTaxCalculation(UUID calculationId, UUID userId, String calculationType,
                                    String calculationStatus, Integer taxYear, String taxJurisdiction,
                                    BigDecimal grossAmount, BigDecimal taxAmount,
                                    LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "TAX_CALCULATION_PROCESSED",
                    userId.toString(),
                    String.format("Tax calculation %s - Type: %s, Year: %d, Jurisdiction: %s, Tax: %s", 
                            calculationStatus, calculationType, taxYear, taxJurisdiction, taxAmount),
                    Map.of(
                            "calculationId", calculationId.toString(),
                            "userId", userId.toString(),
                            "calculationType", calculationType,
                            "calculationStatus", calculationStatus,
                            "taxYear", taxYear,
                            "taxJurisdiction", taxJurisdiction,
                            "grossAmount", grossAmount.toString(),
                            "taxAmount", taxAmount.toString(),
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit tax calculation - CalculationId: {}", calculationId, e);
        }
    }
    
    private void handleCalculationFailure(UUID calculationId, UUID userId, String calculationType,
                                         Exception error) {
        try {
            taxCalculationService.handleCalculationFailure(calculationId, userId, calculationType, 
                    error.getMessage());
            
            auditService.auditFinancialEvent(
                    "TAX_CALCULATION_PROCESSING_FAILED",
                    userId.toString(),
                    "Failed to process tax calculation: " + error.getMessage(),
                    Map.of(
                            "calculationId", calculationId.toString(),
                            "userId", userId.toString(),
                            "calculationType", calculationType != null ? calculationType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle calculation failure - CalculationId: {}", calculationId, e);
        }
    }
    
    @KafkaListener(
        topics = {"tax-calculation-events.DLQ", "tax-calculations.DLQ"},
        groupId = "tax-service-calculation-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Tax calculation event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID calculationId = event.containsKey("calculationId") ? 
                    UUID.fromString((String) event.get("calculationId")) : null;
            UUID userId = event.containsKey("userId") ? 
                    UUID.fromString((String) event.get("userId")) : null;
            String calculationType = (String) event.get("calculationType");
            
            log.error("DLQ: Tax calculation failed permanently - CalculationId: {}, UserId: {}, Type: {} - MANUAL REVIEW REQUIRED", 
                    calculationId, userId, calculationType);
            
            if (calculationId != null && userId != null) {
                taxCalculationService.markForManualReview(calculationId, userId, calculationType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse tax calculation DLQ event: {}", eventJson, e);
        }
    }
}