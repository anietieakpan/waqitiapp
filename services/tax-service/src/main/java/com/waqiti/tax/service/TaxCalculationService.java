package com.waqiti.tax.service;

import com.waqiti.tax.dto.TaxCalculationRequest;
import com.waqiti.tax.dto.TaxCalculationResponse;
import com.waqiti.tax.dto.TaxReportRequest;
import com.waqiti.tax.dto.TaxReportResponse;
import com.waqiti.tax.entity.TaxRule;
import com.waqiti.tax.entity.TaxTransaction;
import com.waqiti.tax.entity.TaxJurisdiction;
import com.waqiti.tax.repository.TaxRuleRepository;
import com.waqiti.tax.repository.TaxTransactionRepository;
import com.waqiti.tax.repository.TaxJurisdictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxCalculationService {

    private final TaxRuleRepository taxRuleRepository;
    private final TaxTransactionRepository taxTransactionRepository;
    private final TaxJurisdictionRepository taxJurisdictionRepository;
    private final TaxReportingService taxReportingService;
    private final ExternalTaxApiService externalTaxApiService;
    
    @Lazy
    private final TaxCalculationService self;

    @Transactional
    public TaxCalculationResponse calculateTax(@NonNull TaxCalculationRequest request) {
        log.info("Calculating tax for transaction: {} in jurisdiction: {}", 
            request.getTransactionId(), request.getJurisdiction());

        try {
            // Get applicable tax rules (using self-injection for cache to work)
            List<TaxRule> applicableRules = self.getApplicableTaxRules(
                request.getJurisdiction(),
                request.getTransactionType(),
                request.getAmount(),
                request.getTransactionDate()
            );

            // Calculate tax for each applicable rule
            Map<String, BigDecimal> taxBreakdown = new HashMap<>();
            BigDecimal totalTax = BigDecimal.ZERO;
            BigDecimal totalRate = BigDecimal.ZERO;

            for (TaxRule rule : applicableRules) {
                BigDecimal taxAmount = calculateTaxForRule(rule, request);
                if (taxAmount.compareTo(BigDecimal.ZERO) > 0) {
                    taxBreakdown.put(rule.getTaxType(), taxAmount);
                    totalTax = totalTax.add(taxAmount);
                    totalRate = totalRate.add(rule.getRate());
                }
            }

            // Handle special cases
            totalTax = applyTaxCaps(totalTax, request);
            totalTax = applyTaxExemptions(totalTax, request);

            // Create tax transaction record
            TaxTransaction taxTransaction = createTaxTransaction(request, totalTax, taxBreakdown);
            taxTransactionRepository.save(taxTransaction);

            TaxCalculationResponse response = TaxCalculationResponse.builder()
                .transactionId(request.getTransactionId())
                .totalTaxAmount(totalTax)
                .effectiveTaxRate(calculateEffectiveRate(totalTax, request.getAmount()))
                .taxBreakdown(taxBreakdown)
                .applicableRules(applicableRules.stream()
                    .map(rule -> rule.getTaxType() + ": " + rule.getRate() + "%")
                    .collect(Collectors.toList()))
                .calculationDate(LocalDateTime.now())
                .jurisdiction(request.getJurisdiction())
                .build();

            log.info("Tax calculation completed: {} total tax for transaction {}", 
                totalTax, request.getTransactionId());

            return response;

        } catch (Exception e) {
            log.error("Error calculating tax for transaction: {}", request.getTransactionId(), e);
            throw new TaxCalculationException("Failed to calculate tax: " + e.getMessage(), e);
        }
    }

    @Cacheable(value = "tax-rules", key = "#jurisdiction + ':' + #transactionType + ':' + #date")
    public List<TaxRule> getApplicableTaxRules(@NonNull String jurisdiction, @NonNull String transactionType, 
                                               @NonNull BigDecimal amount, @NonNull LocalDate date) {
        
        List<TaxRule> rules = taxRuleRepository.findByJurisdictionAndTransactionTypeAndActiveTrue(
            jurisdiction, transactionType);

        return rules.stream()
            .filter(rule -> isRuleApplicable(rule, amount, date))
            .collect(Collectors.toList());
    }

    private boolean isRuleApplicable(@NonNull TaxRule rule, @NonNull BigDecimal amount, @NonNull LocalDate date) {
        // Check date validity
        if (rule.getEffectiveFrom() != null && date.isBefore(rule.getEffectiveFrom())) {
            return false;
        }
        if (rule.getEffectiveTo() != null && date.isAfter(rule.getEffectiveTo())) {
            return false;
        }

        // Check amount thresholds
        if (rule.getMinimumAmount() != null && amount.compareTo(rule.getMinimumAmount()) < 0) {
            return false;
        }
        if (rule.getMaximumAmount() != null && amount.compareTo(rule.getMaximumAmount()) > 0) {
            return false;
        }

        return true;
    }

    private BigDecimal calculateTaxForRule(@NonNull TaxRule rule, @NonNull TaxCalculationRequest request) {
        BigDecimal taxableAmount = calculateTaxableAmount(rule, request);
        
        switch (rule.getCalculationType()) {
            case "PERCENTAGE":
                return taxableAmount.multiply(rule.getRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.CEILING); // Tax always rounds up
                
            case "FLAT_FEE":
                return rule.getFlatFee() != null ? rule.getFlatFee() : BigDecimal.ZERO;
                
            case "PROGRESSIVE":
                return calculateProgressiveTax(taxableAmount, rule);
                
            case "BRACKET":
                return calculateBracketTax(taxableAmount, rule);
                
            default:
                log.warn("Unknown tax calculation type: {}", rule.getCalculationType());
                return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculateTaxableAmount(@NonNull TaxRule rule, @NonNull TaxCalculationRequest request) {
        BigDecimal taxableAmount = request.getAmount();

        // Apply deductions if specified in the rule
        if (rule.getDeductions() != null && !rule.getDeductions().isEmpty()) {
            for (String deductionCode : rule.getDeductions()) {
                BigDecimal deduction = getDeductionAmount(deductionCode, request);
                taxableAmount = taxableAmount.subtract(deduction);
            }
        }

        return taxableAmount.max(BigDecimal.ZERO);
    }

    private BigDecimal calculateProgressiveTax(@NonNull BigDecimal taxableAmount, @NonNull TaxRule rule) {
        // Implementation for progressive tax calculation
        // This would use tax brackets defined in the rule
        List<TaxBracket> brackets = parseTaxBrackets(rule.getTaxBrackets());
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal remainingAmount = taxableAmount;

        for (TaxBracket bracket : brackets) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal bracketAmount = remainingAmount.min(bracket.getUpperLimit().subtract(bracket.getLowerLimit()));
            BigDecimal bracketTax = bracketAmount.multiply(bracket.getRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            totalTax = totalTax.add(bracketTax);
            remainingAmount = remainingAmount.subtract(bracketAmount);
        }

        return totalTax.setScale(2, RoundingMode.CEILING); // Tax always rounds up
    }

    private BigDecimal calculateBracketTax(@NonNull BigDecimal taxableAmount, @NonNull TaxRule rule) {
        // Implementation for bracket-based tax calculation
        List<TaxBracket> brackets = parseTaxBrackets(rule.getTaxBrackets());
        
        for (TaxBracket bracket : brackets) {
            if (taxableAmount.compareTo(bracket.getLowerLimit()) >= 0 &&
                taxableAmount.compareTo(bracket.getUpperLimit()) <= 0) {
                return taxableAmount.multiply(bracket.getRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.CEILING); // Tax always rounds up
            }
        }

        return BigDecimal.ZERO;
    }

    private List<TaxBracket> parseTaxBrackets(@Nullable String taxBrackets) {
        List<TaxBracket> brackets = new ArrayList<>();
        
        if (taxBrackets != null && !taxBrackets.isEmpty()) {
            try {
                // Parse JSON string to extract tax bracket information
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode bracketArray = mapper.readTree(taxBrackets);
                
                for (com.fasterxml.jackson.databind.JsonNode bracketNode : bracketArray) {
                    BigDecimal minIncome = new BigDecimal(bracketNode.get("minIncome").asText());
                    BigDecimal maxIncome = bracketNode.has("maxIncome") && !bracketNode.get("maxIncome").isNull()
                        ? new BigDecimal(bracketNode.get("maxIncome").asText())
                        : null; // Unlimited max for highest bracket
                    BigDecimal rate = new BigDecimal(bracketNode.get("rate").asText());
                    
                    brackets.add(new TaxBracket(minIncome, maxIncome, rate));
                }
                
                // Sort brackets by minimum income
                brackets.sort(Comparator.comparing(TaxBracket::getMinIncome));
                
            } catch (Exception e) {
                log.error("Failed to parse tax brackets JSON: {}", taxBrackets, e);
                // Fallback to default US tax brackets for current year
                brackets.addAll(getDefaultUSFederalTaxBrackets());
            }
        } else {
            // No specific brackets provided, use default
            brackets.addAll(getDefaultUSFederalTaxBrackets());
        }
        
        return brackets;
    }

    /**
     * Get default US Federal tax brackets for current tax year
     */
    private List<TaxBracket> getDefaultUSFederalTaxBrackets() {
        List<TaxBracket> defaultBrackets = new ArrayList<>();
        
        // 2024 US Federal Tax Brackets (Single Filer)
        defaultBrackets.add(new TaxBracket(BigDecimal.ZERO, new BigDecimal("11000"), new BigDecimal("10")));
        defaultBrackets.add(new TaxBracket(new BigDecimal("11001"), new BigDecimal("44725"), new BigDecimal("12")));
        defaultBrackets.add(new TaxBracket(new BigDecimal("44726"), new BigDecimal("95375"), new BigDecimal("22")));
        defaultBrackets.add(new TaxBracket(new BigDecimal("95376"), new BigDecimal("182050"), new BigDecimal("24")));
        defaultBrackets.add(new TaxBracket(new BigDecimal("182051"), new BigDecimal("231250"), new BigDecimal("32")));
        defaultBrackets.add(new TaxBracket(new BigDecimal("231251"), new BigDecimal("578125"), new BigDecimal("35")));
        defaultBrackets.add(new TaxBracket(new BigDecimal("578126"), null, new BigDecimal("37"))); // No upper limit
        
        return defaultBrackets;
    }

    /**
     * Get applicable tax bracket for income amount using progressive tax calculation
     */
    public List<TaxBracket> getApplicableTaxBrackets(@NonNull BigDecimal income, @NonNull String jurisdiction, int taxYear) {
        List<TaxBracket> applicableBrackets = new ArrayList<>();
        
        try {
            // First try to get jurisdiction-specific brackets from database
            Optional<TaxJurisdiction> taxJurisdiction = taxJurisdictionRepository
                .findByJurisdictionCodeAndTaxYear(jurisdiction, taxYear);
            
            if (taxJurisdiction.isPresent() && taxJurisdiction.get().getTaxBracketsJson() != null) {
                applicableBrackets = parseTaxBrackets(taxJurisdiction.get().getTaxBracketsJson());
            } else {
                // Fallback to default brackets
                applicableBrackets = getDefaultUSFederalTaxBrackets();
                log.warn("Using default tax brackets for jurisdiction: {} year: {}", jurisdiction, taxYear);
            }
            
            // Filter brackets that apply to the given income
            return applicableBrackets.stream()
                .filter(bracket -> {
                    boolean aboveMin = income.compareTo(bracket.getMinIncome()) >= 0;
                    boolean belowMax = bracket.getMaxIncome() == null || 
                                     income.compareTo(bracket.getMaxIncome()) <= 0;
                    return aboveMin && belowMax;
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error getting tax brackets for income: {} jurisdiction: {} year: {}", 
                income, jurisdiction, taxYear, e);
            // Return safe default
            return List.of(new TaxBracket(BigDecimal.ZERO, null, new BigDecimal("10")));
        }
    }

    /**
     * Calculate progressive tax using multiple brackets
     */
    public BigDecimal calculateProgressiveTax(@NonNull BigDecimal income, @NonNull List<TaxBracket> brackets) {
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal remainingIncome = income;
        
        for (TaxBracket bracket : brackets) {
            if (remainingIncome.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            
            BigDecimal bracketMin = bracket.getMinIncome();
            BigDecimal bracketMax = bracket.getMaxIncome() != null 
                ? bracket.getMaxIncome() 
                : income; // Use total income if no max limit
                
            // Calculate taxable amount in this bracket
            BigDecimal taxableInThisBracket;
            if (income.compareTo(bracketMax) <= 0) {
                // Income falls within this bracket
                taxableInThisBracket = income.subtract(bracketMin).max(BigDecimal.ZERO);
            } else {
                // Income exceeds this bracket
                taxableInThisBracket = bracketMax.subtract(bracketMin);
            }
            
            // Calculate tax for this bracket
            BigDecimal bracketTax = taxableInThisBracket
                .multiply(bracket.getRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.CEILING); // Tax rounds up
            
            totalTax = totalTax.add(bracketTax);
            remainingIncome = remainingIncome.subtract(taxableInThisBracket);
            
            log.debug("Bracket {}-{} at {}%: taxable=${}, tax=${}", 
                bracketMin, bracketMax, bracket.getRate(), taxableInThisBracket, bracketTax);
        }
        
        return totalTax.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getDeductionAmount(String deductionCode, TaxCalculationRequest request) {
        // Implementation would look up deduction amounts based on codes
        switch (deductionCode) {
            case "STANDARD_DEDUCTION":
                return BigDecimal.valueOf(1000);
            case "TRANSACTION_FEE_DEDUCTION":
                return request.getTransactionFee() != null ? request.getTransactionFee() : BigDecimal.ZERO;
            default:
                return BigDecimal.ZERO;
        }
    }

    private BigDecimal applyTaxCaps(BigDecimal totalTax, TaxCalculationRequest request) {
        // Apply maximum tax caps if defined
        TaxJurisdiction jurisdiction = taxJurisdictionRepository.findByCode(request.getJurisdiction())
            .orElse(null);
            
        if (jurisdiction != null && jurisdiction.getMaxTaxAmount() != null) {
            return totalTax.min(jurisdiction.getMaxTaxAmount());
        }
        
        return totalTax;
    }

    private BigDecimal applyTaxExemptions(BigDecimal totalTax, TaxCalculationRequest request) {
        // Apply tax exemptions based on user status or transaction type
        if (request.getTaxExemptionCodes() != null && !request.getTaxExemptionCodes().isEmpty()) {
            for (String exemptionCode : request.getTaxExemptionCodes()) {
                if (isExemptionApplicable(exemptionCode, request)) {
                    BigDecimal exemptionAmount = calculateExemptionAmount(exemptionCode, totalTax, request);
                    totalTax = totalTax.subtract(exemptionAmount);
                }
            }
        }
        
        return totalTax.max(BigDecimal.ZERO);
    }

    private boolean isExemptionApplicable(String exemptionCode, TaxCalculationRequest request) {
        // Check if exemption is applicable
        switch (exemptionCode) {
            case "CHARITY_EXEMPTION":
                return "CHARITY".equals(request.getTransactionCategory());
            case "GOVERNMENT_EXEMPTION":
                return "GOVERNMENT".equals(request.getRecipientType());
            case "SMALL_AMOUNT_EXEMPTION":
                return request.getAmount().compareTo(BigDecimal.valueOf(10)) <= 0;
            default:
                return false;
        }
    }

    private BigDecimal calculateExemptionAmount(String exemptionCode, BigDecimal totalTax, 
                                                TaxCalculationRequest request) {
        switch (exemptionCode) {
            case "CHARITY_EXEMPTION":
                return totalTax; // Full exemption
            case "GOVERNMENT_EXEMPTION":
                return totalTax; // Full exemption
            case "SMALL_AMOUNT_EXEMPTION":
                return totalTax; // Full exemption for small amounts
            default:
                return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculateEffectiveRate(BigDecimal totalTax, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return totalTax.divide(amount, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    private TaxTransaction createTaxTransaction(TaxCalculationRequest request, BigDecimal totalTax, 
                                                Map<String, BigDecimal> taxBreakdown) {
        return TaxTransaction.builder()
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .jurisdiction(request.getJurisdiction())
            .transactionType(request.getTransactionType())
            .transactionAmount(request.getAmount())
            .taxAmount(totalTax)
            .taxBreakdown(taxBreakdown)
            .calculationDate(LocalDateTime.now())
            .taxYear(Year.now().getValue())
            .status("CALCULATED")
            .build();
    }

    @Transactional(readOnly = true)
    public TaxReportResponse generateTaxReport(TaxReportRequest request) {
        log.info("Generating tax report for user: {} for period: {} to {}", 
            request.getUserId(), request.getStartDate(), request.getEndDate());

        List<TaxTransaction> transactions = taxTransactionRepository
            .findByUserIdAndCalculationDateBetween(
                request.getUserId(), 
                request.getStartDate().atStartOfDay(), 
                request.getEndDate().atTime(23, 59, 59)
            );

        BigDecimal totalTaxPaid = transactions.stream()
            .map(TaxTransaction::getTaxAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTransactionAmount = transactions.stream()
            .map(TaxTransaction::getTransactionAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> taxByType = transactions.stream()
            .flatMap(tx -> tx.getTaxBreakdown().entrySet().stream())
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.reducing(BigDecimal.ZERO, Map.Entry::getValue, BigDecimal::add)
            ));

        return TaxReportResponse.builder()
            .userId(request.getUserId())
            .reportPeriodStart(request.getStartDate())
            .reportPeriodEnd(request.getEndDate())
            .totalTransactionAmount(totalTransactionAmount)
            .totalTaxAmount(totalTaxPaid)
            .effectiveTaxRate(calculateEffectiveRate(totalTaxPaid, totalTransactionAmount))
            .taxByType(taxByType)
            .transactionCount(transactions.size())
            .generatedDate(LocalDateTime.now())
            .build();
    }

    public void updateTaxRules(String jurisdiction) {
        log.info("Updating tax rules for jurisdiction: {}", jurisdiction);
        
        try {
            // Fetch latest tax rules from external service if available
            List<TaxRule> updatedRules = externalTaxApiService.fetchTaxRules(jurisdiction);
            
            if (!updatedRules.isEmpty()) {
                // Deactivate old rules
                List<TaxRule> existingRules = taxRuleRepository.findByJurisdictionAndActiveTrue(jurisdiction);
                existingRules.forEach(rule -> rule.setActive(false));
                taxRuleRepository.saveAll(existingRules);
                
                // Save new rules
                taxRuleRepository.saveAll(updatedRules);
                
                log.info("Updated {} tax rules for jurisdiction: {}", updatedRules.size(), jurisdiction);
            }
            
        } catch (Exception e) {
            log.error("Failed to update tax rules for jurisdiction: {}", jurisdiction, e);
            throw new TaxCalculationException("Failed to update tax rules", e);
        }
    }

    // Inner class for tax brackets
    private static class TaxBracket {
        private final BigDecimal lowerLimit;
        private final BigDecimal upperLimit;
        private final BigDecimal rate;

        public TaxBracket(BigDecimal lowerLimit, BigDecimal upperLimit, BigDecimal rate) {
            this.lowerLimit = lowerLimit;
            this.upperLimit = upperLimit;
            this.rate = rate;
        }

        public BigDecimal getLowerLimit() { return lowerLimit; }
        public BigDecimal getUpperLimit() { return upperLimit; }
        public BigDecimal getRate() { return rate; }
    }

    // Custom exception
    public static class TaxCalculationException extends RuntimeException {
        public TaxCalculationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}