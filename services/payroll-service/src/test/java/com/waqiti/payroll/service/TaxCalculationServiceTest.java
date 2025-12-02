package com.waqiti.payroll.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TaxCalculationService
 * Tests IRS tax bracket calculations, FICA, Medicare, and state tax logic
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tax Calculation Service Tests")
class TaxCalculationServiceTest {

    private TaxCalculationService taxCalculationService;

    @BeforeEach
    void setUp() {
        taxCalculationService = new TaxCalculationService();

        // Set default tax rates
        ReflectionTestUtils.setField(taxCalculationService, "federalTaxRate", new BigDecimal("0.22"));
        ReflectionTestUtils.setField(taxCalculationService, "stateTaxRate", new BigDecimal("0.05"));
        ReflectionTestUtils.setField(taxCalculationService, "socialSecurityRate", new BigDecimal("0.062"));
        ReflectionTestUtils.setField(taxCalculationService, "medicareRate", new BigDecimal("0.0145"));
        ReflectionTestUtils.setField(taxCalculationService, "socialSecurityWageBase", new BigDecimal("160200.00"));
        ReflectionTestUtils.setField(taxCalculationService, "additionalMedicareThreshold", new BigDecimal("200000.00"));
        ReflectionTestUtils.setField(taxCalculationService, "additionalMedicareRate", new BigDecimal("0.009"));
    }

    @Test
    @DisplayName("Should calculate employee taxes correctly for single filer")
    void testCalculateEmployeeTaxes_SingleFiler() {
        // Given
        TaxCalculationService.PaymentCalculation calculation = new TaxCalculationService.PaymentCalculation();
        calculation.setEmployeeId("EMP001");
        calculation.setGrossAmount(new BigDecimal("5000.00"));
        calculation.setYtdGross(new BigDecimal("50000.00"));
        calculation.setFilingStatus("SINGLE");
        calculation.setExemptions(1);
        calculation.setState("CA");

        // When
        TaxCalculationService.EmployeeTaxCalculation result =
            taxCalculationService.calculateEmployeeTaxes(calculation);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmployeeId()).isEqualTo("EMP001");
        assertThat(result.getGrossAmount()).isEqualByComparingTo("5000.00");

        // Verify Social Security tax (6.2%)
        assertThat(result.getSocialSecurityTax()).isEqualByComparingTo("310.00");

        // Verify Medicare tax (1.45%)
        assertThat(result.getMedicareTax()).isEqualByComparingTo("72.50");

        // Verify net amount is calculated
        assertThat(result.getNetAmount()).isLessThan(result.getGrossAmount());
    }

    @Test
    @DisplayName("Should cap Social Security tax at wage base limit")
    void testSocialSecurityTax_WageBaseCap() {
        // Given - Employee already at SS wage base
        TaxCalculationService.PaymentCalculation calculation = new TaxCalculationService.PaymentCalculation();
        calculation.setEmployeeId("EMP002");
        calculation.setGrossAmount(new BigDecimal("10000.00"));
        calculation.setYtdGross(new BigDecimal("160200.00")); // At wage base
        calculation.setFilingStatus("SINGLE");
        calculation.setExemptions(1);
        calculation.setState("CA");

        // When
        TaxCalculationService.EmployeeTaxCalculation result =
            taxCalculationService.calculateEmployeeTaxes(calculation);

        // Then - Should be zero since already at wage base
        assertThat(result.getSocialSecurityTax()).isEqualByComparingTo("0.00");

        // Medicare has no cap, should still be calculated
        assertThat(result.getMedicareTax()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should apply additional Medicare tax for high earners")
    void testMedicareTax_AdditionalTaxForHighEarners() {
        // Given - Employee above additional Medicare threshold
        TaxCalculationService.PaymentCalculation calculation = new TaxCalculationService.PaymentCalculation();
        calculation.setEmployeeId("EMP003");
        calculation.setGrossAmount(new BigDecimal("10000.00"));
        calculation.setYtdGross(new BigDecimal("200000.00")); // At threshold
        calculation.setFilingStatus("SINGLE");
        calculation.setExemptions(1);
        calculation.setState("CA");

        // When
        TaxCalculationService.EmployeeTaxCalculation result =
            taxCalculationService.calculateEmployeeTaxes(calculation);

        // Then
        // Base Medicare (1.45%) + Additional Medicare (0.9%)
        BigDecimal expectedBaseMedicare = new BigDecimal("10000.00").multiply(new BigDecimal("0.0145"));
        BigDecimal expectedAdditionalMedicare = new BigDecimal("10000.00").multiply(new BigDecimal("0.009"));
        BigDecimal expectedTotal = expectedBaseMedicare.add(expectedAdditionalMedicare);

        assertThat(result.getMedicareTax()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("Should calculate payroll taxes for batch correctly")
    void testCalculatePayrollTaxes_Batch() {
        // Given
        List<TaxCalculationService.PaymentCalculation> calculations = List.of(
            createPaymentCalculation("EMP001", "5000.00", "50000.00"),
            createPaymentCalculation("EMP002", "6000.00", "60000.00"),
            createPaymentCalculation("EMP003", "7000.00", "70000.00")
        );

        // When
        TaxCalculationService.TaxCalculationResult result =
            taxCalculationService.calculatePayrollTaxes("COMP001", calculations, LocalDate.now());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCompanyId()).isEqualTo("COMP001");
        assertThat(result.getTotalEmployees()).isEqualTo(3);

        // Verify total gross
        assertThat(result.getTotalGrossAmount()).isEqualByComparingTo("18000.00");

        // Verify total Social Security tax (6.2% of 18000)
        assertThat(result.getTotalSocialSecurityTax()).isEqualByComparingTo("1116.00");

        // Verify total Medicare tax (1.45% of 18000)
        assertThat(result.getTotalMedicareTax()).isEqualByComparingTo("261.00");
    }

    @Test
    @DisplayName("Should handle zero gross amount gracefully")
    void testCalculateEmployeeTaxes_ZeroGross() {
        // Given
        TaxCalculationService.PaymentCalculation calculation = new TaxCalculationService.PaymentCalculation();
        calculation.setEmployeeId("EMP004");
        calculation.setGrossAmount(BigDecimal.ZERO);
        calculation.setYtdGross(new BigDecimal("10000.00"));
        calculation.setFilingStatus("SINGLE");
        calculation.setExemptions(1);
        calculation.setState("CA");

        // When
        TaxCalculationService.EmployeeTaxCalculation result =
            taxCalculationService.calculateEmployeeTaxes(calculation);

        // Then
        assertThat(result.getGrossAmount()).isEqualByComparingTo("0.00");
        assertThat(result.getSocialSecurityTax()).isEqualByComparingTo("0.00");
        assertThat(result.getMedicareTax()).isEqualByComparingTo("0.00");
        assertThat(result.getNetAmount()).isEqualByComparingTo("0.00");
    }

    // Helper method
    private TaxCalculationService.PaymentCalculation createPaymentCalculation(
            String employeeId, String grossAmount, String ytdGross) {

        TaxCalculationService.PaymentCalculation calc = new TaxCalculationService.PaymentCalculation();
        calc.setEmployeeId(employeeId);
        calc.setGrossAmount(new BigDecimal(grossAmount));
        calc.setYtdGross(new BigDecimal(ytdGross));
        calc.setFilingStatus("SINGLE");
        calc.setExemptions(1);
        calc.setState("CA");
        calc.setCity("San Francisco");
        return calc;
    }
}
