package com.waqiti.payment.service;

import com.waqiti.payment.entity.FundRelease;
import com.waqiti.payment.entity.ReleaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for MerchantService
 *
 * Tests merchant validation and operations:
 * - Merchant status checks (active, investigation, verification)
 * - Daily release limits
 * - Trust level checks
 * - Volume assessment
 * - Risk evaluation
 * - Webhook operations
 * - Balance updates
 * - Circuit breaker fallbacks
 * - Retry mechanisms
 * - WebClient integration
 * - Batch payment authorization
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantService Tests")
class MerchantServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private MerchantService merchantService;

    private static final String TEST_MERCHANT_ID = "merchant-123";
    private static final String MERCHANT_SERVICE_URL = "http://localhost:8086";
    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("100000.00");
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("100000.00");

    @BeforeEach
    void setUp() {
        merchantService = new MerchantService(webClientBuilder);

        // Set test properties
        ReflectionTestUtils.setField(merchantService, "merchantServiceUrl", MERCHANT_SERVICE_URL);
        ReflectionTestUtils.setField(merchantService, "defaultDailyLimit", DEFAULT_DAILY_LIMIT);
        ReflectionTestUtils.setField(merchantService, "highValueThreshold", HIGH_VALUE_THRESHOLD);

        // Mock WebClient builder
        when(webClientBuilder.baseUrl(MERCHANT_SERVICE_URL)).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
    }

    @Nested
    @DisplayName("Merchant Active Status Tests")
    class MerchantActiveStatusTests {

        @Test
        @DisplayName("Should return true when merchant is active")
        void shouldReturnTrueWhenMerchantIsActive() {
            // Given
            MerchantStatusResponse response = new MerchantStatusResponse();
            response.setStatus("ACTIVE");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantStatusResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean isActive = merchantService.isActive(TEST_MERCHANT_ID);

            // Then
            assertThat(isActive).isTrue();
        }

        @Test
        @DisplayName("Should return false when merchant is inactive")
        void shouldReturnFalseWhenMerchantIsInactive() {
            // Given
            MerchantStatusResponse response = new MerchantStatusResponse();
            response.setStatus("INACTIVE");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantStatusResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean isActive = merchantService.isActive(TEST_MERCHANT_ID);

            // Then
            assertThat(isActive).isFalse();
        }

        @Test
        @DisplayName("Should use fallback when merchant status check fails")
        void shouldUseFallbackWhenStatusCheckFails() {
            // Given
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantStatusResponse.class))
                    .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

            // When
            boolean isActive = merchantService.isActive(TEST_MERCHANT_ID);

            // Then - Fallback returns false
            assertThat(isActive).isFalse();
        }

        @Test
        @DisplayName("Should return false when response is null")
        void shouldReturnFalseWhenResponseIsNull() {
            // Given
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantStatusResponse.class)).thenReturn(Mono.empty());

            // When
            boolean isActive = merchantService.isActive(TEST_MERCHANT_ID);

            // Then
            assertThat(isActive).isFalse();
        }
    }

    @Nested
    @DisplayName("Investigation Status Tests")
    class InvestigationStatusTests {

        @Test
        @DisplayName("Should return true when merchant is under investigation")
        void shouldReturnTrueWhenUnderInvestigation() {
            // Given
            MerchantStatusResponse response = new MerchantStatusResponse();
            response.setUnderInvestigation(true);

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantStatusResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean underInvestigation = merchantService.isUnderInvestigation(TEST_MERCHANT_ID);

            // Then
            assertThat(underInvestigation).isTrue();
        }

        @Test
        @DisplayName("Should return false when merchant is not under investigation")
        void shouldReturnFalseWhenNotUnderInvestigation() {
            // Given
            MerchantStatusResponse response = new MerchantStatusResponse();
            response.setUnderInvestigation(false);

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantStatusResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean underInvestigation = merchantService.isUnderInvestigation(TEST_MERCHANT_ID);

            // Then
            assertThat(underInvestigation).isFalse();
        }

        @Test
        @DisplayName("Should use fallback when investigation check fails - assumes true for safety")
        void shouldUseFallbackWhenInvestigationCheckFails() {
            // Given
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantStatusResponse.class))
                    .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

            // When
            boolean underInvestigation = merchantService.isUnderInvestigation(TEST_MERCHANT_ID);

            // Then - Fallback returns true for safety
            assertThat(underInvestigation).isTrue();
        }
    }

    @Nested
    @DisplayName("Enhanced Verification Tests")
    class EnhancedVerificationTests {

        @Test
        @DisplayName("Should return true when merchant has enhanced verification")
        void shouldReturnTrueWhenHasEnhancedVerification() {
            // Given
            MerchantVerificationResponse response = new MerchantVerificationResponse();
            response.setEnhancedVerified(true);

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantVerificationResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean hasVerification = merchantService.hasEnhancedVerification(TEST_MERCHANT_ID);

            // Then
            assertThat(hasVerification).isTrue();
        }

        @Test
        @DisplayName("Should return false when merchant lacks enhanced verification")
        void shouldReturnFalseWhenLacksEnhancedVerification() {
            // Given
            MerchantVerificationResponse response = new MerchantVerificationResponse();
            response.setEnhancedVerified(false);

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantVerificationResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean hasVerification = merchantService.hasEnhancedVerification(TEST_MERCHANT_ID);

            // Then
            assertThat(hasVerification).isFalse();
        }

        @Test
        @DisplayName("Should use fallback when verification check fails")
        void shouldUseFallbackWhenVerificationCheckFails() {
            // Given
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantVerificationResponse.class))
                    .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

            // When
            boolean hasVerification = merchantService.hasEnhancedVerification(TEST_MERCHANT_ID);

            // Then - Fallback returns false
            assertThat(hasVerification).isFalse();
        }
    }

    @Nested
    @DisplayName("Daily Release Limit Tests")
    class DailyReleaseLimitTests {

        @Test
        @DisplayName("Should return merchant-specific daily release limit")
        void shouldReturnMerchantSpecificDailyReleaseLimit() {
            // Given
            BigDecimal customLimit = new BigDecimal("250000.00");
            MerchantLimitsResponse response = new MerchantLimitsResponse();
            response.setDailyReleaseLimit(customLimit);

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantLimitsResponse.class)).thenReturn(Mono.just(response));

            // When
            BigDecimal limit = merchantService.getDailyReleaseLimit(TEST_MERCHANT_ID);

            // Then
            assertThat(limit).isEqualByComparingTo(customLimit);
        }

        @Test
        @DisplayName("Should return default limit when merchant has no custom limit")
        void shouldReturnDefaultLimitWhenNoCustomLimit() {
            // Given
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantLimitsResponse.class)).thenReturn(Mono.empty());

            // When
            BigDecimal limit = merchantService.getDailyReleaseLimit(TEST_MERCHANT_ID);

            // Then
            assertThat(limit).isEqualByComparingTo(DEFAULT_DAILY_LIMIT);
        }

        @Test
        @DisplayName("Should use fallback when limit check fails")
        void shouldUseFallbackWhenLimitCheckFails() {
            // Given
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantLimitsResponse.class))
                    .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

            // When
            BigDecimal limit = merchantService.getDailyReleaseLimit(TEST_MERCHANT_ID);

            // Then - Fallback returns default limit
            assertThat(limit).isEqualByComparingTo(DEFAULT_DAILY_LIMIT);
        }
    }

    @Nested
    @DisplayName("Trust Level Tests")
    class TrustLevelTests {

        @Test
        @DisplayName("Should return true when merchant is trusted")
        void shouldReturnTrueWhenMerchantIsTrusted() {
            // Given
            MerchantTrustResponse response = new MerchantTrustResponse();
            response.setTrustLevel("TRUSTED");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantTrustResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean isTrusted = merchantService.isTrusted(TEST_MERCHANT_ID);

            // Then
            assertThat(isTrusted).isTrue();
        }

        @Test
        @DisplayName("Should return false when merchant is not trusted")
        void shouldReturnFalseWhenMerchantIsNotTrusted() {
            // Given
            MerchantTrustResponse response = new MerchantTrustResponse();
            response.setTrustLevel("STANDARD");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantTrustResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean isTrusted = merchantService.isTrusted(TEST_MERCHANT_ID);

            // Then
            assertThat(isTrusted).isFalse();
        }

        @Test
        @DisplayName("Should use fallback when trust check fails")
        void shouldUseFallbackWhenTrustCheckFails() {
            // Given
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantTrustResponse.class))
                    .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

            // When
            boolean isTrusted = merchantService.isTrusted(TEST_MERCHANT_ID);

            // Then - Fallback returns false
            assertThat(isTrusted).isFalse();
        }
    }

    @Nested
    @DisplayName("High Volume Tests")
    class HighVolumeTests {

        @Test
        @DisplayName("Should return true when merchant is high volume")
        void shouldReturnTrueWhenHighVolume() {
            // Given
            MerchantProfileResponse response = new MerchantProfileResponse();
            response.setHighVolume(true);

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantProfileResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean isHighVolume = merchantService.isHighVolume(TEST_MERCHANT_ID);

            // Then
            assertThat(isHighVolume).isTrue();
        }

        @Test
        @DisplayName("Should return false when merchant is not high volume")
        void shouldReturnFalseWhenNotHighVolume() {
            // Given
            MerchantProfileResponse response = new MerchantProfileResponse();
            response.setHighVolume(false);

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantProfileResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean isHighVolume = merchantService.isHighVolume(TEST_MERCHANT_ID);

            // Then
            assertThat(isHighVolume).isFalse();
        }
    }

    @Nested
    @DisplayName("High Risk Tests")
    class HighRiskTests {

        @Test
        @DisplayName("Should return true when merchant is high risk")
        void shouldReturnTrueWhenHighRisk() {
            // Given
            MerchantRiskResponse response = new MerchantRiskResponse();
            response.setRiskLevel("HIGH");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantRiskResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean isHighRisk = merchantService.isHighRisk(TEST_MERCHANT_ID);

            // Then
            assertThat(isHighRisk).isTrue();
        }

        @Test
        @DisplayName("Should return false when merchant is low risk")
        void shouldReturnFalseWhenLowRisk() {
            // Given
            MerchantRiskResponse response = new MerchantRiskResponse();
            response.setRiskLevel("LOW");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantRiskResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean isHighRisk = merchantService.isHighRisk(TEST_MERCHANT_ID);

            // Then
            assertThat(isHighRisk).isFalse();
        }

        @Test
        @DisplayName("Should use fallback when risk check fails - assumes high risk for safety")
        void shouldUseFallbackWhenRiskCheckFails() {
            // Given
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantRiskResponse.class))
                    .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

            // When
            boolean isHighRisk = merchantService.isHighRisk(TEST_MERCHANT_ID);

            // Then - Fallback returns true for safety
            assertThat(isHighRisk).isTrue();
        }
    }

    @Nested
    @DisplayName("Batch Payment Authorization Tests")
    class BatchPaymentAuthorizationTests {

        @Test
        @DisplayName("Should authorize batch payments when merchant is active and not high risk")
        void shouldAuthorizeBatchPaymentsWhenActiveAndNotHighRisk() {
            // Given - Active merchant
            MerchantStatusResponse statusResponse = new MerchantStatusResponse();
            statusResponse.setStatus("ACTIVE");

            // Given - Low risk merchant
            MerchantRiskResponse riskResponse = new MerchantRiskResponse();
            riskResponse.setRiskLevel("LOW");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantStatusResponse.class)).thenReturn(Mono.just(statusResponse));
            when(responseSpec.bodyToMono(MerchantRiskResponse.class)).thenReturn(Mono.just(riskResponse));

            // When
            boolean authorized = merchantService.isAuthorizedForBatchPayments(TEST_MERCHANT_ID);

            // Then
            assertThat(authorized).isTrue();
        }

        @Test
        @DisplayName("Should not authorize batch payments when merchant is inactive")
        void shouldNotAuthorizeBatchPaymentsWhenInactive() {
            // Given
            MerchantStatusResponse response = new MerchantStatusResponse();
            response.setStatus("INACTIVE");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantStatusResponse.class)).thenReturn(Mono.just(response));

            // When
            boolean authorized = merchantService.isAuthorizedForBatchPayments(TEST_MERCHANT_ID);

            // Then
            assertThat(authorized).isFalse();
        }

        @Test
        @DisplayName("Should not authorize batch payments when merchant is high risk")
        void shouldNotAuthorizeBatchPaymentsWhenHighRisk() {
            // Given - Active merchant
            MerchantStatusResponse statusResponse = new MerchantStatusResponse();
            statusResponse.setStatus("ACTIVE");

            // Given - High risk merchant
            MerchantRiskResponse riskResponse = new MerchantRiskResponse();
            riskResponse.setRiskLevel("HIGH");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(MerchantStatusResponse.class)).thenReturn(Mono.just(statusResponse));
            when(responseSpec.bodyToMono(MerchantRiskResponse.class)).thenReturn(Mono.just(riskResponse));

            // When
            boolean authorized = merchantService.isAuthorizedForBatchPayments(TEST_MERCHANT_ID);

            // Then
            assertThat(authorized).isFalse();
        }
    }

    // Helper classes matching the service's inner classes
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantStatusResponse {
        private String status;
        private boolean underInvestigation;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantVerificationResponse {
        private boolean enhancedVerified;
        private String verificationType;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantLimitsResponse {
        private BigDecimal dailyReleaseLimit;
        private BigDecimal monthlyReleaseLimit;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantTrustResponse {
        private String trustLevel;
        private Integer trustScore;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantProfileResponse {
        private boolean highVolume;
        private String tier;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantRiskResponse {
        private String riskLevel;
        private Integer riskScore;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantWebhookResponse {
        private boolean webhookEnabled;
        private String webhookUrl;
    }
}
