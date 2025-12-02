package com.waqiti.payment.invoice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component for generating unique invoice numbers with various formats
 */
@Slf4j
@Component
public class InvoiceNumberGenerator {
    
    private final ConcurrentHashMap<String, AtomicLong> sequenceMap = new ConcurrentHashMap<>();
    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    
    public enum InvoiceNumberFormat {
        SEQUENTIAL,           // INV-000001
        DATE_SEQUENTIAL,     // INV-20240101-001
        YEAR_SEQUENTIAL,     // INV-2024-00001
        MONTH_SEQUENTIAL,    // INV-202401-001
        CUSTOM_PREFIX,       // CUSTOM-000001
        BUSINESS_SEQUENTIAL  // BUS123-INV-00001
    }
    
    /**
     * Generate invoice number with default format
     */
    public String generateInvoiceNumber(String businessId) {
        return generateInvoiceNumber(businessId, InvoiceNumberFormat.YEAR_SEQUENTIAL, "INV");
    }
    
    /**
     * Generate invoice number with specified format
     */
    public String generateInvoiceNumber(String businessId, InvoiceNumberFormat format, String prefix) {
        String key = buildSequenceKey(businessId, format);
        long sequence = getNextSequence(key);
        
        switch (format) {
            case SEQUENTIAL:
                return formatSequential(prefix, sequence);
                
            case DATE_SEQUENTIAL:
                return formatDateSequential(prefix, sequence);
                
            case YEAR_SEQUENTIAL:
                return formatYearSequential(prefix, sequence);
                
            case MONTH_SEQUENTIAL:
                return formatMonthSequential(prefix, sequence);
                
            case CUSTOM_PREFIX:
                return formatCustomPrefix(prefix, sequence);
                
            case BUSINESS_SEQUENTIAL:
                return formatBusinessSequential(businessId, prefix, sequence);
                
            default:
                return formatSequential(prefix, sequence);
        }
    }
    
    /**
     * Generate invoice number with custom pattern
     */
    public String generateCustomInvoiceNumber(String businessId, String pattern, long sequence) {
        LocalDate now = LocalDate.now();
        
        return pattern.replace("{BUSINESS}", businessId)
                     .replace("{YEAR}", String.valueOf(now.getYear()))
                     .replace("{MONTH}", String.format("%02d", now.getMonthValue()))
                     .replace("{DAY}", String.format("%02d", now.getDayOfMonth()))
                     .replace("{SEQUENCE}", String.format("%06d", sequence))
                     .replace("{SEQ4}", String.format("%04d", sequence))
                     .replace("{SEQ5}", String.format("%05d", sequence));
    }
    
    /**
     * Reset sequence for a business (useful for yearly/monthly resets)
     */
    public void resetSequence(String businessId, InvoiceNumberFormat format) {
        String key = buildSequenceKey(businessId, format);
        sequenceMap.put(key, new AtomicLong(0));
        log.info("Reset invoice number sequence for business: {}, format: {}", businessId, format);
    }
    
    /**
     * Get current sequence value without incrementing
     */
    public long getCurrentSequence(String businessId, InvoiceNumberFormat format) {
        String key = buildSequenceKey(businessId, format);
        AtomicLong sequence = sequenceMap.get(key);
        return sequence != null ? sequence.get() : 0;
    }
    
    /**
     * Set specific sequence value
     */
    public void setSequence(String businessId, InvoiceNumberFormat format, long value) {
        String key = buildSequenceKey(businessId, format);
        sequenceMap.put(key, new AtomicLong(value));
        log.info("Set invoice number sequence for business: {}, format: {}, value: {}", businessId, format, value);
    }
    
    /**
     * Validate invoice number format
     */
    public boolean isValidInvoiceNumber(String invoiceNumber) {
        if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
            return false;
        }
        
        // Basic validation - contains alphanumeric and common separators
        return invoiceNumber.matches("^[A-Z0-9][A-Z0-9\\-/_]*$");
    }
    
    /**
     * Parse invoice number to extract components
     */
    public InvoiceNumberComponents parseInvoiceNumber(String invoiceNumber) {
        if (!isValidInvoiceNumber(invoiceNumber)) {
            throw new IllegalArgumentException("Invalid invoice number: " + invoiceNumber);
        }
        
        String[] parts = invoiceNumber.split("[-/_]");
        
        return InvoiceNumberComponents.builder()
                .fullNumber(invoiceNumber)
                .prefix(parts.length > 0 ? parts[0] : null)
                .sequence(extractSequence(invoiceNumber))
                .year(extractYear(invoiceNumber).orElse(null))
                .month(extractMonth(invoiceNumber).orElse(null))
                .build();
    }
    
    // Private helper methods
    
    private String buildSequenceKey(String businessId, InvoiceNumberFormat format) {
        LocalDate now = LocalDate.now();
        
        switch (format) {
            case YEAR_SEQUENTIAL:
                return businessId + "-" + now.getYear();
                
            case MONTH_SEQUENTIAL:
                return businessId + "-" + now.getYear() + "-" + now.getMonthValue();
                
            case DATE_SEQUENTIAL:
                return businessId + "-" + now.format(DATE_FORMATTER);
                
            default:
                return businessId;
        }
    }
    
    private long getNextSequence(String key) {
        return sequenceMap.computeIfAbsent(key, k -> new AtomicLong(0))
                         .incrementAndGet();
    }
    
    private String formatSequential(String prefix, long sequence) {
        return String.format("%s-%06d", prefix, sequence);
    }
    
    private String formatDateSequential(String prefix, long sequence) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return String.format("%s-%s-%03d", prefix, date, sequence);
    }
    
    private String formatYearSequential(String prefix, long sequence) {
        String year = LocalDate.now().format(YEAR_FORMATTER);
        return String.format("%s-%s-%05d", prefix, year, sequence);
    }
    
    private String formatMonthSequential(String prefix, long sequence) {
        String month = LocalDate.now().format(MONTH_FORMATTER);
        return String.format("%s-%s-%03d", prefix, month, sequence);
    }
    
    private String formatCustomPrefix(String prefix, long sequence) {
        return String.format("%s-%06d", prefix != null ? prefix : "INV", sequence);
    }
    
    private String formatBusinessSequential(String businessId, String prefix, long sequence) {
        String shortBusinessId = businessId.length() > 6 ? 
                                businessId.substring(0, 6).toUpperCase() : 
                                businessId.toUpperCase();
        return String.format("%s-%s-%05d", shortBusinessId, prefix, sequence);
    }
    
    private Long extractSequence(String invoiceNumber) {
        // Extract numeric sequence from the end of the invoice number
        String reversed = new StringBuilder(invoiceNumber).reverse().toString();
        StringBuilder digits = new StringBuilder();
        
        for (char c : reversed.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (digits.length() > 0) {
                break;
            }
        }
        
        if (digits.length() > 0) {
            String sequence = digits.reverse().toString();
            try {
                return Long.parseLong(sequence);
            } catch (NumberFormatException e) {
                log.error("CRITICAL: Failed to parse invoice sequence from digits");
                return 0L; // Return 0 as fallback for sequence parsing failure
            }
        }
        
        log.debug("No digits found in invoice number - returning 0");
        return 0L; // Return 0 as fallback when no digits found
    }
    
    private Optional<Integer> extractYear(String invoiceNumber) {
        // Try to extract 4-digit year
        if (invoiceNumber.matches(".*\\d{4}.*")) {
            for (int i = 0; i <= invoiceNumber.length() - 4; i++) {
                String substring = invoiceNumber.substring(i, i + 4);
                if (substring.matches("\\d{4}")) {
                    int year = Integer.parseInt(substring);
                    if (year >= 2000 && year <= 2100) {
                        return Optional.of(year);
                    }
                }
            }
        }
        log.warn("Unable to extract valid year from invoice number: {}", invoiceNumber);
        return Optional.empty();
    }
    
    private Optional<Integer> extractMonth(String invoiceNumber) {
        // Try to extract month after year
        Optional<Integer> yearOpt = extractYear(invoiceNumber);
        if (yearOpt.isPresent()) {
            int year = yearOpt.get();
            int yearIndex = invoiceNumber.indexOf(String.valueOf(year));
            if (yearIndex >= 0 && yearIndex + 4 < invoiceNumber.length() - 1) {
                String afterYear = invoiceNumber.substring(yearIndex + 4);
                if (afterYear.matches("^\\D?(\\d{2}).*")) {
                    String monthStr = afterYear.replaceFirst("^\\D?(\\d{2}).*", "$1");
                    int month = Integer.parseInt(monthStr);
                    if (month >= 1 && month <= 12) {
                        return Optional.of(month);
                    }
                }
            }
        }
        return Optional.empty();
    }
    
    /**
     * Inner class to hold invoice number components
     */
    public static class InvoiceNumberComponents {
        private final String fullNumber;
        private final String prefix;
        private final Long sequence;
        private final Integer year;
        private final Integer month;
        
        private InvoiceNumberComponents(Builder builder) {
            this.fullNumber = builder.fullNumber;
            this.prefix = builder.prefix;
            this.sequence = builder.sequence;
            this.year = builder.year;
            this.month = builder.month;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String fullNumber;
            private String prefix;
            private Long sequence;
            private Integer year;
            private Integer month;
            
            public Builder fullNumber(String fullNumber) {
                this.fullNumber = fullNumber;
                return this;
            }
            
            public Builder prefix(String prefix) {
                this.prefix = prefix;
                return this;
            }
            
            public Builder sequence(Long sequence) {
                this.sequence = sequence;
                return this;
            }
            
            public Builder year(Integer year) {
                this.year = year;
                return this;
            }
            
            public Builder month(Integer month) {
                this.month = month;
                return this;
            }
            
            public InvoiceNumberComponents build() {
                return new InvoiceNumberComponents(this);
            }
        }
        
        // Getters
        public String getFullNumber() { return fullNumber; }
        public String getPrefix() { return prefix; }
        public Long getSequence() { return sequence; }
        public Integer getYear() { return year; }
        public Integer getMonth() { return month; }
    }
}