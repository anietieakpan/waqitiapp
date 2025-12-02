package com.waqiti.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for SensitiveDataMasker utility class.
 *
 * <p>This test suite validates PCI DSS and GDPR compliance for logging sensitive data.
 *
 * @author Waqiti Security Team
 * @version 1.0
 * @since 2025-10-18
 */
@DisplayName("SensitiveDataMasker Tests")
class SensitiveDataMaskerTest {

    @Nested
    @DisplayName("Session Token Masking Tests")
    class SessionTokenMaskingTests {

        @Test
        @DisplayName("Should mask session token and return consistent session ID")
        void shouldMaskSessionTokenConsistently() {
            String sessionToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ";

            String masked1 = SensitiveDataMasker.maskSessionToken(sessionToken);
            String masked2 = SensitiveDataMasker.maskSessionToken(sessionToken);

            // Should return consistent ID for same token
            assertEquals(masked1, masked2);

            // Should not contain the original token
            assertFalse(masked1.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));

            // Should be in correct format
            assertTrue(masked1.startsWith("[SESSION:"));
            assertTrue(masked1.endsWith("]"));
        }

        @Test
        @DisplayName("Should handle null session token")
        void shouldHandleNullSessionToken() {
            String result = SensitiveDataMasker.maskSessionToken(null);
            assertEquals("[NO_SESSION]", result);
        }

        @Test
        @DisplayName("Should handle empty session token")
        void shouldHandleEmptySessionToken() {
            String result = SensitiveDataMasker.maskSessionToken("");
            assertEquals("[NO_SESSION]", result);
        }

        @Test
        @DisplayName("Should generate different IDs for different tokens")
        void shouldGenerateDifferentIdsForDifferentTokens() {
            String token1 = "session-token-abc-123";
            String token2 = "session-token-xyz-789";

            String masked1 = SensitiveDataMasker.maskSessionToken(token1);
            String masked2 = SensitiveDataMasker.maskSessionToken(token2);

            assertNotEquals(masked1, masked2);
        }

        @Test
        @DisplayName("Should handle JWT-like tokens")
        void shouldHandleJwtTokens() {
            String jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature";

            String masked = SensitiveDataMasker.maskSessionToken(jwtToken);

            assertNotNull(masked);
            assertTrue(masked.startsWith("[SESSION:"));
            assertFalse(masked.contains("payload"));
            assertFalse(masked.contains("signature"));
        }
    }

    @Nested
    @DisplayName("Email Masking Tests")
    class EmailMaskingTests {

        @Test
        @DisplayName("Should mask standard email address")
        void shouldMaskStandardEmail() {
            String email = "john.doe@example.com";
            String masked = SensitiveDataMasker.maskEmail(email);

            assertEquals("j***@example.com", masked);
        }

        @Test
        @DisplayName("Should mask single character email")
        void shouldMaskSingleCharacterEmail() {
            String email = "a@example.com";
            String masked = SensitiveDataMasker.maskEmail(email);

            assertEquals("a***@example.com", masked);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty email")
        void shouldHandleNullAndEmptyEmail(String email) {
            String result = SensitiveDataMasker.maskEmail(email);
            assertEquals("", result);
        }

        @Test
        @DisplayName("Should mask email with plus addressing")
        void shouldMaskEmailWithPlusAddressing() {
            String email = "john.doe+test@example.com";
            String masked = SensitiveDataMasker.maskEmail(email);

            assertTrue(masked.endsWith("@example.com"));
            assertTrue(masked.startsWith("j"));
            assertTrue(masked.contains("***"));
        }

        @Test
        @DisplayName("Should handle malformed email")
        void shouldHandleMalformedEmail() {
            String email = "notanemail";
            String masked = SensitiveDataMasker.maskEmail(email);

            assertEquals("***MASKED***", masked);
        }
    }

    @Nested
    @DisplayName("Phone Number Masking Tests")
    class PhoneNumberMaskingTests {

        @Test
        @DisplayName("Should mask phone number showing only last 4 digits")
        void shouldMaskPhoneNumber() {
            String phone = "+1234567890";
            String masked = SensitiveDataMasker.maskPhoneNumber(phone);

            assertEquals("***7890", masked);
        }

        @Test
        @DisplayName("Should mask phone with dashes and spaces")
        void shouldMaskPhoneWithFormatting() {
            String phone = "+1 (234) 567-8900";
            String masked = SensitiveDataMasker.maskPhoneNumber(phone);

            assertEquals("***8900", masked);
        }

        @Test
        @DisplayName("Should handle short phone numbers")
        void shouldHandleShortPhone() {
            String phone = "1234";
            String masked = SensitiveDataMasker.maskPhoneNumber(phone);

            assertEquals("****", masked);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty phone")
        void shouldHandleNullAndEmptyPhone(String phone) {
            String result = SensitiveDataMasker.maskPhoneNumber(phone);
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("Credit Card Masking Tests - PCI DSS Compliance")
    class CreditCardMaskingTests {

        @Test
        @DisplayName("Should mask Visa card (16 digits) - PCI DSS compliant")
        void shouldMaskVisaCard() {
            String cardNumber = "4532123456789012";
            String masked = SensitiveDataMasker.maskCardNumber(cardNumber);

            // PCI DSS: Only last 4 digits should be visible
            assertEquals("****9012", masked);
        }

        @Test
        @DisplayName("Should mask American Express card (15 digits)")
        void shouldMaskAmexCard() {
            String cardNumber = "371234567890123";
            String masked = SensitiveDataMasker.maskCardNumber(cardNumber);

            assertEquals("****0123", masked);
        }

        @Test
        @DisplayName("Should mask card with spaces")
        void shouldMaskCardWithSpaces() {
            String cardNumber = "4532 1234 5678 9012";
            String masked = SensitiveDataMasker.maskCardNumber(cardNumber);

            assertEquals("****9012", masked);
        }

        @Test
        @DisplayName("Should mask card with dashes")
        void shouldMaskCardWithDashes() {
            String cardNumber = "4532-1234-5678-9012";
            String masked = SensitiveDataMasker.maskCardNumber(cardNumber);

            assertEquals("****9012", masked);
        }

        @Test
        @DisplayName("Should reject short card numbers")
        void shouldRejectShortCardNumber() {
            String cardNumber = "123456";
            String masked = SensitiveDataMasker.maskCardNumber(cardNumber);

            assertEquals("***MASKED***", masked);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty card numbers")
        void shouldHandleNullAndEmptyCard(String cardNumber) {
            String result = SensitiveDataMasker.maskCardNumber(cardNumber);
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("SSN Masking Tests")
    class SsnMaskingTests {

        @Test
        @DisplayName("Should mask SSN showing only last 4 digits")
        void shouldMaskSsn() {
            String ssn = "123-45-6789";
            String masked = SensitiveDataMasker.maskSSN(ssn);

            assertEquals("***-**-6789", masked);
        }

        @Test
        @DisplayName("Should handle SSN without formatting")
        void shouldHandleUnformattedSsn() {
            String ssn = "123456789";
            String masked = SensitiveDataMasker.maskSSN(ssn);

            assertEquals("***MASKED***", masked);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty SSN")
        void shouldHandleNullAndEmptySsn(String ssn) {
            String result = SensitiveDataMasker.maskSSN(ssn);
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("API Key Masking Tests")
    class ApiKeyMaskingTests {

        @Test
        @DisplayName("Should mask Stripe API key")
        void shouldMaskStripeApiKey() {
            String apiKey = "sk_live_51H3abc123def456";
            String masked = SensitiveDataMasker.maskApiKey(apiKey);

            assertEquals("sk_live_***", masked);
        }

        @Test
        @DisplayName("Should mask generic API key")
        void shouldMaskGenericApiKey() {
            String apiKey = "api_key_abc123def456ghi789";
            String masked = SensitiveDataMasker.maskApiKey(apiKey);

            assertTrue(masked.endsWith("***"));
            assertTrue(masked.length() < apiKey.length());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty API key")
        void shouldHandleNullAndEmptyApiKey(String apiKey) {
            String result = SensitiveDataMasker.maskApiKey(apiKey);
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("Password Masking Tests")
    class PasswordMaskingTests {

        @Test
        @DisplayName("Should always redact passwords")
        void shouldAlwaysRedactPassword() {
            String password = "SuperSecretPassword123!";
            String masked = SensitiveDataMasker.maskPassword(password);

            assertEquals("***REDACTED***", masked);
            assertFalse(masked.contains("Super"));
            assertFalse(masked.contains("Secret"));
        }

        @Test
        @DisplayName("Should redact null password")
        void shouldRedactNullPassword() {
            String masked = SensitiveDataMasker.maskPassword(null);
            assertEquals("***REDACTED***", masked);
        }

        @Test
        @DisplayName("Should never log any part of password")
        void shouldNeverLogPasswordParts() {
            String[] passwords = {
                "password123",
                "P@ssw0rd!",
                "very-long-password-with-special-chars-12345",
                ""
            };

            for (String password : passwords) {
                String masked = SensitiveDataMasker.maskPassword(password);
                assertEquals("***REDACTED***", masked);
                assertFalse(masked.contains(password));
            }
        }
    }

    @Nested
    @DisplayName("User ID Formatting Tests")
    class UserIdFormattingTests {

        @Test
        @DisplayName("Should format valid UUID for logging")
        void shouldFormatValidUuid() {
            UUID userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
            String formatted = SensitiveDataMasker.formatUserIdForLogging(userId);

            assertEquals("[USER:123e4567-e89b-12d3-a456-426614174000]", formatted);
        }

        @Test
        @DisplayName("Should handle null user ID")
        void shouldHandleNullUserId() {
            String formatted = SensitiveDataMasker.formatUserIdForLogging(null);
            assertEquals("[NO_USER]", formatted);
        }
    }

    @Nested
    @DisplayName("Sensitive Data Detection Tests")
    class SensitiveDataDetectionTests {

        @Test
        @DisplayName("Should mask emails in free text")
        void shouldMaskEmailsInText() {
            String text = "Please contact john.doe@example.com for assistance";
            String sanitized = SensitiveDataMasker.maskSensitiveData(text);

            assertTrue(sanitized.contains("j***@example.com"));
            assertFalse(sanitized.contains("john.doe@example.com"));
        }

        @Test
        @DisplayName("Should mask credit cards in free text")
        void shouldMaskCardsInText() {
            String text = "Card number: 4532123456789012 was charged";
            String sanitized = SensitiveDataMasker.maskSensitiveData(text);

            assertTrue(sanitized.contains("****9012"));
            assertFalse(sanitized.contains("4532123456789012"));
        }

        @Test
        @DisplayName("Should mask SSN in free text")
        void shouldMaskSsnInText() {
            String text = "SSN 123-45-6789 on file";
            String sanitized = SensitiveDataMasker.maskSensitiveData(text);

            assertTrue(sanitized.contains("***-**-6789"));
            assertFalse(sanitized.contains("123-45-6789"));
        }

        @Test
        @DisplayName("Should mask multiple types in same text")
        void shouldMaskMultipleTypesInText() {
            String text = "User john.doe@example.com with card 4532123456789012 and SSN 123-45-6789";
            String sanitized = SensitiveDataMasker.maskSensitiveData(text);

            assertFalse(sanitized.contains("john.doe@example.com"));
            assertFalse(sanitized.contains("4532123456789012"));
            assertFalse(sanitized.contains("123-45-6789"));

            assertTrue(sanitized.contains("j***@example.com"));
            assertTrue(sanitized.contains("****9012"));
            assertTrue(sanitized.contains("***-**-6789"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty text")
        void shouldHandleNullAndEmptyText(String text) {
            String result = SensitiveDataMasker.maskSensitiveData(text);
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("Transaction Log Context Tests")
    class TransactionLogContextTests {

        @Test
        @DisplayName("Should create properly formatted transaction log context")
        void shouldCreateTransactionLogContext() {
            UUID userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
            UUID transactionId = UUID.fromString("987e6543-e21a-43b2-b789-123456789abc");
            String amount = "100.50";
            String currency = "USD";

            String context = SensitiveDataMasker.createTransactionLogContext(
                userId, transactionId, amount, currency
            );

            assertTrue(context.contains("[USER:123e4567-e89b-12d3-a456-426614174000]"));
            assertTrue(context.contains("987e6543-e21a-43b2-b789-123456789abc"));
            assertTrue(context.contains("100.50"));
            assertTrue(context.contains("USD"));
        }

        @Test
        @DisplayName("Should handle null user ID in transaction context")
        void shouldHandleNullUserIdInContext() {
            UUID transactionId = UUID.fromString("987e6543-e21a-43b2-b789-123456789abc");

            String context = SensitiveDataMasker.createTransactionLogContext(
                null, transactionId, "50.00", "EUR"
            );

            assertTrue(context.contains("[NO_USER]"));
        }
    }

    @Nested
    @DisplayName("PCI DSS Compliance Tests")
    class PciDssComplianceTests {

        @Test
        @DisplayName("PCI DSS 3.4 - Card numbers must be masked except last 4")
        void pciDss34_CardNumberMasking() {
            String[] cardNumbers = {
                "4532123456789012",  // Visa
                "5432123456789010",  // Mastercard
                "371234567890123",   // Amex
                "6011123456789012"   // Discover
            };

            for (String cardNumber : cardNumbers) {
                String masked = SensitiveDataMasker.maskCardNumber(cardNumber);

                // Only last 4 digits should be visible
                assertTrue(masked.endsWith(cardNumber.substring(cardNumber.length() - 4)));

                // First digits must be masked
                assertTrue(masked.startsWith("****"));

                // Original card number should not be reconstructible
                assertFalse(masked.contains(cardNumber.substring(0, 12)));
            }
        }

        @Test
        @DisplayName("PCI DSS 10.3 - Session IDs must not be logged in clear text")
        void pciDss103_SessionIdMasking() {
            String sessionToken = "sk_live_sensitive_session_token_12345";
            String masked = SensitiveDataMasker.maskSessionToken(sessionToken);

            assertFalse(masked.contains("sensitive"));
            assertFalse(masked.contains("12345"));
            assertTrue(masked.startsWith("[SESSION:"));
        }
    }

    @Nested
    @DisplayName("GDPR Compliance Tests")
    class GdprComplianceTests {

        @Test
        @DisplayName("GDPR Article 32 - PII must be pseudonymized in logs")
        void gdprArticle32_PiiPseudonymization() {
            String email = "user@example.com";
            String phone = "+1234567890";

            String maskedEmail = SensitiveDataMasker.maskEmail(email);
            String maskedPhone = SensitiveDataMasker.maskPhoneNumber(phone);

            // Email should be pseudonymized
            assertNotEquals(email, maskedEmail);
            assertFalse(maskedEmail.contains("user"));

            // Phone should be pseudonymized
            assertNotEquals(phone, maskedPhone);
            assertFalse(maskedPhone.contains("123456"));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Security Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should not throw exception on malformed input")
        void shouldNotThrowOnMalformedInput() {
            assertDoesNotThrow(() -> {
                SensitiveDataMasker.maskEmail("@@@invalid");
                SensitiveDataMasker.maskPhoneNumber("abc");
                SensitiveDataMasker.maskCardNumber("not-a-card");
                SensitiveDataMasker.maskSSN("invalid-ssn");
            });
        }

        @Test
        @DisplayName("Should handle very long strings without performance issues")
        void shouldHandleLongStrings() {
            String longToken = "a".repeat(10000);

            long startTime = System.currentTimeMillis();
            String masked = SensitiveDataMasker.maskSessionToken(longToken);
            long endTime = System.currentTimeMillis();

            // Should complete in under 100ms
            assertTrue(endTime - startTime < 100);
            assertNotNull(masked);
        }

        @Test
        @DisplayName("Should be thread-safe")
        void shouldBeThreadSafe() throws InterruptedException {
            String sessionToken = "test-session-token-12345";

            Thread[] threads = new Thread[100];
            String[] results = new String[100];

            for (int i = 0; i < threads.length; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    results[index] = SensitiveDataMasker.maskSessionToken(sessionToken);
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // All results should be identical (consistent hashing)
            String expected = results[0];
            for (String result : results) {
                assertEquals(expected, result);
            }
        }
    }
}
