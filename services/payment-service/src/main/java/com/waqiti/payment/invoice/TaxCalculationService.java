package com.waqiti.payment.invoice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for calculating taxes on invoices and line items
 */
@Slf4j
@Service
public class TaxCalculationService {
    
    private static final Map<String, BigDecimal> DEFAULT_TAX_RATES = new HashMap<>();
    
    static {
        // Initialize default tax rates by country/region
        DEFAULT_TAX_RATES.put("US", BigDecimal.valueOf(0.08)); // 8% average US sales tax
        DEFAULT_TAX_RATES.put("CA", BigDecimal.valueOf(0.13)); // 13% average Canadian GST+PST
        DEFAULT_TAX_RATES.put("GB", BigDecimal.valueOf(0.20)); // 20% UK VAT
        DEFAULT_TAX_RATES.put("EU", BigDecimal.valueOf(0.21)); // 21% average EU VAT
        DEFAULT_TAX_RATES.put("AU", BigDecimal.valueOf(0.10)); // 10% Australian GST
    }
    
    /**
     * Calculate tax amount for a given amount and tax rate
     */
    public BigDecimal calculateTax(BigDecimal amount, BigDecimal taxRate) {
        if (amount == null || taxRate == null) {
            return BigDecimal.ZERO;
        }
        
        return amount.multiply(taxRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate tax for an invoice line item
     */
    public BigDecimal calculateLineItemTax(BigDecimal unitPrice, BigDecimal quantity, BigDecimal taxRate, BigDecimal discountRate) {
        if (unitPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal subtotal = unitPrice.multiply(quantity);
        
        // Apply discount if present
        if (discountRate != null && discountRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountAmount = subtotal.multiply(discountRate)
                                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            subtotal = subtotal.subtract(discountAmount);
        }
        
        // Calculate tax on discounted amount
        return calculateTax(subtotal, taxRate != null ? taxRate : BigDecimal.ZERO);
    }
    
    /**
     * Calculate compound tax (tax on tax)
     */
    public BigDecimal calculateCompoundTax(BigDecimal amount, BigDecimal primaryTaxRate, BigDecimal secondaryTaxRate) {
        BigDecimal primaryTax = calculateTax(amount, primaryTaxRate);
        BigDecimal amountWithPrimaryTax = amount.add(primaryTax);
        BigDecimal secondaryTax = calculateTax(amountWithPrimaryTax, secondaryTaxRate);
        
        return primaryTax.add(secondaryTax);
    }
    
    /**
     * Get default tax rate for a country/region
     */
    @Cacheable(value = "countryCodes", key = "#countryCode")
    public BigDecimal getDefaultTaxRate(String countryCode) {
        return DEFAULT_TAX_RATES.getOrDefault(countryCode.toUpperCase(), BigDecimal.ZERO);
    }
    
    /**
     * Calculate tax-inclusive amount from tax-exclusive amount
     */
    public BigDecimal calculateTaxInclusiveAmount(BigDecimal taxExclusiveAmount, BigDecimal taxRate) {
        if (taxExclusiveAmount == null || taxRate == null) {
            return taxExclusiveAmount != null ? taxExclusiveAmount : BigDecimal.ZERO;
        }
        
        BigDecimal taxAmount = calculateTax(taxExclusiveAmount, taxRate);
        return taxExclusiveAmount.add(taxAmount);
    }
    
    /**
     * Extract tax-exclusive amount from tax-inclusive amount
     */
    public BigDecimal extractTaxExclusiveAmount(BigDecimal taxInclusiveAmount, BigDecimal taxRate) {
        if (taxInclusiveAmount == null || taxRate == null || taxRate.compareTo(BigDecimal.ZERO) == 0) {
            return taxInclusiveAmount != null ? taxInclusiveAmount : BigDecimal.ZERO;
        }
        
        // Formula: taxExclusive = taxInclusive / (1 + taxRate/100)
        BigDecimal divisor = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        return taxInclusiveAmount.divide(divisor, 2, RoundingMode.HALF_UP);
    }
    
    /**
     * Validate tax rate is within acceptable range
     */
    public boolean isValidTaxRate(BigDecimal taxRate) {
        if (taxRate == null) {
            return true; // Null tax rate is valid (means no tax)
        }
        
        // Tax rate should be between 0 and 100
        return taxRate.compareTo(BigDecimal.ZERO) >= 0 && 
               taxRate.compareTo(BigDecimal.valueOf(100)) <= 0;
    }
    
    /**
     * Calculate effective tax rate for multiple tax components
     */
    public BigDecimal calculateEffectiveTaxRate(Map<String, BigDecimal> taxComponents) {
        if (taxComponents == null || taxComponents.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        return taxComponents.values().stream()
                          .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Apply tax rounding rules based on jurisdiction
     */
    public BigDecimal applyTaxRounding(BigDecimal taxAmount, String jurisdiction) {
        if (taxAmount == null) {
            return BigDecimal.ZERO;
        }
        
        // Different jurisdictions may have different rounding rules
        switch (jurisdiction != null ? jurisdiction.toUpperCase() : "DEFAULT") {
            case "CA":
                // Canada rounds to nearest 5 cents for cash transactions
                return roundToNearest5Cents(taxAmount);
            case "JP":
                // Japan rounds down to nearest yen
                return taxAmount.setScale(0, RoundingMode.DOWN);
            default:
                // Default to 2 decimal places, half-up rounding
                return taxAmount.setScale(2, RoundingMode.HALF_UP);
        }
    }
    
    /**
     * Round to nearest 5 cents (for Canadian cash transactions)
     */
    private BigDecimal roundToNearest5Cents(BigDecimal amount) {
        BigDecimal cents = amount.multiply(BigDecimal.valueOf(100));
        BigDecimal rounded = cents.divide(BigDecimal.valueOf(5), 0, RoundingMode.HALF_UP)
                                  .multiply(BigDecimal.valueOf(5));
        return rounded.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}