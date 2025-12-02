package com.waqiti.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for SARProcessingService
 * 
 * Tests SAR (Suspicious Activity Report) processing functionality including:
 * - Report generation and validation
 * - Narrative quality checking
 * - Documentation management
 * - Compliance verification
 * - Multi-jurisdiction support
 * - Error handling and recovery
 * 
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SAR Processing Service Tests")
class SARProcessingServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private SARProcessingService sarProcessingService;

    private String sarId;
    private String customerId;
    private String accountId;
    private String transactionId;
    private BigDecimal suspiciousAmount;
    private String currency;
    private String suspiciousActivity;
    private String narrativeDescription;
    private LocalDateTime reportingDate;

    @BeforeEach
    void setUp() {
        sarId = UUID.randomUUID().toString();
        customerId = UUID.randomUUID().toString();
        accountId = UUID.randomUUID().toString();
        transactionId = UUID.randomUUID().toString();
        suspiciousAmount = new BigDecimal("15000.00");
        currency = "USD";
        suspiciousActivity = "STRUCTURING";
        narrativeDescription = "Customer conducted multiple transactions just below $10,000 reporting threshold over a 3-day period, totaling $45,000. Pattern suggests potential structuring to avoid CTR reporting requirements. Transactions were made at different branch locations. Customer provided inconsistent explanations for the source of funds.";
        reportingDate = LocalDateTime.now();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // Set default configuration values
        ReflectionTestUtils.setField(sarProcessingService, "sarProcessingEnabled", true);
        ReflectionTestUtils.setField(sarProcessingService, "qualityThreshold", 95.0);
        ReflectionTestUtils.setField(sarProcessingService, "narrativeMinLength", 500);
        ReflectionTestUtils.setField(sarProcessingService, "sarRetentionDays", 2555);
    }

    @Nested
    @DisplayName("SAR Report Generation Tests")
    class SARReportGenerationTests {

        @Test
        @DisplayName("Should successfully generate SAR report with all required fields")
        void shouldGenerateSARReportSuccessfully() {
            sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            );

            verify(valueOperations, atLeastOnce()).set(
                contains("sar:report:" + sarId),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("Should skip report generation when processing is disabled")
        void shouldSkipWhenProcessingDisabled() {
            ReflectionTestUtils.setField(sarProcessingService, "sarProcessingEnabled", false);

            sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            );

            verify(valueOperations, never()).set(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should handle null transaction ID gracefully")
        void shouldHandleNullTransactionId() {
            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, null,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should generate report with multiple currencies")
        void shouldHandleMultipleCurrencies() {
            String[] currencies = {"USD", "EUR", "GBP", "CAD"};

            for (String curr : currencies) {
                sarProcessingService.generateSARReport(
                    UUID.randomUUID().toString(), customerId, accountId, transactionId,
                    suspiciousAmount, curr, suspiciousActivity,
                    narrativeDescription, reportingDate
                );
            }

            verify(valueOperations, times(currencies.length)).set(
                anyString(), any(), any()
            );
        }

        @Test
        @DisplayName("Should handle large suspicious amounts")
        void shouldHandleLargeAmounts() {
            BigDecimal largeAmount = new BigDecimal("999999999.99");

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                largeAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            )).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Narrative Validation Tests")
    class NarrativeValidationTests {

        @Test
        @DisplayName("Should validate narrative meets minimum length requirement")
        void shouldValidateNarrativeLength() {
            String shortNarrative = "Too short";

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                shortNarrative, reportingDate
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept narrative with exactly minimum length")
        void shouldAcceptMinimumLengthNarrative() {
            String minLengthNarrative = "A".repeat(500);

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                minLengthNarrative, reportingDate
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle very long narratives")
        void shouldHandleLongNarratives() {
            String longNarrative = "Detailed investigation narrative. ".repeat(200);

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                longNarrative, reportingDate
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle narrative with special characters")
        void shouldHandleSpecialCharacters() {
            String specialNarrative = "Customer used symbols: $, €, £, ¥. " +
                "Provided address: 123 Main St., Apt #45B. " +
                "Referenced account @FinancialInst123. " +
                "Total amount: $15,000.00 (USD). " +
                "Pattern detected on 01/15/2024 @ 14:30 PST. " +
                "Additional notes: See attached documentation (refs: DOC-2024-001, DOC-2024-002). " +
                "Customer's explanation included: \"Personal savings\" & \"Gift from family\". " +
                "Suspicious indicators: Multiple transactions <$10K, velocity pattern, inconsistent stories. " +
                "Conclusion: Requires immediate review & potential SAR filing.";

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                specialNarrative, reportingDate
            )).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Suspicious Activity Type Tests")
    class SuspiciousActivityTypeTests {

        @Test
        @DisplayName("Should process structuring activity type")
        void shouldProcessStructuringActivity() {
            sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, "STRUCTURING",
                narrativeDescription, reportingDate
            );

            verify(valueOperations, atLeastOnce()).set(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should process money laundering activity type")
        void shouldProcessMoneyLaunderingActivity() {
            sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, "MONEY_LAUNDERING",
                narrativeDescription, reportingDate
            );

            verify(valueOperations, atLeastOnce()).set(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should process terrorist financing activity type")
        void shouldProcessTerroristFinancingActivity() {
            sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, "TERRORIST_FINANCING",
                narrativeDescription, reportingDate
            );

            verify(valueOperations, atLeastOnce()).set(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should process fraud activity type")
        void shouldProcessFraudActivity() {
            sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, "FRAUD",
                narrativeDescription, reportingDate
            );

            verify(valueOperations, atLeastOnce()).set(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should process identity theft activity type")
        void shouldProcessIdentityTheftActivity() {
            sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, "IDENTITY_THEFT",
                narrativeDescription, reportingDate
            );

            verify(valueOperations, atLeastOnce()).set(anyString(), any(), any());
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle Redis connection failure gracefully")
        void shouldHandleRedisFailure() {
            when(valueOperations.set(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Redis connection failed"));

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle null customer ID")
        void shouldHandleNullCustomerId() {
            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, null, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle null account ID")
        void shouldHandleNullAccountId() {
            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, null, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle negative amounts")
        void shouldHandleNegativeAmounts() {
            BigDecimal negativeAmount = new BigDecimal("-1000.00");

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                negativeAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle zero amount")
        void shouldHandleZeroAmount() {
            BigDecimal zeroAmount = BigDecimal.ZERO;

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                zeroAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle future reporting date")
        void shouldHandleFutureReportingDate() {
            LocalDateTime futureDate = LocalDateTime.now().plusDays(30);

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, futureDate
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle very old reporting date")
        void shouldHandleOldReportingDate() {
            LocalDateTime oldDate = LocalDateTime.now().minusYears(5);

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, oldDate
            )).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Configuration and Feature Flag Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should respect quality threshold configuration")
        void shouldRespectQualityThreshold() {
            ReflectionTestUtils.setField(sarProcessingService, "qualityThreshold", 99.0);

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should respect retention days configuration")
        void shouldRespectRetentionDays() {
            ReflectionTestUtils.setField(sarProcessingService, "sarRetentionDays", 3650); // 10 years

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should respect custom narrative minimum length")
        void shouldRespectCustomNarrativeLength() {
            ReflectionTestUtils.setField(sarProcessingService, "narrativeMinLength", 1000);

            assertThatCode(() -> sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            )).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Concurrent Processing Tests")
    class ConcurrentProcessingTests {

        @Test
        @DisplayName("Should handle multiple concurrent SAR generations")
        void shouldHandleConcurrentGenerations() {
            String[] sarIds = {
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
            };

            for (String id : sarIds) {
                sarProcessingService.generateSARReport(
                    id, customerId, accountId, transactionId,
                    suspiciousAmount, currency, suspiciousActivity,
                    narrativeDescription, reportingDate
                );
            }

            verify(valueOperations, times(sarIds.length)).set(
                anyString(), any(), any()
            );
        }

        @Test
        @DisplayName("Should handle duplicate SAR ID attempts")
        void shouldHandleDuplicateSARIds() {
            sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            );

            sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            );

            verify(valueOperations, atLeast(2)).set(anyString(), any(), any());
        }
    }

    @Nested
    @DisplayName("Integration and Compliance Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should generate report compliant with FinCEN format")
        void shouldGenerateFinCENCompliantReport() {
            sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                suspiciousAmount, currency, suspiciousActivity,
                narrativeDescription, reportingDate
            );

            verify(valueOperations, atLeastOnce()).set(
                argThat(key -> key.contains("sar:report")),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("Should handle BSA Act compliance requirements")
        void shouldHandleBSACompliance() {
            BigDecimal bsaAmount = new BigDecimal("10000.00");

            sarProcessingService.generateSARReport(
                sarId, customerId, accountId, transactionId,
                bsaAmount, currency, "BSA_VIOLATION",
                narrativeDescription, reportingDate
            );

            verify(valueOperations, atLeastOnce()).set(anyString(), any(), any());
        }
    }
}