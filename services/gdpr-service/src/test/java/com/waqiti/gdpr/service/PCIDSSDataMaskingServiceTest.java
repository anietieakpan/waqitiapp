package com.waqiti.gdpr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for PCI-DSS compliant data masking.
 *
 * Tests cover:
 * - PAN masking (various formats)
 * - CVV/PIN removal
 * - SSN masking
 * - Account number masking
 * - Prohibited field removal
 * - Compliance validation
 * - Nested object masking
 * - Edge cases and security violations
 *
 * Target Coverage: 95%+
 */
@DisplayName("PCI-DSS Data Masking Service Tests")
class PCIDSSDataMaskingServiceTest {

    private PCIDSSDataMaskingService maskingService;

    @BeforeEach
    void setUp() {
        maskingService = new PCIDSSDataMaskingService();
    }

    @Nested
    @DisplayName("PAN (Primary Account Number) Masking Tests")
    class PANMaskingTests {

        @Test
        @DisplayName("Should mask Visa card (16 digits) to show only last 4")
        void testMaskVisaCard() {
            // Given
            Map<String, Object> exportData = createPaymentMethodWithPAN("4532123456789012");

            // When
            Map<String, Object> masked = maskingService.maskExportData(exportData);

            // Then
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> methods = (List<Map<String, Object>>) masked.get("paymentMethods");
            String maskedPAN = methods.get(0).get("cardNumber").toString();

            assertEquals("************9012", maskedPAN, "PAN should show only last 4 digits");
            assertFalse(maskedPAN.contains("4532"), "PAN should not contain first 4 digits");
        }

        @Test
        @DisplayName("Should mask Mastercard (16 digits) to show only last 4")
        void testMaskMastercard() {
            Map<String, Object> exportData = createPaymentMethodWithPAN("5425233430109903");
            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> methods = (List<Map<String, Object>>) masked.get("paymentMethods");
            String maskedPAN = methods.get(0).get("cardNumber").toString();

            assertEquals("************9903", maskedPAN);
        }

        @Test
        @DisplayName("Should mask Amex (15 digits) to show only last 4")
        void testMaskAmex() {
            Map<String, Object> exportData = createPaymentMethodWithPAN("378282246310005");
            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> methods = (List<Map<String, Object>>) masked.get("paymentMethods");
            String maskedPAN = methods.get(0).get("cardNumber").toString();

            assertEquals("************0005", maskedPAN);
        }

        @ParameterizedTest
        @DisplayName("Should mask various PAN formats")
        @CsvSource({
            "4532123456789012, ************9012",
            "5425233430109903, ************9903",
            "378282246310005, ************0005",
            "6011111111111117, ************1117"
        })
        void testMaskVariousPANFormats(String pan, String expected) {
            Map<String, Object> exportData = createPaymentMethodWithPAN(pan);
            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> methods = (List<Map<String, Object>>) masked.get("paymentMethods");
            String maskedPAN = methods.get(0).get("cardNumber").toString();

            assertEquals(expected, maskedPAN);
        }

        @Test
        @DisplayName("Should handle PAN with spaces and dashes")
        void testMaskPANWithFormatting() {
            Map<String, Object> exportData = createPaymentMethodWithPAN("4532-1234-5678-9012");
            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> methods = (List<Map<String, Object>>) masked.get("paymentMethods");
            String maskedPAN = methods.get(0).get("cardNumber").toString();

            assertEquals("************9012", maskedPAN, "Should strip formatting and mask");
        }

        @Test
        @DisplayName("Should handle invalid PAN gracefully")
        void testMaskInvalidPAN() {
            Map<String, Object> exportData = createPaymentMethodWithPAN("12345"); // Too short
            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> methods = (List<Map<String, Object>>) masked.get("paymentMethods");
            String maskedPAN = methods.get(0).get("cardNumber").toString();

            assertEquals("****", maskedPAN, "Invalid PAN should be completely masked");
        }

        @Test
        @DisplayName("Should handle null PAN")
        void testMaskNullPAN() {
            Map<String, Object> exportData = createPaymentMethodWithPAN(null);
            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> methods = (List<Map<String, Object>>) masked.get("paymentMethods");
            String maskedPAN = methods.get(0).get("cardNumber").toString();

            assertEquals("****", maskedPAN);
        }
    }

    @Nested
    @DisplayName("CVV/PIN Removal Tests (CRITICAL)")
    class CVVPINRemovalTests {

        @Test
        @DisplayName("CRITICAL: Should NEVER export CVV")
        void testRemoveCVV() {
            Map<String, Object> exportData = new HashMap<>();
            List<Map<String, Object>> methods = new ArrayList<>();
            Map<String, Object> method = new HashMap<>();
            method.put("cardNumber", "4532123456789012");
            method.put("cvv", "123");
            methods.add(method);
            exportData.put("paymentMethods", methods);

            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> maskedMethods = (List<Map<String, Object>>) masked.get("paymentMethods");

            assertEquals("[REDACTED FOR SECURITY]", maskedMethods.get(0).get("cvv"),
                "CVV must be redacted for PCI-DSS compliance");
        }

        @Test
        @DisplayName("CRITICAL: Should NEVER export CVV2")
        void testRemoveCVV2() {
            Map<String, Object> exportData = new HashMap<>();
            List<Map<String, Object>> methods = new ArrayList<>();
            Map<String, Object> method = new HashMap<>();
            method.put("cardNumber", "4532123456789012");
            method.put("cvv2", "456");
            methods.add(method);
            exportData.put("paymentMethods", methods);

            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> maskedMethods = (List<Map<String, Object>>) masked.get("paymentMethods");

            assertEquals("[REDACTED FOR SECURITY]", maskedMethods.get(0).get("cvv2"));
        }

        @Test
        @DisplayName("CRITICAL: Should NEVER export CVC")
        void testRemoveCVC() {
            Map<String, Object> exportData = new HashMap<>();
            List<Map<String, Object>> methods = new ArrayList<>();
            Map<String, Object> method = new HashMap<>();
            method.put("cardNumber", "4532123456789012");
            method.put("cvc", "789");
            methods.add(method);
            exportData.put("paymentMethods", methods);

            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> maskedMethods = (List<Map<String, Object>>) masked.get("paymentMethods");

            assertEquals("[REDACTED FOR SECURITY]", maskedMethods.get(0).get("cvc"));
        }

        @Test
        @DisplayName("CRITICAL: Should NEVER export PIN")
        void testRemovePIN() {
            Map<String, Object> exportData = new HashMap<>();
            List<Map<String, Object>> methods = new ArrayList<>();
            Map<String, Object> method = new HashMap<>();
            method.put("cardNumber", "4532123456789012");
            method.put("pin", "1234");
            methods.add(method);
            exportData.put("paymentMethods", methods);

            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> maskedMethods = (List<Map<String, Object>>) masked.get("paymentMethods");

            assertEquals("[REDACTED FOR SECURITY]", maskedMethods.get(0).get("pin"));
        }

        @Test
        @DisplayName("CRITICAL: Should remove magnetic stripe data (track1/track2)")
        void testRemoveMagneticStripeData() {
            Map<String, Object> exportData = new HashMap<>();
            List<Map<String, Object>> methods = new ArrayList<>();
            Map<String, Object> method = new HashMap<>();
            method.put("cardNumber", "4532123456789012");
            method.put("track1Data", "B4532123456789012^DOE/JOHN^2512101");
            method.put("track2Data", "4532123456789012=251210100001");
            methods.add(method);
            exportData.put("paymentMethods", methods);

            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> maskedMethods = (List<Map<String, Object>>) masked.get("paymentMethods");

            assertFalse(maskedMethods.get(0).containsKey("track1Data"),
                "Track1 data must be completely removed");
            assertFalse(maskedMethods.get(0).containsKey("track2Data"),
                "Track2 data must be completely removed");
        }
    }

    @Nested
    @DisplayName("SSN/Tax ID Masking Tests")
    class SSNMaskingTests {

        @Test
        @DisplayName("Should mask SSN to format ***-**-1234")
        void testMaskSSN() {
            Map<String, Object> exportData = new HashMap<>();
            Map<String, Object> taxInfo = new HashMap<>();
            taxInfo.put("ssn", "123-45-6789");
            exportData.put("taxInformation", taxInfo);

            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            Map<String, Object> maskedTax = (Map<String, Object>) masked.get("taxInformation");

            assertEquals("***-**-6789", maskedTax.get("ssn"),
                "SSN should show only last 4 digits");
        }

        @Test
        @DisplayName("Should mask SSN without dashes")
        void testMaskSSNNoDashes() {
            Map<String, Object> exportData = new HashMap<>();
            Map<String, Object> taxInfo = new HashMap<>();
            taxInfo.put("ssn", "123456789");
            exportData.put("taxInformation", taxInfo);

            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            Map<String, Object> maskedTax = (Map<String, Object>) masked.get("taxInformation");

            assertEquals("***-**-6789", maskedTax.get("ssn"));
        }

        @Test
        @DisplayName("Should handle invalid SSN")
        void testMaskInvalidSSN() {
            Map<String, Object> exportData = new HashMap<>();
            Map<String, Object> taxInfo = new HashMap<>();
            taxInfo.put("ssn", "12345"); // Too short
            exportData.put("taxInformation", taxInfo);

            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            Map<String, Object> maskedTax = (Map<String, Object>) masked.get("taxInformation");

            assertEquals("***-**-****", maskedTax.get("ssn"),
                "Invalid SSN should be completely masked");
        }
    }

    @Nested
    @DisplayName("PCI-DSS Compliance Validation Tests")
    class ComplianceValidationTests {

        @Test
        @DisplayName("Should detect unmasked PAN as compliance violation")
        void testDetectUnmaskedPAN() {
            Map<String, Object> exportData = new HashMap<>();
            exportData.put("cardInfo", "4532123456789012"); // Full PAN

            boolean compliant = maskingService.validatePCIDSSCompliance(exportData);

            assertFalse(compliant, "Unmasked PAN should be detected as violation");
        }

        @Test
        @DisplayName("Should detect CVV presence as compliance violation")
        void testDetectCVVPresence() {
            Map<String, Object> exportData = new HashMap<>();
            List<Map<String, Object>> methods = new ArrayList<>();
            Map<String, Object> method = new HashMap<>();
            method.put("cvv", "123");
            methods.add(method);
            exportData.put("paymentMethods", methods);

            boolean compliant = maskingService.validatePCIDSSCompliance(exportData);

            assertFalse(compliant, "CVV presence should be detected as violation");
        }

        @Test
        @DisplayName("Should validate properly masked data as compliant")
        void testValidateMaskedDataCompliant() {
            Map<String, Object> exportData = new HashMap<>();
            List<Map<String, Object>> methods = new ArrayList<>();
            Map<String, Object> method = new HashMap<>();
            method.put("cardNumber", "************9012"); // Properly masked
            method.put("expiryDate", "12/25"); // Allowed
            method.put("cardholderName", "John Doe"); // Allowed
            methods.add(method);
            exportData.put("paymentMethods", methods);

            boolean compliant = maskingService.validatePCIDSSCompliance(exportData);

            assertTrue(compliant, "Properly masked data should be compliant");
        }

        @Test
        @DisplayName("Should detect prohibited fields in nested objects")
        void testDetectProhibitedFieldsInNestedObjects() {
            Map<String, Object> exportData = new HashMap<>();
            Map<String, Object> nested = new HashMap<>();
            Map<String, Object> deepNested = new HashMap<>();
            deepNested.put("cvv", "123");
            nested.put("paymentData", deepNested);
            exportData.put("transaction", nested);

            boolean compliant = maskingService.validatePCIDSSCompliance(exportData);

            assertFalse(compliant, "Prohibited fields in nested objects should be detected");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Security Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty export data")
        void testHandleEmptyData() {
            Map<String, Object> exportData = new HashMap<>();

            assertDoesNotThrow(() -> maskingService.maskExportData(exportData));
        }

        @Test
        @DisplayName("Should handle null export data gracefully")
        void testHandleNullData() {
            Map<String, Object> exportData = new HashMap<>();
            exportData.put("paymentMethods", null);

            assertDoesNotThrow(() -> maskingService.maskExportData(exportData));
        }

        @Test
        @DisplayName("Should mask PAN in transaction history")
        void testMaskPANInTransactions() {
            Map<String, Object> exportData = new HashMap<>();
            List<Map<String, Object>> transactions = new ArrayList<>();
            Map<String, Object> txn = new HashMap<>();
            txn.put("cardNumber", "4532123456789012");
            transactions.add(txn);
            exportData.put("transactions", transactions);

            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> maskedTxns = (List<Map<String, Object>>) masked.get("transactions");

            assertEquals("************9012", maskedTxns.get(0).get("cardNumber"));
        }

        @Test
        @DisplayName("Should handle deeply nested structures")
        void testDeeplyNestedStructures() {
            Map<String, Object> exportData = new HashMap<>();
            Map<String, Object> level1 = new HashMap<>();
            Map<String, Object> level2 = new HashMap<>();
            List<Map<String, Object>> level3 = new ArrayList<>();
            Map<String, Object> method = new HashMap<>();
            method.put("cardNumber", "4532123456789012");
            method.put("cvv", "123");
            level3.add(method);
            level2.put("paymentMethods", level3);
            level1.put("nested", level2);
            exportData.put("userData", level1);

            Map<String, Object> masked = maskingService.maskExportData(exportData);

            // Verify deep masking occurred
            @SuppressWarnings("unchecked")
            Map<String, Object> l1 = (Map<String, Object>) masked.get("userData");
            @SuppressWarnings("unchecked")
            Map<String, Object> l2 = (Map<String, Object>) l1.get("nested");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> l3 = (List<Map<String, Object>>) l2.get("paymentMethods");

            assertEquals("[REDACTED FOR SECURITY]", l3.get(0).get("cvv"),
                "CVV should be redacted even in deeply nested structures");
        }

        @Test
        @DisplayName("Should preserve allowed fields (expiry, cardholder name)")
        void testPreserveAllowedFields() {
            Map<String, Object> exportData = new HashMap<>();
            List<Map<String, Object>> methods = new ArrayList<>();
            Map<String, Object> method = new HashMap<>();
            method.put("cardNumber", "4532123456789012");
            method.put("expiryDate", "12/25");
            method.put("cardholderName", "John Doe");
            methods.add(method);
            exportData.put("paymentMethods", methods);

            Map<String, Object> masked = maskingService.maskExportData(exportData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> maskedMethods = (List<Map<String, Object>>) masked.get("paymentMethods");

            assertEquals("12/25", maskedMethods.get(0).get("expiryDate"),
                "Expiry date should be preserved");
            assertEquals("John Doe", maskedMethods.get(0).get("cardholderName"),
                "Cardholder name should be preserved");
        }
    }

    // Helper methods

    private Map<String, Object> createPaymentMethodWithPAN(String pan) {
        Map<String, Object> exportData = new HashMap<>();
        List<Map<String, Object>> methods = new ArrayList<>();
        Map<String, Object> method = new HashMap<>();
        method.put("cardNumber", pan);
        methods.add(method);
        exportData.put("paymentMethods", methods);
        return exportData;
    }
}
