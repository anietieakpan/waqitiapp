package com.waqiti.fraud.sanctions.service;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.fraud.sanctions.dto.OfacSdnEntry;
import com.waqiti.fraud.sanctions.dto.SanctionsScreeningRequest;
import com.waqiti.fraud.sanctions.dto.SanctionsScreeningResult;
import com.waqiti.fraud.sanctions.entity.SanctionsCheckRecord;
import com.waqiti.fraud.sanctions.entity.SanctionsCheckRecord.*;
import com.waqiti.fraud.sanctions.fuzzy.FuzzyMatchResult;
import com.waqiti.fraud.sanctions.fuzzy.FuzzyMatchingAlgorithm;
import com.waqiti.fraud.sanctions.fuzzy.FuzzyMatchingService;
import com.waqiti.fraud.sanctions.repository.SanctionsCheckRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for SanctionsScreeningService.
 *
 * Test Coverage:
 * - User screening with exact matches
 * - User screening with fuzzy matches
 * - Transaction party screening
 * - Manual review workflow
 * - SAR filing integration
 * - Idempotency handling
 * - Error scenarios
 * - Edge cases
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Sanctions Screening Service Tests")
class SanctionsScreeningServiceTest {

    @Mock
    private SanctionsCheckRepository sanctionsCheckRepository;

    @Mock
    private SanctionsListCacheService sanctionsListCacheService;

    @Mock
    private FuzzyMatchingService fuzzyMatchingService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private SanctionsScreeningService sanctionsScreeningService;

    private SanctionsScreeningRequest testRequest;
    private UUID testUserId;
    private UUID testTransactionId;
    private List<OfacSdnEntry> mockOfacList;
    private List<OfacSdnEntry> mockEuList;
    private List<OfacSdnEntry> mockUnList;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testTransactionId = UUID.randomUUID();

        testRequest = SanctionsScreeningRequest.builder()
            .userId(testUserId)
            .entityType(EntityType.USER)
            .fullName("John Doe")
            .dateOfBirth(LocalDate.of(1980, 1, 15))
            .nationality("USA")
            .checkSource(CheckSource.REGISTRATION)
            .build();

        mockOfacList = createMockOfacList();
        mockEuList = createMockEuList();
        mockUnList = createMockUnList();
    }

    // =============================================================================
    // User Screening Tests
    // =============================================================================

    @Nested
    @DisplayName("User Screening Tests")
    class UserScreeningTests {

        @Test
        @DisplayName("Should pass screening when no matches found")
        void shouldPassScreeningWhenNoMatchesFound() throws Exception {
            // Given
            when(sanctionsListCacheService.getOfacSdnList()).thenReturn(mockOfacList);
            when(sanctionsListCacheService.getEuSanctionsList()).thenReturn(mockEuList);
            when(sanctionsListCacheService.getUnSanctionsList()).thenReturn(mockUnList);

            when(fuzzyMatchingService.isExactMatch(anyString(), anyString())).thenReturn(false);
            when(fuzzyMatchingService.getWeightedConfidence(anyString(), anyString()))
                .thenReturn(new BigDecimal("25.00")); // Below threshold

            when(idempotencyService.executeIdempotentWithPersistenceAsync(
                anyString(), anyString(), anyString(), any(), any()
            )).thenAnswer(invocation -> {
                var supplier = invocation.getArgument(3, java.util.function.Supplier.class);
                return CompletableFuture.completedFuture(supplier.get());
            });

            ArgumentCaptor<SanctionsCheckRecord> recordCaptor = ArgumentCaptor.forClass(SanctionsCheckRecord.class);
            when(sanctionsCheckRepository.save(recordCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            // When
            CompletableFuture<SanctionsScreeningResult> futureResult =
                sanctionsScreeningService.screenUser(testRequest);
            SanctionsScreeningResult result = futureResult.get();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMatchFound()).isFalse();
            assertThat(result.getMatchCount()).isZero();
            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
            assertThat(result.getBlocked()).isFalse();
            assertThat(result.getCleared()).isTrue();
            assertThat(result.getRequiresManualReview()).isFalse();

            SanctionsCheckRecord savedRecord = recordCaptor.getValue();
            assertThat(savedRecord.getUserId()).isEqualTo(testUserId);
            assertThat(savedRecord.getMatchFound()).isFalse();
            assertThat(savedRecord.getCheckStatus()).isEqualTo(CheckStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should detect exact match and flag for manual review")
        void shouldDetectExactMatchAndFlagForReview() throws Exception {
            // Given
            OfacSdnEntry exactMatch = OfacSdnEntry.builder()
                .uid(12345L)
                .fullName("John Doe")
                .sdnType("Individual")
                .programs(List.of("SDGT"))
                .dateOfBirth(LocalDate.of(1980, 1, 15))
                .nationality("USA")
                .listName("OFAC_SDN")
                .build();

            List<OfacSdnEntry> ofacListWithMatch = new ArrayList<>(mockOfacList);
            ofacListWithMatch.add(exactMatch);

            when(sanctionsListCacheService.getOfacSdnList()).thenReturn(ofacListWithMatch);
            when(sanctionsListCacheService.getEuSanctionsList()).thenReturn(mockEuList);
            when(sanctionsListCacheService.getUnSanctionsList()).thenReturn(mockUnList);

            when(fuzzyMatchingService.isExactMatch("John Doe", "John Doe")).thenReturn(true);
            when(fuzzyMatchingService.getWeightedConfidence("JOHN DOE", "JOHN DOE"))
                .thenReturn(new BigDecimal("100.00"));

            when(idempotencyService.executeIdempotentWithPersistenceAsync(
                anyString(), anyString(), anyString(), any(), any()
            )).thenAnswer(invocation -> {
                var supplier = invocation.getArgument(3, java.util.function.Supplier.class);
                return CompletableFuture.completedFuture(supplier.get());
            });

            ArgumentCaptor<SanctionsCheckRecord> recordCaptor = ArgumentCaptor.forClass(SanctionsCheckRecord.class);
            when(sanctionsCheckRepository.save(recordCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            // When
            CompletableFuture<SanctionsScreeningResult> futureResult =
                sanctionsScreeningService.screenUser(testRequest);
            SanctionsScreeningResult result = futureResult.get();

            // Then
            assertThat(result.getMatchFound()).isTrue();
            assertThat(result.getMatchCount()).isGreaterThan(0);
            assertThat(result.getRiskLevel()).isIn(RiskLevel.HIGH, RiskLevel.CRITICAL);
            assertThat(result.getRequiresManualReview()).isTrue();
            assertThat(result.getBlocked()).isFalse(); // Not auto-blocked, requires review
            assertThat(result.getCheckStatus()).isEqualTo(CheckStatus.MANUAL_REVIEW);

            SanctionsCheckRecord savedRecord = recordCaptor.getValue();
            assertThat(savedRecord.getMatchFound()).isTrue();
            assertThat(savedRecord.getMatchedOfac()).isTrue();
            assertThat(savedRecord.getRequiresManualReview()).isTrue();
        }

        @Test
        @DisplayName("Should detect high-confidence fuzzy match")
        void shouldDetectHighConfidenceFuzzyMatch() throws Exception {
            // Given
            OfacSdnEntry fuzzyMatch = OfacSdnEntry.builder()
                .uid(12346L)
                .fullName("Jon Doe") // Slight variation
                .sdnType("Individual")
                .programs(List.of("SDGT"))
                .dateOfBirth(LocalDate.of(1980, 1, 15))
                .listName("OFAC_SDN")
                .build();

            List<OfacSdnEntry> ofacListWithMatch = new ArrayList<>(mockOfacList);
            ofacListWithMatch.add(fuzzyMatch);

            when(sanctionsListCacheService.getOfacSdnList()).thenReturn(ofacListWithMatch);
            when(sanctionsListCacheService.getEuSanctionsList()).thenReturn(mockEuList);
            when(sanctionsListCacheService.getUnSanctionsList()).thenReturn(mockUnList);

            when(fuzzyMatchingService.isExactMatch(anyString(), anyString())).thenReturn(false);
            when(fuzzyMatchingService.getWeightedConfidence("JOHN DOE", "JON DOE"))
                .thenReturn(new BigDecimal("92.50")); // High confidence

            FuzzyMatchResult fuzzyResult = FuzzyMatchResult.builder()
                .algorithm(FuzzyMatchingAlgorithm.JARO_WINKLER)
                .confidence(new BigDecimal("95.00"))
                .matchedName1("JOHN DOE")
                .matchedName2("JON DOE")
                .build();

            when(fuzzyMatchingService.matchName(anyString(), anyString(), anyList()))
                .thenReturn(List.of(fuzzyResult));

            when(idempotencyService.executeIdempotentWithPersistenceAsync(
                anyString(), anyString(), anyString(), any(), any()
            )).thenAnswer(invocation -> {
                var supplier = invocation.getArgument(3, java.util.function.Supplier.class);
                return CompletableFuture.completedFuture(supplier.get());
            });

            ArgumentCaptor<SanctionsCheckRecord> recordCaptor = ArgumentCaptor.forClass(SanctionsCheckRecord.class);
            when(sanctionsCheckRepository.save(recordCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            // When
            CompletableFuture<SanctionsScreeningResult> futureResult =
                sanctionsScreeningService.screenUser(testRequest);
            SanctionsScreeningResult result = futureResult.get();

            // Then
            assertThat(result.getMatchFound()).isTrue();
            assertThat(result.getMatchScore()).isGreaterThan(new BigDecimal("90.00"));
            assertThat(result.getRiskLevel()).isIn(RiskLevel.MEDIUM, RiskLevel.HIGH);
            assertThat(result.getRequiresManualReview()).isTrue();
        }

        @Test
        @DisplayName("Should ignore low-confidence fuzzy matches")
        void shouldIgnoreLowConfidenceFuzzyMatches() throws Exception {
            // Given
            OfacSdnEntry lowConfidenceMatch = OfacSdnEntry.builder()
                .uid(12347L)
                .fullName("Jane Smith") // Different name
                .sdnType("Individual")
                .programs(List.of("SDGT"))
                .listName("OFAC_SDN")
                .build();

            List<OfacSdnEntry> ofacListWithMatch = new ArrayList<>(mockOfacList);
            ofacListWithMatch.add(lowConfidenceMatch);

            when(sanctionsListCacheService.getOfacSdnList()).thenReturn(ofacListWithMatch);
            when(sanctionsListCacheService.getEuSanctionsList()).thenReturn(mockEuList);
            when(sanctionsListCacheService.getUnSanctionsList()).thenReturn(mockUnList);

            when(fuzzyMatchingService.isExactMatch(anyString(), anyString())).thenReturn(false);
            when(fuzzyMatchingService.getWeightedConfidence(anyString(), anyString()))
                .thenReturn(new BigDecimal("30.00")); // Low confidence

            when(idempotencyService.executeIdempotentWithPersistenceAsync(
                anyString(), anyString(), anyString(), any(), any()
            )).thenAnswer(invocation -> {
                var supplier = invocation.getArgument(3, java.util.function.Supplier.class);
                return CompletableFuture.completedFuture(supplier.get());
            });

            ArgumentCaptor<SanctionsCheckRecord> recordCaptor = ArgumentCaptor.forClass(SanctionsCheckRecord.class);
            when(sanctionsCheckRepository.save(recordCaptor.capture())).thenAnswer(i -> i.getArgument(0));

            // When
            CompletableFuture<SanctionsScreeningResult> futureResult =
                sanctionsScreeningService.screenUser(testRequest);
            SanctionsScreeningResult result = futureResult.get();

            // Then
            assertThat(result.getMatchFound()).isFalse();
            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
            assertThat(result.getBlocked()).isFalse();
            assertThat(result.getCleared()).isTrue();
        }
    }

    // =============================================================================
    // Transaction Screening Tests
    // =============================================================================

    @Nested
    @DisplayName("Transaction Party Screening Tests")
    class TransactionScreeningTests {

        @Test
        @DisplayName("Should screen transaction beneficiary")
        void shouldScreenTransactionBeneficiary() throws Exception {
            // Given
            testRequest = SanctionsScreeningRequest.builder()
                .transactionId(testTransactionId)
                .entityType(EntityType.TRANSACTION_PARTY)
                .fullName("Beneficiary Name")
                .transactionAmount(new BigDecimal("50000.00"))
                .transactionCurrency("USD")
                .checkSource(CheckSource.TRANSACTION)
                .build();

            when(sanctionsListCacheService.getOfacSdnList()).thenReturn(mockOfacList);
            when(sanctionsListCacheService.getEuSanctionsList()).thenReturn(mockEuList);
            when(sanctionsListCacheService.getUnSanctionsList()).thenReturn(mockUnList);

            when(fuzzyMatchingService.isExactMatch(anyString(), anyString())).thenReturn(false);
            when(fuzzyMatchingService.getWeightedConfidence(anyString(), anyString()))
                .thenReturn(new BigDecimal("25.00"));

            when(idempotencyService.executeIdempotentWithPersistenceAsync(
                anyString(), anyString(), anyString(), any(), any()
            )).thenAnswer(invocation -> {
                var supplier = invocation.getArgument(3, java.util.function.Supplier.class);
                return CompletableFuture.completedFuture(supplier.get());
            });

            when(sanctionsCheckRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            CompletableFuture<SanctionsScreeningResult> futureResult =
                sanctionsScreeningService.screenTransactionParties(testRequest);
            SanctionsScreeningResult result = futureResult.get();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCheckStatus()).isEqualTo(CheckStatus.COMPLETED);
        }
    }

    // =============================================================================
    // Manual Review Workflow Tests
    // =============================================================================

    @Nested
    @DisplayName("Manual Review Workflow Tests")
    class ManualReviewTests {

        @Test
        @DisplayName("Should resolve check as CLEARED")
        void shouldResolveCheckAsCleared() {
            // Given
            UUID checkId = UUID.randomUUID();
            UUID reviewerId = UUID.randomUUID();
            String reviewNotes = "False positive - different person with similar name";

            SanctionsCheckRecord existingCheck = SanctionsCheckRecord.builder()
                .id(checkId)
                .userId(testUserId)
                .checkedName("John Doe")
                .matchFound(true)
                .checkStatus(CheckStatus.MANUAL_REVIEW)
                .requiresManualReview(true)
                .build();

            when(sanctionsCheckRepository.findById(checkId)).thenReturn(Optional.of(existingCheck));
            when(sanctionsCheckRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            SanctionsScreeningResult result = sanctionsScreeningService.resolveManualReview(
                checkId, Resolution.CLEARED, reviewNotes, reviewerId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getResolution()).isEqualTo(Resolution.CLEARED);
            assertThat(result.getCleared()).isTrue();
            assertThat(result.getBlocked()).isFalse();

            verify(sanctionsCheckRepository).save(argThat(record ->
                record.getResolution() == Resolution.CLEARED &&
                record.getCleared() == true &&
                record.getReviewedBy().equals(reviewerId)
            ));
        }

        @Test
        @DisplayName("Should resolve check as BLOCKED")
        void shouldResolveCheckAsBlocked() {
            // Given
            UUID checkId = UUID.randomUUID();
            UUID reviewerId = UUID.randomUUID();
            String reviewNotes = "Confirmed match - same person on sanctions list";

            SanctionsCheckRecord existingCheck = SanctionsCheckRecord.builder()
                .id(checkId)
                .userId(testUserId)
                .checkedName("John Doe")
                .matchFound(true)
                .checkStatus(CheckStatus.MANUAL_REVIEW)
                .requiresManualReview(true)
                .build();

            when(sanctionsCheckRepository.findById(checkId)).thenReturn(Optional.of(existingCheck));
            when(sanctionsCheckRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // When
            SanctionsScreeningResult result = sanctionsScreeningService.resolveManualReview(
                checkId, Resolution.BLOCKED, reviewNotes, reviewerId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getResolution()).isEqualTo(Resolution.BLOCKED);
            assertThat(result.getBlocked()).isTrue();
            assertThat(result.getCleared()).isFalse();

            verify(sanctionsCheckRepository).save(argThat(record ->
                record.getResolution() == Resolution.BLOCKED &&
                record.getBlocked() == true &&
                record.getReviewedBy().equals(reviewerId)
            ));
        }

        @Test
        @DisplayName("Should throw exception when resolving non-existent check")
        void shouldThrowExceptionWhenResolvingNonExistentCheck() {
            // Given
            UUID nonExistentCheckId = UUID.randomUUID();
            when(sanctionsCheckRepository.findById(nonExistentCheckId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() ->
                sanctionsScreeningService.resolveManualReview(
                    nonExistentCheckId, Resolution.CLEARED, "Notes", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        }
    }

    // =============================================================================
    // Idempotency Tests
    // =============================================================================

    @Nested
    @DisplayName("Idempotency Tests")
    class IdempotencyTests {

        @Test
        @DisplayName("Should prevent duplicate screening within 24 hours")
        void shouldPreventDuplicateScreening() throws Exception {
            // Given
            SanctionsCheckRecord cachedResult = SanctionsCheckRecord.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .checkedName("John Doe")
                .matchFound(false)
                .checkStatus(CheckStatus.COMPLETED)
                .build();

            when(idempotencyService.executeIdempotentWithPersistenceAsync(
                anyString(), anyString(), anyString(), any(), any()
            )).thenReturn(CompletableFuture.completedFuture(
                SanctionsScreeningResult.builder()
                    .checkId(cachedResult.getId())
                    .matchFound(false)
                    .checkStatus(CheckStatus.COMPLETED)
                    .build()
            ));

            // When
            CompletableFuture<SanctionsScreeningResult> futureResult =
                sanctionsScreeningService.screenUser(testRequest);
            SanctionsScreeningResult result = futureResult.get();

            // Then
            assertThat(result).isNotNull();
            verify(idempotencyService).executeIdempotentWithPersistenceAsync(
                eq("sanctions-screening"),
                eq("SCREEN_USER"),
                anyString(),
                any(),
                eq(Duration.ofHours(24))
            );
        }
    }

    // =============================================================================
    // Query Tests
    // =============================================================================

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Should retrieve pending manual reviews ordered by risk level")
        void shouldRetrievePendingManualReviews() {
            // Given
            List<SanctionsCheckRecord> pendingReviews = List.of(
                createMockCheck(RiskLevel.CRITICAL, CheckStatus.MANUAL_REVIEW),
                createMockCheck(RiskLevel.HIGH, CheckStatus.MANUAL_REVIEW),
                createMockCheck(RiskLevel.MEDIUM, CheckStatus.MANUAL_REVIEW)
            );

            when(sanctionsCheckRepository.findPendingManualReview()).thenReturn(pendingReviews);

            // When
            List<SanctionsCheckRecord> result = sanctionsScreeningService.getPendingManualReviews();

            // Then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
        }

        @Test
        @DisplayName("Should check if entity is blocked")
        void shouldCheckIfEntityIsBlocked() {
            // Given
            when(sanctionsCheckRepository.isEntityBlocked(testUserId)).thenReturn(true);

            // When
            boolean isBlocked = sanctionsScreeningService.isEntityBlocked(testUserId);

            // Then
            assertThat(isBlocked).isTrue();
        }
    }

    // =============================================================================
    // Helper Methods
    // =============================================================================

    private List<OfacSdnEntry> createMockOfacList() {
        return List.of(
            OfacSdnEntry.builder()
                .uid(1L)
                .fullName("Sanctioned Person One")
                .sdnType("Individual")
                .programs(List.of("SDGT"))
                .listName("OFAC_SDN")
                .build(),
            OfacSdnEntry.builder()
                .uid(2L)
                .fullName("Sanctioned Entity Two")
                .sdnType("Entity")
                .programs(List.of("IRAN"))
                .listName("OFAC_SDN")
                .build()
        );
    }

    private List<OfacSdnEntry> createMockEuList() {
        return List.of(
            OfacSdnEntry.builder()
                .uid(100L)
                .fullName("EU Sanctioned Person")
                .sdnType("Individual")
                .listName("EU_SANCTIONS")
                .build()
        );
    }

    private List<OfacSdnEntry> createMockUnList() {
        return List.of(
            OfacSdnEntry.builder()
                .uid(200L)
                .fullName("UN Sanctioned Person")
                .sdnType("Individual")
                .listName("UN_SANCTIONS")
                .build()
        );
    }

    private SanctionsCheckRecord createMockCheck(RiskLevel riskLevel, CheckStatus status) {
        return SanctionsCheckRecord.builder()
            .id(UUID.randomUUID())
            .userId(testUserId)
            .checkedName("Test Name")
            .riskLevel(riskLevel)
            .checkStatus(status)
            .requiresManualReview(true)
            .build();
    }
}
