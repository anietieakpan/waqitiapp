package com.waqiti.accounting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Fee Calculation Service
 * Calculates fees based on database-driven configuration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeeCalculationService {

    private final JdbcTemplate jdbcTemplate;
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Calculate total fees for a transaction amount
     */
    public BigDecimal calculateTotalFees(BigDecimal amount, String currency, String transactionType) {
        LocalDate today = LocalDate.now();

        String sql = """
            SELECT fee_code, calculation_method, percentage_rate, fixed_amount, minimum_fee, maximum_fee
            FROM fee_configuration
            WHERE is_active = TRUE
              AND currency = ?
              AND effective_from <= ?
              AND (effective_to IS NULL OR effective_to >= ?)
              AND (applies_to_transaction_types IS NULL OR applies_to_transaction_types @> ?::jsonb)
            ORDER BY priority DESC
            """;

        List<Map<String, Object>> fees = jdbcTemplate.queryForList(
            sql, currency, today, today, String.format("[\"%s\"]", transactionType)
        );

        BigDecimal totalFees = BigDecimal.ZERO;

        for (Map<String, Object> fee : fees) {
            BigDecimal feeAmount = calculateSingleFee(amount, fee);
            totalFees = totalFees.add(feeAmount);
            log.debug("Applied fee {}: {}", fee.get("fee_code"), feeAmount);
        }

        return totalFees.setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate a single fee based on configuration
     */
    private BigDecimal calculateSingleFee(BigDecimal amount, Map<String, Object> feeConfig) {
        String calculationMethod = (String) feeConfig.get("calculation_method");
        BigDecimal percentageRate = (BigDecimal) feeConfig.get("percentage_rate");
        BigDecimal fixedAmount = (BigDecimal) feeConfig.get("fixed_amount");
        BigDecimal minimumFee = (BigDecimal) feeConfig.get("minimum_fee");
        BigDecimal maximumFee = (BigDecimal) feeConfig.get("maximum_fee");

        BigDecimal calculatedFee = switch (calculationMethod) {
            case "PERCENTAGE" -> calculatePercentageFee(amount, percentageRate);
            case "FIXED" -> fixedAmount != null ? fixedAmount : BigDecimal.ZERO;
            case "PERCENTAGE_PLUS_FIXED" -> calculatePercentageFee(amount, percentageRate)
                .add(fixedAmount != null ? fixedAmount : BigDecimal.ZERO);
            default -> BigDecimal.ZERO;
        };

        // Apply minimum/maximum constraints
        if (minimumFee != null && calculatedFee.compareTo(minimumFee) < 0) {
            calculatedFee = minimumFee;
        }
        if (maximumFee != null && calculatedFee.compareTo(maximumFee) > 0) {
            calculatedFee = maximumFee;
        }

        return calculatedFee.setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate percentage-based fee
     */
    private BigDecimal calculatePercentageFee(BigDecimal amount, BigDecimal percentageRate) {
        if (percentageRate == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(percentageRate.divide(BigDecimal.valueOf(100), SCALE, ROUNDING_MODE))
            .setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate tax amount
     */
    public BigDecimal calculateTax(BigDecimal amount, String jurisdiction, String taxType) {
        LocalDate today = LocalDate.now();

        String sql = """
            SELECT tax_rate, tax_inclusive
            FROM tax_configuration
            WHERE is_active = TRUE
              AND tax_jurisdiction = ?
              AND tax_type = ?
              AND effective_from <= ?
              AND (effective_to IS NULL OR effective_to >= ?)
            LIMIT 1
            """;

        List<Map<String, Object>> taxes = jdbcTemplate.queryForList(sql, jurisdiction, taxType, today, today);

        if (taxes.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<String, Object> tax = taxes.get(0);
        BigDecimal taxRate = (BigDecimal) tax.get("tax_rate");
        Boolean taxInclusive = (Boolean) tax.get("tax_inclusive");

        if (Boolean.TRUE.equals(taxInclusive)) {
            // Tax is already included in amount, calculate the portion
            return amount.multiply(taxRate.divide(BigDecimal.valueOf(100).add(taxRate), SCALE, ROUNDING_MODE))
                .setScale(SCALE, ROUNDING_MODE);
        } else {
            // Tax is on top of amount
            return amount.multiply(taxRate.divide(BigDecimal.valueOf(100), SCALE, ROUNDING_MODE))
                .setScale(SCALE, ROUNDING_MODE);
        }
    }
}
