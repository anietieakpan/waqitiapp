package com.waqiti.payment.international.corridor;

import com.waqiti.payment.international.model.*;
import com.waqiti.payment.international.provider.RemitlyProvider;
import com.waqiti.payment.international.provider.WiseProvider;
import com.waqiti.payment.international.provider.WesternUnionProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;

/**
 * Transfer corridor from USA to Mexico
 * High-volume corridor with multiple provider options and competitive rates
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class USAToMexicoCorridor implements TransferCorridor {
    
    private static final String CORRIDOR_ID = "USA_MX_001";
    private static final String CORRIDOR_NAME = "USA to Mexico";
    private static final String SENDER_COUNTRY = "US";
    private static final String RECIPIENT_COUNTRY = "MX";
    
    private final RemitlyProvider remitlyProvider;
    private final WiseProvider wiseProvider;
    private final WesternUnionProvider westernUnionProvider;
    
    @Override
    public String getId() {
        return CORRIDOR_ID;
    }
    
    @Override
    public String getName() {
        return CORRIDOR_NAME;
    }
    
    @Override
    public String getSenderCountry() {
        return SENDER_COUNTRY;
    }
    
    @Override
    public String getRecipientCountry() {
        return RECIPIENT_COUNTRY;
    }
    
    @Override
    public String getProviderId() {
        // Return the primary/preferred provider for this corridor
        // Can be dynamically selected based on amount, delivery method, etc.
        return remitlyProvider.getProviderId();
    }
    
    @Override
    public String getProviderName() {
        return remitlyProvider.getName();
    }
    
    @Override
    public boolean isActive() {
        return true; // This is a major corridor, always active
    }
    
    @Override
    public Set<String> getSupportedCurrencies() {
        return Set.of("USD", "MXN");
    }
    
    @Override
    public boolean supportsCurrencyPair(String fromCurrency, String toCurrency) {
        return "USD".equals(fromCurrency) && "MXN".equals(toCurrency);
    }
    
    @Override
    public BigDecimal getMinAmount() {
        return new BigDecimal("1.00"); // $1 USD minimum
    }
    
    @Override
    public BigDecimal getMaxAmount() {
        return new BigDecimal("50000.00"); // $50,000 USD maximum per transaction
    }
    
    @Override
    public boolean supportsAmount(BigDecimal amount, String currency) {
        if (!"USD".equals(currency)) {
            return false;
        }
        
        return amount.compareTo(getMinAmount()) >= 0 && 
               amount.compareTo(getMaxAmount()) <= 0;
    }
    
    @Override
    public Duration getEstimatedDeliveryTime() {
        // Average delivery time for Mexico corridor (bank transfers)
        return Duration.ofMinutes(30); // 30 minutes for express transfers
    }
    
    @Override
    public Set<PaymentMethod> getSupportedPaymentMethods() {
        return Set.of(
            PaymentMethod.DEBIT_CARD,
            PaymentMethod.CREDIT_CARD,
            PaymentMethod.BANK_TRANSFER,
            PaymentMethod.ACH,
            PaymentMethod.DIGITAL_WALLET
        );
    }
    
    @Override
    public Set<DeliveryMethod> getSupportedDeliveryMethods() {
        return Set.of(
            DeliveryMethod.BANK_DEPOSIT,
            DeliveryMethod.CASH_PICKUP,
            DeliveryMethod.MOBILE_WALLET,
            DeliveryMethod.HOME_DELIVERY,
            DeliveryMethod.DEBIT_CARD_DEPOSIT
        );
    }
    
    @Override
    public boolean requiresComplianceCheck() {
        return true; // USA-Mexico corridor has strict compliance requirements
    }
    
    @Override
    public Set<ComplianceRequirement> getComplianceRequirements() {
        return Set.of(
            ComplianceRequirement.SENDER_ID_VERIFICATION,
            ComplianceRequirement.RECIPIENT_ID_VERIFICATION,
            ComplianceRequirement.PURPOSE_OF_REMITTANCE,
            ComplianceRequirement.SOURCE_OF_FUNDS,
            ComplianceRequirement.ANTI_MONEY_LAUNDERING_CHECK,
            ComplianceRequirement.OFAC_SCREENING,
            ComplianceRequirement.BSA_REPORTING // Bank Secrecy Act
        );
    }
    
    @Override
    public FeeStructure calculateFees(BigDecimal amount, String currency) {
        if (!"USD".equals(currency)) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
        
        FeeStructure.Builder feeBuilder = FeeStructure.builder();
        
        // Base transfer fee (tiered based on amount)
        BigDecimal baseFee = calculateBaseFee(amount);
        feeBuilder.baseFee(baseFee);
        
        // Exchange rate margin (built into the rate, but tracked separately)
        BigDecimal exchangeMargin = amount.multiply(new BigDecimal("0.015")); // 1.5% margin
        feeBuilder.exchangeRateMargin(exchangeMargin);
        
        // Processing fee (fixed)
        BigDecimal processingFee = new BigDecimal("2.99");
        feeBuilder.processingFee(processingFee);
        
        // Regulatory fees
        BigDecimal regulatoryFee = calculateRegulatoryFee(amount);
        feeBuilder.regulatoryFee(regulatoryFee);
        
        // Payment method fees (variable based on method)
        BigDecimal paymentMethodFee = new BigDecimal("0.00"); // Will be calculated based on selected method
        feeBuilder.paymentMethodFee(paymentMethodFee);
        
        // Delivery method fees
        BigDecimal deliveryFee = new BigDecimal("0.00"); // Bank deposit is free, cash pickup has fees
        feeBuilder.deliveryFee(deliveryFee);
        
        // Calculate total fees
        BigDecimal totalFees = baseFee
                .add(processingFee)
                .add(regulatoryFee)
                .add(paymentMethodFee)
                .add(deliveryFee);
        
        return feeBuilder
                .totalFees(totalFees)
                .recipientFees(BigDecimal.ZERO) // No fees deducted from recipient amount
                .currency(currency)
                .breakdown(buildFeeBreakdown(baseFee, processingFee, regulatoryFee, 
                                           paymentMethodFee, deliveryFee))
                .build();
    }
    
    @Override
    public double getReliabilityScore() {
        return 0.98; // 98% success rate for USA-Mexico corridor
    }
    
    @Override
    public boolean supports(String senderCountry, String recipientCountry) {
        return SENDER_COUNTRY.equals(senderCountry) && RECIPIENT_COUNTRY.equals(recipientCountry);
    }
    
    @Override
    public Map<String, Object> getCorridorSpecificData() {
        Map<String, Object> data = new HashMap<>();
        
        // Popular delivery methods in Mexico
        data.put("popular_delivery_methods", List.of("BANK_DEPOSIT", "OXXO_PICKUP", "ELEKTRA_PICKUP"));
        
        // Major Mexican banks supported
        data.put("supported_banks", List.of(
            "BBVA Mexico", "Santander Mexico", "Citibanamex", "Banorte", 
            "HSBC Mexico", "Scotiabank Mexico", "Banco Azteca"
        ));
        
        // Cash pickup networks
        data.put("cash_pickup_networks", List.of(
            "OXXO", "Elektra", "Coppel", "Farmacia Guadalajara", "7-Eleven Mexico"
        ));
        
        // Mobile wallet providers
        data.put("mobile_wallets", List.of(
            "Mercado Pago", "BBVA Wallet", "Santander Way", "Citibanamex Móvil"
        ));
        
        // Regulatory information
        data.put("regulatory_authority", "CNBV"); // Comisión Nacional Bancaria y de Valores
        data.put("max_annual_limit", "USD 100,000");
        data.put("reporting_threshold", "USD 3,000");
        
        // Operating hours (Mexico City time)
        data.put("operating_hours", Map.of(
            "monday_friday", "06:00-22:00",
            "saturday", "08:00-18:00",
            "sunday", "10:00-16:00",
            "timezone", "America/Mexico_City"
        ));
        
        // Special considerations
        data.put("holiday_delays", List.of(
            "Día de la Revolución (Nov 20)",
            "Día de Muertos (Nov 1-2)",
            "Christmas/New Year period"
        ));
        
        return data;
    }
    
    // Helper methods
    
    private BigDecimal calculateBaseFee(BigDecimal amount) {
        // Tiered fee structure for USA-Mexico corridor
        if (amount.compareTo(new BigDecimal("100")) <= 0) {
            return new BigDecimal("4.99"); // $4.99 for amounts up to $100
        } else if (amount.compareTo(new BigDecimal("500")) <= 0) {
            return new BigDecimal("7.99"); // $7.99 for amounts $100-$500
        } else if (amount.compareTo(new BigDecimal("1000")) <= 0) {
            return new BigDecimal("12.99"); // $12.99 for amounts $500-$1000
        } else if (amount.compareTo(new BigDecimal("5000")) <= 0) {
            return new BigDecimal("19.99"); // $19.99 for amounts $1000-$5000
        } else {
            // For amounts over $5000, charge 0.5% of the amount, max $99.99
            BigDecimal percentageFee = amount.multiply(new BigDecimal("0.005"));
            return percentageFee.min(new BigDecimal("99.99"));
        }
    }
    
    private BigDecimal calculateRegulatoryFee(BigDecimal amount) {
        // BSA (Bank Secrecy Act) and FinCEN regulatory fees
        if (amount.compareTo(new BigDecimal("3000")) > 0) {
            return new BigDecimal("1.50"); // Additional fee for reporting requirements
        }
        return BigDecimal.ZERO;
    }
    
    private Map<String, BigDecimal> buildFeeBreakdown(
            BigDecimal baseFee, 
            BigDecimal processingFee, 
            BigDecimal regulatoryFee,
            BigDecimal paymentMethodFee, 
            BigDecimal deliveryFee) {
        
        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        breakdown.put("Transfer Fee", baseFee);
        breakdown.put("Processing Fee", processingFee);
        
        if (regulatoryFee.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.put("Regulatory Fee", regulatoryFee);
        }
        
        if (paymentMethodFee.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.put("Payment Method Fee", paymentMethodFee);
        }
        
        if (deliveryFee.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.put("Delivery Fee", deliveryFee);
        }
        
        return breakdown;
    }
    
    /**
     * Get delivery time based on specific delivery method
     */
    public Duration getEstimatedDeliveryTime(DeliveryMethod deliveryMethod) {
        switch (deliveryMethod) {
            case BANK_DEPOSIT:
                return Duration.ofMinutes(30); // Express bank deposits
            case CASH_PICKUP:
                return Duration.ofMinutes(15); // Immediate cash pickup availability
            case MOBILE_WALLET:
                return Duration.ofMinutes(5); // Near-instant mobile wallet deposits
            case HOME_DELIVERY:
                return Duration.ofHours(2); // 2-hour home delivery in major cities
            case DEBIT_CARD_DEPOSIT:
                return Duration.ofMinutes(10); // Quick debit card deposits
            default:
                return getEstimatedDeliveryTime();
        }
    }
    
    /**
     * Calculate fees based on specific payment and delivery methods
     */
    public FeeStructure calculateFees(BigDecimal amount, String currency, 
                                    PaymentMethod paymentMethod, 
                                    DeliveryMethod deliveryMethod) {
        
        FeeStructure baseFees = calculateFees(amount, currency);
        
        // Adjust for payment method
        BigDecimal paymentMethodFee = calculatePaymentMethodFee(amount, paymentMethod);
        
        // Adjust for delivery method
        BigDecimal deliveryFee = calculateDeliveryMethodFee(amount, deliveryMethod);
        
        return baseFees.toBuilder()
                .paymentMethodFee(paymentMethodFee)
                .deliveryFee(deliveryFee)
                .totalFees(baseFees.getTotalFees().add(paymentMethodFee).add(deliveryFee))
                .build();
    }
    
    private BigDecimal calculatePaymentMethodFee(BigDecimal amount, PaymentMethod paymentMethod) {
        switch (paymentMethod) {
            case DEBIT_CARD:
                return BigDecimal.ZERO; // No additional fee for debit cards
            case CREDIT_CARD:
                return amount.multiply(new BigDecimal("0.029")); // 2.9% credit card fee
            case BANK_TRANSFER:
                return BigDecimal.ZERO; // No additional fee for bank transfers
            case ACH:
                return new BigDecimal("0.50"); // Small ACH processing fee
            case DIGITAL_WALLET:
                return BigDecimal.ZERO; // No additional fee for digital wallets
            default:
                return BigDecimal.ZERO;
        }
    }
    
    private BigDecimal calculateDeliveryMethodFee(BigDecimal amount, DeliveryMethod deliveryMethod) {
        switch (deliveryMethod) {
            case BANK_DEPOSIT:
                return BigDecimal.ZERO; // Free bank deposits
            case CASH_PICKUP:
                return new BigDecimal("2.99"); // Cash pickup network fee
            case MOBILE_WALLET:
                return BigDecimal.ZERO; // Free mobile wallet deposits
            case HOME_DELIVERY:
                return new BigDecimal("9.99"); // Home delivery service fee
            case DEBIT_CARD_DEPOSIT:
                return new BigDecimal("1.99"); // Debit card deposit fee
            default:
                return BigDecimal.ZERO;
        }
    }
    
    /**
     * Get corridor-specific exchange rate markup
     */
    public BigDecimal getExchangeRateMarkup() {
        return new BigDecimal("0.015"); // 1.5% margin for USD-MXN
    }
    
    /**
     * Check if recipient bank is supported
     */
    public boolean isBankSupported(String bankCode, String swiftCode) {
        // List of major Mexican banks and their codes
        Set<String> supportedBankCodes = Set.of(
            "012", // BBVA Mexico
            "014", // Santander Mexico
            "002", // Citibanamex
            "072", // Banorte
            "021", // HSBC Mexico
            "044", // Scotiabank Mexico
            "127"  // Banco Azteca
        );
        
        return supportedBankCodes.contains(bankCode) || 
               (swiftCode != null && swiftCode.startsWith("MX"));
    }
    
    /**
     * Get available cash pickup locations near recipient
     */
    public List<PickupLocation> getPickupLocations(String city, String state) {
        // This would typically call an external API to get pickup locations
        // For now, return sample locations
        List<PickupLocation> locations = new ArrayList<>();
        
        // OXXO stores (most common in Mexico)
        locations.add(PickupLocation.builder()
                .networkId("OXXO")
                .locationId("OXXO_" + city + "_001")
                .name("OXXO " + city + " Centro")
                .address(city + " Centro, " + state)
                .hours("24/7")
                .phone("+52-800-OXXO")
                .fees(new BigDecimal("2.99"))
                .build());
        
        return locations;
    }
}