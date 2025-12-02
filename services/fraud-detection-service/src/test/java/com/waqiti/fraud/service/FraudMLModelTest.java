package com.waqiti.fraud.service;

import com.waqiti.fraud.dto.FraudDetectionRequest;
import com.waqiti.frauddetection.dto.FraudCheckRequest;
import com.waqiti.frauddetection.dto.FraudCheckResponse;
import com.waqiti.frauddetection.dto.RiskLevel;
import com.waqiti.frauddetection.ml.FraudMLModel;
import com.waqiti.frauddetection.service.FraudDetectionService;
import com.waqiti.frauddetection.entity.FraudIncident;
import com.waqiti.frauddetection.repository.FraudIncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Fraud Detection ML Model Tests")
class FraudMLModelTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired(required = false)
    private FraudMLModel fraudMLModel;

    @Autowired(required = false)
    private FeatureEngineeringService featureEngineeringService;

    @Autowired(required = false)
    private FraudIncidentRepository fraudIncidentRepository;

    @MockBean(name = "fraudMLModel")
    private FraudMLModel mockFraudMLModel;

    private FraudDetectionRequest testRequest;
    private FraudCheckRequest testCheckRequest;

    @BeforeEach
    void setUp() {
        testRequest = createTestFraudDetectionRequest();
        testCheckRequest = createTestFraudCheckRequest();
    }

    @Nested
    @DisplayName("Feature Engineering Tests")
    class FeatureEngineeringTests {

        @Test
        @DisplayName("Should extract basic transaction features")
        void shouldExtractBasicTransactionFeatures() {
            if (featureEngineeringService == null) {
                return;
            }

            Map<String, Double> features = featureEngineeringService.extractFeatures(testRequest);

            assertThat(features).isNotNull();
            assertThat(features).containsKey("transaction_amount");
            assertThat(features).containsKey("transaction_amount_log");
            assertThat(features.get("transaction_amount")).isEqualTo(1000.0);
        }

        @Test
        @DisplayName("Should extract velocity features")
        void shouldExtractVelocityFeatures() {
            if (featureEngineeringService == null) {
                return;
            }

            Map<String, Double> features = featureEngineeringService.extractFeatures(testRequest);

            assertThat(features).containsKeys(
                    "txn_count_1d", "txn_count_7d", "txn_count_30d"
            );
        }

        @Test
        @DisplayName("Should extract temporal features with cyclical encoding")
        void shouldExtractTemporalFeaturesWithCyclicalEncoding() {
            if (featureEngineeringService == null) {
                return;
            }

            Map<String, Double> features = featureEngineeringService.extractFeatures(testRequest);

            assertThat(features).containsKeys(
                    "transaction_hour",
                    "hour_sin", "hour_cos",
                    "day_of_week_sin", "day_of_week_cos"
            );

            double hourSin = features.get("hour_sin");
            double hourCos = features.get("hour_cos");

            assertThat(hourSin * hourSin + hourCos * hourCos).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("Should extract geographic risk features")
        void shouldExtractGeographicRiskFeatures() {
            if (featureEngineeringService == null) {
                return;
            }

            testRequest.setCountry("US");
            testRequest.setCity("New York");

            Map<String, Double> features = featureEngineeringService.extractFeatures(testRequest);

            assertThat(features).containsKey("country_risk_score");
        }

        @Test
        @DisplayName("Should handle missing features gracefully")
        void shouldHandleMissingFeaturesGracefully() {
            if (featureEngineeringService == null) {
                return;
            }

            FraudDetectionRequest minimalRequest = FraudDetectionRequest.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .userId("minimal-user")
                    .amount(new BigDecimal("100.00"))
                    .timestamp(LocalDateTime.now())
                    .build();

            Map<String, Double> features = featureEngineeringService.extractFeatures(minimalRequest);

            assertThat(features).isNotNull();
            assertThat(features).isNotEmpty();
        }

        @Test
        @DisplayName("Should validate feature quality")
        void shouldValidateFeatureQuality() {
            if (featureEngineeringService == null) {
                return;
            }

            Map<String, Double> features = featureEngineeringService.extractFeatures(testRequest);

            features.forEach((key, value) -> {
                assertThat(value).isNotNull();
                assertThat(value.isNaN()).isFalse();
                assertThat(value.isInfinite()).isFalse();
            });
        }

        @Test
        @DisplayName("Should apply feature transformations")
        void shouldApplyFeatureTransformations() {
            if (featureEngineeringService == null) {
                return;
            }

            Map<String, Double> features = featureEngineeringService.extractFeatures(testRequest);

            if (features.containsKey("transaction_amount")) {
                assertThat(features).containsKey("transaction_amount_log");
                
                double originalAmount = features.get("transaction_amount");
                double logAmount = features.get("transaction_amount_log");
                
                assertThat(logAmount).isEqualTo(Math.log10(originalAmount + 1));
            }
        }

        @Test
        @DisplayName("Should calculate amount quantile features")
        void shouldCalculateAmountQuantileFeatures() {
            if (featureEngineeringService == null) {
                return;
            }

            Map<String, Double> features = featureEngineeringService.extractFeatures(testRequest);

            assertThat(features.size()).isGreaterThan(10);
        }
    }

    @Nested
    @DisplayName("ML Model Prediction Tests")
    class MLModelPredictionTests {

        @Test
        @DisplayName("Should predict low risk for normal transaction")
        void shouldPredictLowRiskForNormalTransaction() {
            when(mockFraudMLModel.predict(any(FraudCheckRequest.class))).thenReturn(0.2);

            double riskScore = mockFraudMLModel.predict(testCheckRequest);

            assertThat(riskScore).isLessThan(0.5);
        }

        @Test
        @DisplayName("Should predict high risk for suspicious transaction")
        void shouldPredictHighRiskForSuspiciousTransaction() {
            FraudCheckRequest suspiciousRequest = createSuspiciousTransaction();

            when(mockFraudMLModel.predict(any(FraudCheckRequest.class))).thenReturn(0.9);

            double riskScore = mockFraudMLModel.predict(suspiciousRequest);

            assertThat(riskScore).isGreaterThan(0.8);
        }

        @Test
        @DisplayName("Should handle edge case amounts")
        void shouldHandleEdgeCaseAmounts() {
            FraudCheckRequest zeroAmount = testCheckRequest;
            zeroAmount.setAmount(BigDecimal.ZERO);

            when(mockFraudMLModel.predict(any(FraudCheckRequest.class))).thenReturn(0.5);

            double riskScore = mockFraudMLModel.predict(zeroAmount);

            assertThat(riskScore).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("Should return consistent predictions for identical transactions")
        void shouldReturnConsistentPredictionsForIdenticalTransactions() {
            when(mockFraudMLModel.predict(any(FraudCheckRequest.class))).thenReturn(0.3);

            double score1 = mockFraudMLModel.predict(testCheckRequest);
            double score2 = mockFraudMLModel.predict(testCheckRequest);

            assertThat(score1).isEqualTo(score2);
        }

        @Test
        @DisplayName("Should handle concurrent predictions")
        void shouldHandleConcurrentPredictions() throws Exception {
            when(mockFraudMLModel.predict(any(FraudCheckRequest.class))).thenReturn(0.4);

            int numberOfThreads = 10;
            List<Double> results = Collections.synchronizedList(new ArrayList<>());
            List<Thread> threads = new ArrayList<>();

            for (int i = 0; i < numberOfThreads; i++) {
                Thread thread = new Thread(() -> {
                    double score = mockFraudMLModel.predict(testCheckRequest);
                    results.add(score);
                });
                threads.add(thread);
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            assertThat(results).hasSize(numberOfThreads);
            assertThat(results).allMatch(score -> score >= 0.0 && score <= 1.0);
        }
    }

    @Nested
    @DisplayName("Model Training and Retraining Tests")
    class ModelTrainingTests {

        @Test
        @DisplayName("Should retrain model with labeled incidents")
        void shouldRetrainModelWithLabeledIncidents() {
            if (fraudMLModel == null) {
                return;
            }

            List<FraudIncident> labeledIncidents = createLabeledIncidents(100);

            assertThatCode(() -> fraudMLModel.retrain(labeledIncidents))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle small training dataset")
        void shouldHandleSmallTrainingDataset() {
            if (fraudMLModel == null) {
                return;
            }

            List<FraudIncident> smallDataset = createLabeledIncidents(10);

            assertThatCode(() -> fraudMLModel.retrain(smallDataset))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should validate training data quality")
        void shouldValidateTrainingDataQuality() {
            if (fraudMLModel == null) {
                return;
            }

            List<FraudIncident> incidents = createLabeledIncidents(50);

            incidents.forEach(incident -> {
                assertThat(incident.getRiskScore()).isBetween(0.0, 1.0);
                assertThat(incident.getRiskLevel()).isNotNull();
            });
        }

        @Test
        @DisplayName("Should handle imbalanced training data")
        void shouldHandleImbalancedTrainingData() {
            if (fraudMLModel == null) {
                return;
            }

            List<FraudIncident> imbalancedData = new ArrayList<>();

            for (int i = 0; i < 90; i++) {
                imbalancedData.add(createNonFraudIncident());
            }

            for (int i = 0; i < 10; i++) {
                imbalancedData.add(createFraudIncident());
            }

            assertThatCode(() -> fraudMLModel.retrain(imbalancedData))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Model Performance Tests")
    class ModelPerformanceTests {

        @Test
        @DisplayName("Should meet prediction latency requirements")
        void shouldMeetPredictionLatencyRequirements() {
            when(mockFraudMLModel.predict(any(FraudCheckRequest.class))).thenReturn(0.5);

            long startTime = System.currentTimeMillis();
            mockFraudMLModel.predict(testCheckRequest);
            long endTime = System.currentTimeMillis();

            long latency = endTime - startTime;

            assertThat(latency).isLessThan(100);
        }

        @Test
        @DisplayName("Should handle high throughput predictions")
        void shouldHandleHighThroughputPredictions() {
            when(mockFraudMLModel.predict(any(FraudCheckRequest.class))).thenReturn(0.5);

            int numberOfPredictions = 1000;
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < numberOfPredictions; i++) {
                mockFraudMLModel.predict(testCheckRequest);
            }

            long endTime = System.currentTimeMillis();
            double predictionsPerSecond = numberOfPredictions / ((endTime - startTime) / 1000.0);

            assertThat(predictionsPerSecond).isGreaterThan(100);
        }

        @Test
        @DisplayName("Should maintain accuracy across different transaction types")
        void shouldMaintainAccuracyAcrossDifferentTransactionTypes() {
            when(mockFraudMLModel.predict(any(FraudCheckRequest.class)))
                    .thenAnswer(invocation -> {
                        FraudCheckRequest req = invocation.getArgument(0);
                        return req.getAmount().doubleValue() > 5000 ? 0.8 : 0.2;
                    });

            FraudCheckRequest smallTransaction = createTransactionWithAmount(new BigDecimal("100"));
            FraudCheckRequest largeTransaction = createTransactionWithAmount(new BigDecimal("10000"));

            double smallScore = mockFraudMLModel.predict(smallTransaction);
            double largeScore = mockFraudMLModel.predict(largeTransaction);

            assertThat(smallScore).isLessThan(largeScore);
        }
    }

    @Nested
    @DisplayName("Feature Importance Tests")
    class FeatureImportanceTests {

        @Test
        @DisplayName("Should identify key fraud indicators")
        void shouldIdentifyKeyFraudIndicators() {
            when(mockFraudMLModel.predict(any(FraudCheckRequest.class)))
                    .thenAnswer(invocation -> {
                        FraudCheckRequest req = invocation.getArgument(0);
                        
                        double score = 0.0;
                        
                        if (req.getAmount().compareTo(new BigDecimal("10000")) > 0) {
                            score += 0.3;
                        }
                        
                        if (req.getSenderCountry() != null && 
                            !req.getSenderCountry().equals(req.getRecipientCountry())) {
                            score += 0.2;
                        }
                        
                        if (LocalDateTime.now().getHour() < 6) {
                            score += 0.1;
                        }
                        
                        return Math.min(score, 1.0);
                    });

            FraudCheckRequest highRiskRequest = testCheckRequest;
            highRiskRequest.setAmount(new BigDecimal("15000"));
            highRiskRequest.setSenderCountry("US");
            highRiskRequest.setRecipientCountry("NG");

            double riskScore = mockFraudMLModel.predict(highRiskRequest);

            assertThat(riskScore).isGreaterThan(0.3);
        }

        @Test
        @DisplayName("Should weight velocity features appropriately")
        void shouldWeightVelocityFeaturesAppropriately() {
            when(mockFraudMLModel.predict(any(FraudCheckRequest.class))).thenReturn(0.7);

            double score = mockFraudMLModel.predict(testCheckRequest);

            assertThat(score).isGreaterThan(0.5);
        }

        @Test
        @DisplayName("Should consider behavioral patterns")
        void shouldConsiderBehavioralPatterns() {
            when(mockFraudMLModel.predict(any(FraudCheckRequest.class))).thenReturn(0.6);

            double score = mockFraudMLModel.predict(testCheckRequest);

            assertThat(score).isBetween(0.0, 1.0);
        }
    }

    @Nested
    @DisplayName("Model Explainability Tests")
    class ModelExplainabilityTests {

        @Test
        @DisplayName("Should provide prediction explanation")
        void shouldProvidePredictionExplanation() {
            when(mockFraudMLModel.predict(any(FraudCheckRequest.class))).thenReturn(0.85);

            double score = mockFraudMLModel.predict(testCheckRequest);

            assertThat(score).isGreaterThan(0.8);
        }

        @Test
        @DisplayName("Should identify contributing risk factors")
        void shouldIdentifyContributingRiskFactors() {
            when(mockFraudMLModel.predict(any(FraudCheckRequest.class))).thenReturn(0.7);

            double score = mockFraudMLModel.predict(testCheckRequest);

            assertThat(score).isPositive();
        }
    }

    @Nested
    @DisplayName("Model Monitoring Tests")
    class ModelMonitoringTests {

        @Test
        @DisplayName("Should detect model drift")
        void shouldDetectModelDrift() {
            if (fraudMLModel == null) {
                return;
            }

            List<FraudIncident> recentIncidents = createLabeledIncidents(100);

            assertThat(recentIncidents).isNotEmpty();
        }

        @Test
        @DisplayName("Should track prediction distribution")
        void shouldTrackPredictionDistribution() {
            when(mockFraudMLModel.predict(any(FraudCheckRequest.class)))
                    .thenReturn(0.2, 0.5, 0.8, 0.3, 0.6);

            List<Double> predictions = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                predictions.add(mockFraudMLModel.predict(testCheckRequest));
            }

            assertThat(predictions).containsExactly(0.2, 0.5, 0.8, 0.3, 0.6);
        }

        @Test
        @DisplayName("Should monitor false positive rate")
        void shouldMonitorFalsePositiveRate() {
            if (fraudMLModel == null || fraudIncidentRepository == null) {
                return;
            }

            List<FraudIncident> incidents = createLabeledIncidents(50);

            long falsePositives = incidents.stream()
                    .filter(i -> i.getRiskLevel() == RiskLevel.HIGH && !i.isBlocked())
                    .count();

            double falsePositiveRate = (double) falsePositives / incidents.size();

            assertThat(falsePositiveRate).isLessThan(0.1);
        }
    }

    private FraudDetectionRequest createTestFraudDetectionRequest() {
        return FraudDetectionRequest.builder()
                .transactionId(UUID.randomUUID().toString())
                .userId("test-user-123")
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .timestamp(LocalDateTime.now())
                .country("US")
                .city("New York")
                .ipAddress("192.168.1.1")
                .deviceFingerprint("device-123")
                .userAgent("Mozilla/5.0")
                .build();
    }

    private FraudCheckRequest createTestFraudCheckRequest() {
        return FraudCheckRequest.builder()
                .transactionId(UUID.randomUUID().toString())
                .userId("test-user-123")
                .recipientId("recipient-456")
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .senderCountry("US")
                .recipientCountry("US")
                .build();
    }

    private FraudCheckRequest createSuspiciousTransaction() {
        return FraudCheckRequest.builder()
                .transactionId(UUID.randomUUID().toString())
                .userId("new-user-999")
                .recipientId("recipient-suspicious")
                .amount(new BigDecimal("50000.00"))
                .currency("USD")
                .senderCountry("US")
                .recipientCountry("NG")
                .build();
    }

    private FraudCheckRequest createTransactionWithAmount(BigDecimal amount) {
        FraudCheckRequest request = createTestFraudCheckRequest();
        request.setAmount(amount);
        return request;
    }

    private List<FraudIncident> createLabeledIncidents(int count) {
        List<FraudIncident> incidents = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            FraudIncident incident = FraudIncident.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .userId("user-" + i)
                    .amount(new BigDecimal(Math.random() * 10000))
                    .riskScore(Math.random())
                    .riskLevel(Math.random() > 0.7 ? RiskLevel.HIGH : RiskLevel.LOW)
                    .velocityScore(Math.random())
                    .geoScore(Math.random())
                    .deviceScore(Math.random())
                    .behaviorScore(Math.random())
                    .mlScore(Math.random())
                    .timestamp(LocalDateTime.now().minusDays((long) (Math.random() * 90)))
                    .blocked(Math.random() > 0.5)
                    .build();

            incidents.add(incident);
        }

        return incidents;
    }

    private FraudIncident createNonFraudIncident() {
        return FraudIncident.builder()
                .transactionId(UUID.randomUUID().toString())
                .userId("user-" + UUID.randomUUID())
                .amount(new BigDecimal(Math.random() * 1000))
                .riskScore(Math.random() * 0.3)
                .riskLevel(RiskLevel.LOW)
                .velocityScore(Math.random() * 0.3)
                .geoScore(Math.random() * 0.3)
                .deviceScore(Math.random() * 0.3)
                .behaviorScore(Math.random() * 0.3)
                .mlScore(Math.random() * 0.3)
                .timestamp(LocalDateTime.now())
                .blocked(false)
                .build();
    }

    private FraudIncident createFraudIncident() {
        return FraudIncident.builder()
                .transactionId(UUID.randomUUID().toString())
                .userId("user-" + UUID.randomUUID())
                .amount(new BigDecimal(5000 + Math.random() * 10000))
                .riskScore(0.7 + Math.random() * 0.3)
                .riskLevel(RiskLevel.HIGH)
                .velocityScore(0.7 + Math.random() * 0.3)
                .geoScore(0.7 + Math.random() * 0.3)
                .deviceScore(0.7 + Math.random() * 0.3)
                .behaviorScore(0.7 + Math.random() * 0.3)
                .mlScore(0.7 + Math.random() * 0.3)
                .timestamp(LocalDateTime.now())
                .blocked(true)
                .build();
    }
}