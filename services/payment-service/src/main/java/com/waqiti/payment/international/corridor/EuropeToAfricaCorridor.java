package com.waqiti.payment.international.corridor;

import com.waqiti.payment.international.model.*;
import com.waqiti.payment.international.provider.MoneyGramProvider;
import com.waqiti.payment.international.provider.WiseProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

/**
 * Transfer corridor from Europe to Africa
 * Supports multiple African countries with various delivery methods
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EuropeToAfricaCorridor implements TransferCorridor {
    
    private static final String CORRIDOR_ID = "EUR_AFR_001";
    private static final String CORRIDOR_NAME = "Europe to Africa";
    
    // Supported European sender countries
    private static final Set<String> EUROPEAN_COUNTRIES = Set.of(
        "DE", "FR", "IT", "ES", "NL", "BE", "AT", "PT", "IE", "LU",
        "FI", "EE", "LV", "LT", "SK", "SI", "MT", "CY", "GR"
    );
    
    // Supported African recipient countries
    private static final Set<String> AFRICAN_COUNTRIES = Set.of(
        "NG", // Nigeria
        "GH", // Ghana
        "KE", // Kenya
        "UG", // Uganda
        "TZ", // Tanzania
        "RW", // Rwanda
        "SN", // Senegal
        "CI", // Côte d'Ivoire
        "BF", // Burkina Faso
        "ML", // Mali
        "CM", // Cameroon
        "ZA", // South Africa
        "BW", // Botswana
        "ZW", // Zimbabwe
        "ZM", // Zambia
        "MW", // Malawi
        "MZ", // Mozambique
        "AO", // Angola
        "ET", // Ethiopia
        "DJ", // Djibouti
        "SO"  // Somalia
    );
    
    private final MoneyGramProvider moneyGramProvider;
    private final WiseProvider wiseProvider;
    
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
        return "EUR"; // Represents multiple European countries
    }
    
    @Override
    public String getRecipientCountry() {
        return "AFR"; // Represents multiple African countries
    }
    
    @Override
    public String getProviderId() {
        return moneyGramProvider.getProviderId(); // Primary provider for Africa
    }
    
    @Override
    public String getProviderName() {
        return moneyGramProvider.getName();
    }
    
    @Override
    public boolean isActive() {
        return true;
    }
    
    @Override
    public Set<String> getSupportedCurrencies() {
        return Set.of(
            "EUR", // Euro
            "GBP", // British Pound
            "CHF", // Swiss Franc
            "USD", // US Dollar
            "NGN", // Nigerian Naira
            "GHS", // Ghanaian Cedi
            "KES", // Kenyan Shilling
            "UGX", // Ugandan Shilling
            "TZS", // Tanzanian Shilling
            "RWF", // Rwandan Franc
            "XOF", // West African CFA Franc
            "XAF", // Central African CFA Franc
            "ZAR", // South African Rand
            "BWP", // Botswana Pula
            "ZMW", // Zambian Kwacha
            "MWK", // Malawian Kwacha
            "MZN", // Mozambican Metical
            "AOA", // Angolan Kwanza
            "ETB", // Ethiopian Birr
            "DJF", // Djiboutian Franc
            "SOS"  // Somali Shilling
        );
    }
    
    @Override
    public boolean supportsCurrencyPair(String fromCurrency, String toCurrency) {
        Set<String> europeanCurrencies = Set.of("EUR", "GBP", "CHF");
        return europeanCurrencies.contains(fromCurrency) && 
               getSupportedCurrencies().contains(toCurrency) &&
               !fromCurrency.equals(toCurrency);
    }
    
    @Override
    public BigDecimal getMinAmount() {
        return new BigDecimal("10.00"); // €10 minimum
    }
    
    @Override
    public BigDecimal getMaxAmount() {
        return new BigDecimal("25000.00"); // €25,000 maximum per transaction
    }
    
    @Override
    public boolean supportsAmount(BigDecimal amount, String currency) {
        // Convert to EUR for comparison if needed
        BigDecimal amountInEur = convertToEur(amount, currency);
        return amountInEur.compareTo(getMinAmount()) >= 0 && 
               amountInEur.compareTo(getMaxAmount()) <= 0;
    }
    
    @Override
    public Duration getEstimatedDeliveryTime() {
        return Duration.ofHours(2); // Average 2 hours for Africa corridor
    }
    
    @Override
    public Set<PaymentMethod> getSupportedPaymentMethods() {
        return Set.of(
            PaymentMethod.BANK_TRANSFER,
            PaymentMethod.SEPA_TRANSFER,
            PaymentMethod.DEBIT_CARD,
            PaymentMethod.CREDIT_CARD,
            PaymentMethod.DIGITAL_WALLET,
            PaymentMethod.OPEN_BANKING
        );
    }
    
    @Override
    public Set<DeliveryMethod> getSupportedDeliveryMethods() {
        return Set.of(
            DeliveryMethod.BANK_DEPOSIT,
            DeliveryMethod.CASH_PICKUP,
            DeliveryMethod.MOBILE_WALLET,
            DeliveryMethod.AIRTIME_TOPUP,
            DeliveryMethod.HOME_DELIVERY
        );
    }
    
    @Override
    public boolean requiresComplianceCheck() {
        return true; // EU regulations require strict compliance
    }
    
    @Override
    public Set<ComplianceRequirement> getComplianceRequirements() {
        return Set.of(
            ComplianceRequirement.SENDER_ID_VERIFICATION,
            ComplianceRequirement.RECIPIENT_ID_VERIFICATION,
            ComplianceRequirement.PURPOSE_OF_REMITTANCE,
            ComplianceRequirement.SOURCE_OF_FUNDS,
            ComplianceRequirement.ANTI_MONEY_LAUNDERING_CHECK,
            ComplianceRequirement.EU_SANCTIONS_SCREENING,
            ComplianceRequirement.PSD2_COMPLIANCE,
            ComplianceRequirement.GDPR_COMPLIANCE,
            ComplianceRequirement.CDD_ENHANCED, // Customer Due Diligence
            ComplianceRequirement.POLITICALLY_EXPOSED_PERSON_CHECK
        );
    }
    
    @Override
    public FeeStructure calculateFees(BigDecimal amount, String currency) {
        FeeStructure.Builder feeBuilder = FeeStructure.builder();
        
        // Convert amount to EUR for fee calculation
        BigDecimal amountInEur = convertToEur(amount, currency);
        
        // Base transfer fee (higher for Africa due to infrastructure costs)
        BigDecimal baseFee = calculateBaseFee(amountInEur);
        feeBuilder.baseFee(baseFee);
        
        // Exchange rate margin (higher for exotic currency pairs)
        BigDecimal exchangeMargin = amountInEur.multiply(getExchangeRateMarkup(currency));
        feeBuilder.exchangeRateMargin(exchangeMargin);
        
        // Processing fee
        BigDecimal processingFee = new BigDecimal("4.99");
        feeBuilder.processingFee(processingFee);
        
        // Regulatory fees (EU compliance costs)
        BigDecimal regulatoryFee = calculateRegulatoryFee(amountInEur);
        feeBuilder.regulatoryFee(regulatoryFee);
        
        // Corridor infrastructure fee (for African banking infrastructure)
        BigDecimal infrastructureFee = calculateInfrastructureFee(amountInEur);
        feeBuilder.infrastructureFee(infrastructureFee);
        
        // Partner network fee
        BigDecimal partnerFee = calculatePartnerFee(amountInEur);
        feeBuilder.partnerNetworkFee(partnerFee);
        
        BigDecimal totalFees = baseFee
                .add(processingFee)
                .add(regulatoryFee)
                .add(infrastructureFee)
                .add(partnerFee);
        
        return feeBuilder
                .totalFees(totalFees)
                .recipientFees(BigDecimal.ZERO)
                .currency(currency)
                .breakdown(buildFeeBreakdown(baseFee, processingFee, regulatoryFee, 
                                           infrastructureFee, partnerFee))
                .build();
    }
    
    @Override
    public double getReliabilityScore() {
        return 0.92; // 92% success rate (lower due to African infrastructure challenges)
    }
    
    @Override
    public boolean supports(String senderCountry, String recipientCountry) {
        return EUROPEAN_COUNTRIES.contains(senderCountry) && 
               AFRICAN_COUNTRIES.contains(recipientCountry);
    }
    
    @Override
    public Map<String, Object> getCorridorSpecificData() {
        Map<String, Object> data = new HashMap<>();
        
        // Supported European sender countries
        data.put("supported_sender_countries", EUROPEAN_COUNTRIES);
        
        // Supported African recipient countries
        data.put("supported_recipient_countries", AFRICAN_COUNTRIES);
        
        // Mobile money providers by country
        Map<String, List<String>> mobileMoneyProviders = new HashMap<>();
        mobileMoneyProviders.put("NG", List.of("Paga", "Paystack", "Flutterwave", "Opay"));
        mobileMoneyProviders.put("GH", List.of("MTN Mobile Money", "Vodafone Cash", "Tigo Cash"));
        mobileMoneyProviders.put("KE", List.of("M-Pesa", "Airtel Money", "T-Kash"));
        mobileMoneyProviders.put("UG", List.of("MTN Mobile Money", "Airtel Money"));
        mobileMoneyProviders.put("TZ", List.of("M-Pesa", "Airtel Money", "Tigo Pesa", "Halopesa"));
        mobileMoneyProviders.put("RW", List.of("MTN Mobile Money", "Airtel Money", "Tigo Cash"));
        mobileMoneyProviders.put("SN", List.of("Orange Money", "Free Money", "Wave"));
        mobileMoneyProviders.put("CI", List.of("Orange Money", "MTN Mobile Money", "Moov Money"));
        mobileMoneyProviders.put("ZA", List.of("Standard Bank", "FNB", "Nedbank", "ABSA"));
        data.put("mobile_money_providers", mobileMoneyProviders);
        
        // Cash pickup networks by region
        Map<String, List<String>> cashPickupNetworks = new HashMap<>();
        cashPickupNetworks.put("West Africa", List.of("MoneyGram", "Western Union", "Ria", "Small World"));
        cashPickupNetworks.put("East Africa", List.of("MoneyGram", "Western Union", "WorldRemit", "Remitly"));
        cashPickupNetworks.put("Southern Africa", List.of("MoneyGram", "Western Union", "HelloPaisa", "Mama Money"));
        data.put("cash_pickup_networks", cashPickupNetworks);
        
        // Bank coverage by country
        Map<String, List<String>> bankCoverage = new HashMap<>();
        bankCoverage.put("NG", List.of("Access Bank", "Zenith Bank", "GTBank", "First Bank", "UBA"));
        bankCoverage.put("GH", List.of("Ecobank", "GCB Bank", "Standard Chartered", "Fidelity Bank"));
        bankCoverage.put("KE", List.of("Equity Bank", "KCB Bank", "Standard Chartered", "Barclays"));
        bankCoverage.put("ZA", List.of("Standard Bank", "FirstRand", "Nedbank", "ABSA", "Capitec"));
        data.put("major_banks", bankCoverage);
        
        // Regulatory authorities
        Map<String, String> regulatoryAuthorities = new HashMap<>();
        regulatoryAuthorities.put("EU", "EBA - European Banking Authority");
        regulatoryAuthorities.put("NG", "CBN - Central Bank of Nigeria");
        regulatoryAuthorities.put("GH", "Bank of Ghana");
        regulatoryAuthorities.put("KE", "CBK - Central Bank of Kenya");
        regulatoryAuthorities.put("ZA", "SARB - South African Reserve Bank");
        data.put("regulatory_authorities", regulatoryAuthorities);
        
        // Time zones (recipient countries)
        Map<String, String> timeZones = new HashMap<>();
        timeZones.put("West Africa", "GMT+0/+1");
        timeZones.put("Central Africa", "GMT+1/+2");
        timeZones.put("East Africa", "GMT+3");
        timeZones.put("Southern Africa", "GMT+2");
        data.put("time_zones", timeZones);
        
        // Special considerations
        data.put("infrastructure_challenges", List.of(
            "Limited banking infrastructure in rural areas",
            "Mobile money dominance in some countries",
            "Currency volatility",
            "Regulatory differences across countries",
            "Internet connectivity issues"
        ));
        
        // Compliance notes
        data.put("compliance_notes", Map.of(
            "eu_regulations", "PSD2, AML5, GDPR compliance required",
            "reporting_threshold_eur", "1000",
            "enhanced_dd_threshold_eur", "10000",
            "max_anonymous_amount_eur", "150"
        ));
        
        return data;
    }
    
    // Helper methods
    
    private BigDecimal convertToEur(BigDecimal amount, String currency) {
        if ("EUR".equals(currency)) {
            return amount;
        }
        
        // Simplified conversion rates (in production, use real-time rates)
        Map<String, BigDecimal> rates = Map.of(
            "GBP", new BigDecimal("1.15"),
            "CHF", new BigDecimal("0.92"),
            "USD", new BigDecimal("0.85")
        );
        
        BigDecimal rate = rates.getOrDefault(currency, BigDecimal.ONE);
        return amount.multiply(rate);
    }
    
    private BigDecimal calculateBaseFee(BigDecimal amountInEur) {
        if (amountInEur.compareTo(new BigDecimal("50")) <= 0) {
            return new BigDecimal("8.99");
        } else if (amountInEur.compareTo(new BigDecimal("200")) <= 0) {
            return new BigDecimal("12.99");
        } else if (amountInEur.compareTo(new BigDecimal("500")) <= 0) {
            return new BigDecimal("18.99");
        } else if (amountInEur.compareTo(new BigDecimal("1000")) <= 0) {
            return new BigDecimal("24.99");
        } else {
            // For amounts over €1000, charge 2.5% of the amount, max €199.99
            BigDecimal percentageFee = amountInEur.multiply(new BigDecimal("0.025"));
            return percentageFee.min(new BigDecimal("199.99"));
        }
    }
    
    private BigDecimal getExchangeRateMarkup(String targetCurrency) {
        // Higher margins for more exotic African currencies
        Map<String, BigDecimal> margins = Map.of(
            "NGN", new BigDecimal("0.025"), // 2.5% for Nigerian Naira
            "GHS", new BigDecimal("0.030"), // 3.0% for Ghanaian Cedi
            "KES", new BigDecimal("0.020"), // 2.0% for Kenyan Shilling
            "UGX", new BigDecimal("0.035"), // 3.5% for Ugandan Shilling
            "TZS", new BigDecimal("0.025"), // 2.5% for Tanzanian Shilling
            "ZAR", new BigDecimal("0.015"), // 1.5% for South African Rand
            "XOF", new BigDecimal("0.020"), // 2.0% for West African CFA Franc
            "XAF", new BigDecimal("0.025")  // 2.5% for Central African CFA Franc
        );
        
        return margins.getOrDefault(targetCurrency, new BigDecimal("0.030"));
    }
    
    private BigDecimal calculateRegulatoryFee(BigDecimal amountInEur) {
        // EU regulatory compliance costs
        if (amountInEur.compareTo(new BigDecimal("1000")) > 0) {
            return new BigDecimal("2.50"); // Enhanced due diligence fee
        } else if (amountInEur.compareTo(new BigDecimal("150")) > 0) {
            return new BigDecimal("1.00"); // Standard compliance fee
        }
        return BigDecimal.ZERO;
    }
    
    private BigDecimal calculateInfrastructureFee(BigDecimal amountInEur) {
        // Fee to cover African banking infrastructure costs
        return amountInEur.multiply(new BigDecimal("0.005")); // 0.5% infrastructure fee
    }
    
    private BigDecimal calculatePartnerFee(BigDecimal amountInEur) {
        // Fee for partner network (mobile money, cash pickup agents)
        if (amountInEur.compareTo(new BigDecimal("100")) <= 0) {
            return new BigDecimal("3.99");
        } else {
            return amountInEur.multiply(new BigDecimal("0.015")); // 1.5% partner fee
        }
    }
    
    private Map<String, BigDecimal> buildFeeBreakdown(
            BigDecimal baseFee,
            BigDecimal processingFee,
            BigDecimal regulatoryFee,
            BigDecimal infrastructureFee,
            BigDecimal partnerFee) {
        
        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        breakdown.put("Transfer Fee", baseFee);
        breakdown.put("Processing Fee", processingFee);
        
        if (regulatoryFee.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.put("EU Compliance Fee", regulatoryFee);
        }
        
        breakdown.put("Infrastructure Fee", infrastructureFee);
        breakdown.put("Partner Network Fee", partnerFee);
        
        return breakdown;
    }
    
    /**
     * Get delivery time based on recipient country and delivery method
     */
    public Duration getEstimatedDeliveryTime(String recipientCountry, DeliveryMethod deliveryMethod) {
        // Base delivery times vary by African region
        Duration baseTime;
        
        if (Set.of("NG", "GH", "ZA", "KE").contains(recipientCountry)) {
            // Major economies with better infrastructure
            baseTime = Duration.ofMinutes(90);
        } else if (Set.of("UG", "TZ", "RW", "SN", "CI").contains(recipientCountry)) {
            // Developing economies
            baseTime = Duration.ofHours(3);
        } else {
            // Frontier economies
            baseTime = Duration.ofHours(6);
        }
        
        // Adjust based on delivery method
        switch (deliveryMethod) {
            case MOBILE_WALLET:
                return baseTime.dividedBy(2); // Mobile money is fastest
            case BANK_DEPOSIT:
                return baseTime;
            case CASH_PICKUP:
                return baseTime.plus(Duration.ofMinutes(30));
            case AIRTIME_TOPUP:
                return Duration.ofMinutes(15); // Almost instant
            case HOME_DELIVERY:
                return baseTime.plus(Duration.ofHours(24)); // Next day delivery
            default:
                return baseTime;
        }
    }
    
    /**
     * Check if mobile money provider is supported for recipient country
     */
    public boolean isMobileMoneySupported(String recipientCountry, String provider) {
        Map<String, Set<String>> supportedProviders = Map.of(
            "KE", Set.of("M-Pesa", "Airtel Money"),
            "UG", Set.of("MTN Mobile Money", "Airtel Money"),
            "TZ", Set.of("M-Pesa", "Airtel Money", "Tigo Pesa"),
            "GH", Set.of("MTN Mobile Money", "Vodafone Cash"),
            "NG", Set.of("Paga", "Paystack", "Opay"),
            "RW", Set.of("MTN Mobile Money", "Airtel Money")
        );
        
        return supportedProviders.getOrDefault(recipientCountry, Collections.emptySet())
                .contains(provider);
    }
}