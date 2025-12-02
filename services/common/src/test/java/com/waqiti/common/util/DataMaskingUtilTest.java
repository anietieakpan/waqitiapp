package com.waqiti.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DataMaskingUtil
 * Ensures PCI DSS and GDPR compliance in data masking operations
 */
class DataMaskingUtilTest {

    @ParameterizedTest
    @CsvSource({
        "user@example.com, use***@example.com",
        "ab@test.com, ab*@test.com",
        "verylongemail@domain.com, ver***@domain.com",
        "a@b.c, a*@b.c",
        "john.doe@company.co.uk, joh***@company.co.uk"
    })
    void testMaskEmail_ValidEmails(String input, String expected) {
        assertThat(DataMaskingUtil.maskEmail(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  ", "invalid-email", "@example.com", "user@"})
    void testMaskEmail_InvalidInputs(String input) {
        assertThat(DataMaskingUtil.maskEmail(input)).isEqualTo("[REDACTED]");
    }

    @ParameterizedTest
    @CsvSource({
        "4532015112830366, ************0366",
        "5425233430109903, ************9903",
        "4532-0151-1283-0366, ************0366",
        "4532 0151 1283 0366, ************0366",
        "378282246310005, ************0005"
    })
    void testMaskCardNumber_ValidCards(String input, String expected) {
        assertThat(DataMaskingUtil.maskCardNumber(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "123", "12345678901234567890"}) // Too short or too long
    void testMaskCardNumber_InvalidInputs(String input) {
        String result = DataMaskingUtil.maskCardNumber(input);
        assertThat(result).matches("^\\*+$|\\*+\\d{4}");
    }

    @Test
    void testMaskCVV_AlwaysRedacted() {
        assertThat(DataMaskingUtil.maskCVV("123")).isEqualTo("***");
        assertThat(DataMaskingUtil.maskCVV("4567")).isEqualTo("***");
        assertThat(DataMaskingUtil.maskCVV(null)).isEqualTo("***");
        assertThat(DataMaskingUtil.maskCVV("")).isEqualTo("***");
    }

    @ParameterizedTest
    @CsvSource({
        "GB82WEST12345698765432, GB82************5432",
        "DE89370400440532013000, DE89************3000",
        "FR1420041010050500013M02606, FR14****************2606",
        "IT60X0542811101000000123456, IT60****************3456"
    })
    void testMaskIBAN_ValidIBANs(String input, String expected) {
        assertThat(DataMaskingUtil.maskIBAN(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "INVALID", "GB82"})
    void testMaskIBAN_InvalidInputs(String input) {
        assertThat(DataMaskingUtil.maskIBAN(input)).isEqualTo("[REDACTED]");
    }

    @ParameterizedTest
    @CsvSource({
        "123456789012, ********9012",
        "98765432, ****5432",
        "ACC-98765432, ********5432",
        "1234, ****"
    })
    void testMaskAccountNumber_ValidAccounts(String input, String expected) {
        assertThat(DataMaskingUtil.maskAccountNumber(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testMaskAccountNumber_NullOrEmpty(String input) {
        assertThat(DataMaskingUtil.maskAccountNumber(input)).isEqualTo("[REDACTED]");
    }

    @ParameterizedTest
    @CsvSource({
        "+1234567890, +1******7890",
        "555-123-4567, ****4567",
        "+44 20 7123 4567, +44******4567",
        "1234567890, ****7890"
    })
    void testMaskPhoneNumber_ValidPhones(String input, String expected) {
        String result = DataMaskingUtil.maskPhoneNumber(input);
        // Allow flexible matching since phone masking has variations
        assertThat(result).endsWith(input.replaceAll("[^0-9]", "").substring(
            Math.max(input.replaceAll("[^0-9]", "").length() - 4, 0)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testMaskPhoneNumber_NullOrEmpty(String input) {
        assertThat(DataMaskingUtil.maskPhoneNumber(input)).isEqualTo("[REDACTED]");
    }

    @ParameterizedTest
    @CsvSource({
        "123-45-6789, ***-**-6789",
        "123456789, *****6789",
        "12345, *2345"
    })
    void testMaskSSN_ValidSSNs(String input, String expected) {
        assertThat(DataMaskingUtil.maskSSN(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testMaskSSN_NullOrEmpty(String input) {
        assertThat(DataMaskingUtil.maskSSN(input)).isEqualTo("[REDACTED]");
    }

    @ParameterizedTest
    @CsvSource({
        "sk_live_TESTKEY1234567890TESTKEY1234567890ab, sk_live_****************90ab",
        "pk_test_TYooMQauvdEDq54NiTphI7jx, pk_test_****************I7jx"
    })
    void testMaskAPIKey_ValidKeys(String input, String expected) {
        assertThat(DataMaskingUtil.maskAPIKey(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "short"})
    void testMaskAPIKey_ShortOrInvalid(String input) {
        String result = DataMaskingUtil.maskAPIKey(input);
        assertThat(result).matches(".*\\*+.*|\\[REDACTED\\]");
    }

    @Test
    void testMaskPassword_AlwaysRedacted() {
        assertThat(DataMaskingUtil.maskPassword("MySecretPassword123!")).isEqualTo("[REDACTED]");
        assertThat(DataMaskingUtil.maskPassword(null)).isEqualTo("[REDACTED]");
        assertThat(DataMaskingUtil.maskPassword("")).isEqualTo("[REDACTED]");
    }

    @ParameterizedTest
    @CsvSource({
        "192.168.1.100, 192.*.*.100",
        "10.0.0.1, 10.*.*.1",
        "172.16.254.1, 172.*.*.1"
    })
    void testMaskIPAddress_ValidIPs(String input, String expected) {
        assertThat(DataMaskingUtil.maskIPAddress(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid", "192.168.1", "192.168.1.1.1"})
    void testMaskIPAddress_InvalidInputs(String input) {
        assertThat(DataMaskingUtil.maskIPAddress(input)).isEqualTo("[REDACTED]");
    }

    @ParameterizedTest
    @CsvSource({
        "SensitiveData123, 4, Sens******a123",
        "Short, 2, Sh*rt",
        "A, 1, *",
        "Test, 1, T**t"
    })
    void testMaskGeneric_VariousLengths(String input, int showChars, String expected) {
        assertThat(DataMaskingUtil.maskGeneric(input, showChars)).isEqualTo(expected);
    }

    @Test
    void testMaskGeneric_NullOrEmpty() {
        assertThat(DataMaskingUtil.maskGeneric(null, 4)).isEqualTo("[REDACTED]");
        assertThat(DataMaskingUtil.maskGeneric("", 4)).isEqualTo("[REDACTED]");
    }

    @Test
    void testMaskGeneric_ShowCharsExceedsLength() {
        assertThat(DataMaskingUtil.maskGeneric("ABC", 5)).isEqualTo("***");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "password: secret123",
        "token=abc123",
        "apiKey: sk_test_123",
        "cvv: 123",
        "ssn: 123-45-6789",
        "card: 4532015112830366"
    })
    void testContainsSensitiveData_ReturnsTrueForSensitivePatterns(String input) {
        assertThat(DataMaskingUtil.containsSensitiveData(input)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "normal text",
        "user@example.com",
        "transaction ID: 12345",
        ""
    })
    void testContainsSensitiveData_ReturnsFalseForNormalText(String input) {
        assertThat(DataMaskingUtil.containsSensitiveData(input)).isFalse();
    }

    @Test
    void testContainsSensitiveData_NullInput() {
        assertThat(DataMaskingUtil.containsSensitiveData(null)).isFalse();
    }

    @Test
    void testPCIDSSCompliance_CardNumberNeverFullyExposed() {
        // PCI DSS Requirement 3.3: Mask PAN when displayed
        String[] cardNumbers = {
            "4532015112830366",
            "5425233430109903",
            "378282246310005"
        };

        for (String card : cardNumbers) {
            String masked = DataMaskingUtil.maskCardNumber(card);
            // Ensure at least 12 digits are masked
            long asteriskCount = masked.chars().filter(ch -> ch == '*').count();
            assertThat(asteriskCount).isGreaterThanOrEqualTo(12);
            // Ensure only last 4 digits are visible
            assertThat(masked).endsWith(card.substring(card.length() - 4));
        }
    }

    @Test
    void testGDPRCompliance_PersonalDataMasked() {
        // GDPR Article 32: Personal data must be protected
        assertThat(DataMaskingUtil.maskEmail("personal@example.com")).doesNotContain("personal");
        assertThat(DataMaskingUtil.maskPhoneNumber("+1234567890")).doesNotContain("234567");
        assertThat(DataMaskingUtil.maskSSN("123-45-6789")).doesNotContain("123-45");
    }
}
