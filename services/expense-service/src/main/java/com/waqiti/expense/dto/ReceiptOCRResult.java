package com.waqiti.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Result from OCR processing of receipt images.
 * Contains extracted receipt data including merchant info, line items, and totals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptOCRResult {
    
    private boolean successful;
    private String errorMessage;
    private double confidenceScore;
    
    // Merchant Information
    private String merchantName;
    private String storeLocation;
    private String storeAddress;
    private String phoneNumber;
    private String website;
    
    // Transaction Details
    private String receiptNumber;
    private LocalDate transactionDate;
    private String transactionTime;
    private String cashierName;
    private String registerNumber;
    
    // Financial Details
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal taxRate;
    private BigDecimal tipAmount;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private String currency;
    
    // Payment Information
    private String paymentMethod;
    private String lastFourDigits;
    private String authorizationCode;
    
    // Line Items
    private List<LineItem> lineItems;
    
    // Additional Metadata
    private String rawText;
    private List<String> warnings;
    private ProcessingMetadata processingMetadata;
    
    /**
     * Create a failed result with error message
     */
    public static ReceiptOCRResult failed(String errorMessage) {
        return ReceiptOCRResult.builder()
            .successful(false)
            .errorMessage(errorMessage)
            .confidenceScore(0.0)
            .build();
    }
    
    /**
     * Line item extracted from receipt
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        private String description;
        private String productCode;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private BigDecimal discount;
        private boolean taxable;
        private String category;
    }
    
    /**
     * Metadata about the OCR processing
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingMetadata {
        private long processingTimeMs;
        private String ocrEngine;
        private String ocrVersion;
        private int imageWidth;
        private int imageHeight;
        private String imageFormat;
        private boolean preprocessingApplied;
        private List<String> enhancementsApplied;
    }
}