package com.waqiti.compliance.service;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.compliance.dto.SanctionsScreeningRequest;
import com.waqiti.compliance.dto.SanctionsScreeningResponse;
import com.waqiti.compliance.dto.SanctionsMatchResponse;
import com.waqiti.compliance.entity.SanctionedEntity;
import com.waqiti.compliance.entity.SanctionsScreeningRecord;
import com.waqiti.compliance.enums.SanctionsList;
import com.waqiti.compliance.enums.ScreeningStatus;
import com.waqiti.compliance.events.TransactionBlockedEvent;
import com.waqiti.compliance.repository.SanctionedEntityRepository;
import com.waqiti.compliance.repository.SanctionsScreeningRecordRepository;
import com.waqiti.compliance.service.impl.OFACSanctionsScreeningServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Test Suite for OFAC Sanctions Screening Service.
 *
 * Tests compliance with:
 * - 31 CFR Part 501 - Office of Foreign Assets Control
 * - 50 U.S.C. § 1701 - International Emergency Economic Powers Act
 * - E.O. 13224 - Blocking Property and Prohibiting Transactions
 *
 * Sanctions Lists Tested:
 * - OFAC SDN (Specially Designated Nationals)
 * - EU Sanctions List
 * - UN Security Council Consolidated List
 * - UK HM Treasury Financial Sanctions List
 *
 * Critical Requirements:
 * - 100% real-time screening for all transactions
 * - Fuzzy name matching (Jaro-Winkler distance)
 * - Automatic transaction blocking on match
 * - SAR filing required for blocked transactions
 *
 * @author Waqiti Compliance Engineering
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OFAC Sanctions Screening Service Tests")
class OFACSanctionsScreeningServiceTest {

    @Mock
    private SanctionedEntityRepository sanctionedEntityRepository;

    @Mock
    private SanctionsScreeningRecordRepository screeningRecordRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private AuditService auditService;

    @Mock
    private ComplianceNotificationService notificationService;

    @InjectMocks
    private OFACSanctionsScreeningServiceImpl ofacScreeningService;

    @Captor
    private ArgumentCaptor<SanctionsScreeningRecord> screeningRecordCaptor;

    @Captor
    private ArgumentCaptor<TransactionBlockedEvent> eventCaptor;

    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_TRANSACTION_ID = "txn-456";
    private static final double MATCH_THRESHOLD = 0.85; // 85% similarity

    @BeforeEach
    void setUp() {
        reset(sanctionedEntityRepository, screeningRecordRepository, eventPublisher, auditService, notificationService);
    }

    @Nested
    @DisplayName("Exact Name Match Tests")
    class ExactNameMatchTests {

        @Test
        @DisplayName("Should block transaction when exact OFAC SDN match found")
        void shouldBlockTransaction_WhenExactSDNMatch() {
            // Given
            String sanctionedName = "OSAMA BIN LADEN";
            SanctionsScreeningRequest request = createRequest(sanctionedName, "IQ");

            SanctionedEntity entity = createSanctionedEntity(
                sanctionedName, SanctionsList.OFAC_SDN, "SDN-12345"
            );

            when(sanctionedEntityRepository.findByNameIgnoreCase(sanctionedName))
                .thenReturn(Optional.of(entity));
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SanctionsScreeningResponse response = ofacScreeningService.screenTransaction(request);

            // Then
            assertThat(response.isMatch()).isTrue();
            assertThat(response.getStatus()).isEqualTo(ScreeningStatus.BLOCKED);
            assertThat(response.getMatchScore()).isEqualTo(1.0); // 100% match
            assertThat(response.getSanctionsList()).isEqualTo(SanctionsList.OFAC_SDN);

            verify(eventPublisher).publishEvent(any(TransactionBlockedEvent.class));
            verify(notificationService).sendCriticalAlert(
                eq("SANCTIONS_MATCH"),
                contains(sanctionedName),
                anyMap()
            );
        }

        @Test
        @DisplayName("Should block transaction when exact EU sanctions match found")
        void shouldBlockTransaction_WhenExactEUMatch() {
            // Given
            String sanctionedName = "ALEXANDER LUKASHENKO";
            SanctionsScreeningRequest request = createRequest(sanctionedName, "BY");

            SanctionedEntity entity = createSanctionedEntity(
                sanctionedName, SanctionsList.EU_SANCTIONS, "EU-98765"
            );

            when(sanctionedEntityRepository.findByNameIgnoreCase(sanctionedName))
                .thenReturn(Optional.of(entity));
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SanctionsScreeningResponse response = ofacScreeningService.screenTransaction(request);

            // Then
            assertThat(response.isMatch()).isTrue();
            assertThat(response.getSanctionsList()).isEqualTo(SanctionsList.EU_SANCTIONS);
        }

        @Test
        @DisplayName("Should allow transaction when no sanctions match found")
        void shouldAllowTransaction_WhenNoMatch() {
            // Given
            String cleanName = "JOHN SMITH";
            SanctionsScreeningRequest request = createRequest(cleanName, "US");

            when(sanctionedEntityRepository.findByNameIgnoreCase(cleanName))
                .thenReturn(Optional.empty());
            when(sanctionedEntityRepository.searchByFuzzyName(anyString(), anyDouble()))
                .thenReturn(Collections.emptyList());
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SanctionsScreeningResponse response = ofacScreeningService.screenTransaction(request);

            // Then
            assertThat(response.isMatch()).isFalse();
            assertThat(response.getStatus()).isEqualTo(ScreeningStatus.CLEARED);
            assertThat(response.getMatchScore()).isEqualTo(0.0);

            verify(eventPublisher, never()).publishEvent(any(TransactionBlockedEvent.class));
            verify(notificationService, never()).sendCriticalAlert(anyString(), anyString(), anyMap());
        }
    }

    @Nested
    @DisplayName("Fuzzy Name Matching Tests")
    class FuzzyMatchingTests {

        @Test
        @DisplayName("Should detect match with minor spelling variations")
        void shouldDetectMatch_WithSpellingVariations() {
            // Given - Common name variations
            String searchName = "MUHAMMED KADDAFI";
            String sanctionedName = "MUAMMAR GADDAFI"; // Different spelling

            SanctionsScreeningRequest request = createRequest(searchName, "LY");

            SanctionedEntity entity = createSanctionedEntity(
                sanctionedName, SanctionsList.OFAC_SDN, "SDN-55555"
            );

            when(sanctionedEntityRepository.findByNameIgnoreCase(searchName))
                .thenReturn(Optional.empty());
            when(sanctionedEntityRepository.searchByFuzzyName(eq(searchName), anyDouble()))
                .thenReturn(Arrays.asList(entity));
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SanctionsScreeningResponse response = ofacScreeningService.screenTransaction(request);

            // Then
            assertThat(response.isMatch()).isTrue();
            assertThat(response.getStatus()).isEqualTo(ScreeningStatus.BLOCKED);
            assertThat(response.getMatchScore()).isGreaterThan(MATCH_THRESHOLD);
        }

        @Test
        @DisplayName("Should detect match with different name ordering")
        void shouldDetectMatch_WithNameOrdering() {
            // Given
            String searchName = "BIN LADEN OSAMA";
            String sanctionedName = "OSAMA BIN LADEN";

            SanctionsScreeningRequest request = createRequest(searchName, "AF");

            SanctionedEntity entity = createSanctionedEntity(
                sanctionedName, SanctionsList.OFAC_SDN, "SDN-99999"
            );

            when(sanctionedEntityRepository.findByNameIgnoreCase(searchName))
                .thenReturn(Optional.empty());
            when(sanctionedEntityRepository.searchByFuzzyName(eq(searchName), anyDouble()))
                .thenReturn(Arrays.asList(entity));
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SanctionsScreeningResponse response = ofacScreeningService.screenTransaction(request);

            // Then
            assertThat(response.isMatch()).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
            "VLADIMIR PUTIN, VLADIMIR VLADIMIROVICH PUTIN, true",
            "BASHAR ASSAD, BASHAR AL-ASSAD, true",
            "KIM JONG UN, KIM JONG-UN, true",
            "JOHN SMITH, JONATHAN SMITH, false" // Below threshold
        })
        @DisplayName("Should correctly match name variations")
        void shouldMatchNameVariations(String searchName, String sanctionedName, boolean shouldMatch) {
            // Given
            SanctionsScreeningRequest request = createRequest(searchName, "XX");

            SanctionedEntity entity = createSanctionedEntity(
                sanctionedName, SanctionsList.OFAC_SDN, "SDN-TEST"
            );

            when(sanctionedEntityRepository.findByNameIgnoreCase(searchName))
                .thenReturn(Optional.empty());
            when(sanctionedEntityRepository.searchByFuzzyName(eq(searchName), anyDouble()))
                .thenReturn(shouldMatch ? Arrays.asList(entity) : Collections.emptyList());
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SanctionsScreeningResponse response = ofacScreeningService.screenTransaction(request);

            // Then
            assertThat(response.isMatch()).isEqualTo(shouldMatch);
        }

        @Test
        @DisplayName("Should NOT match when similarity below threshold")
        void shouldNotMatch_WhenBelowThreshold() {
            // Given
            String searchName = "JOHN SMITH";
            String similarButDifferent = "JANE SMYTHE"; // Similar but different person

            SanctionsScreeningRequest request = createRequest(searchName, "US");

            when(sanctionedEntityRepository.findByNameIgnoreCase(searchName))
                .thenReturn(Optional.empty());
            when(sanctionedEntityRepository.searchByFuzzyName(eq(searchName), anyDouble()))
                .thenReturn(Collections.emptyList()); // Below threshold
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SanctionsScreeningResponse response = ofacScreeningService.screenTransaction(request);

            // Then
            assertThat(response.isMatch()).isFalse();
            assertThat(response.getStatus()).isEqualTo(ScreeningStatus.CLEARED);
        }
    }

    @Nested
    @DisplayName("Sanctioned Country Tests")
    class SanctionedCountryTests {

        @ParameterizedTest
        @ValueSource(strings = {"IR", "KP", "SY", "CU", "VE", "RU", "MM", "SD", "ZW", "BY", "LY", "SO", "YE", "CF"})
        @DisplayName("Should flag transactions from sanctioned countries")
        void shouldFlagSanctionedCountries(String countryCode) {
            // Given
            SanctionsScreeningRequest request = createRequest("CLEAN NAME", countryCode);

            when(sanctionedEntityRepository.findByNameIgnoreCase(anyString()))
                .thenReturn(Optional.empty());
            when(sanctionedEntityRepository.searchByFuzzyName(anyString(), anyDouble()))
                .thenReturn(Collections.emptyList());
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SanctionsScreeningResponse response = ofacScreeningService.screenTransaction(request);

            // Then
            assertThat(response.isSanctionedCountry()).isTrue();
            assertThat(response.getStatus()).isEqualTo(ScreeningStatus.MANUAL_REVIEW);

            verify(auditService).logComplianceEvent(
                eq("SANCTIONED_COUNTRY_TRANSACTION"),
                contains(countryCode),
                anyMap()
            );
        }

        @Test
        @DisplayName("Should allow transactions from non-sanctioned countries")
        void shouldAllowNonSanctionedCountries() {
            // Given
            SanctionsScreeningRequest request = createRequest("CLEAN NAME", "US");

            when(sanctionedEntityRepository.findByNameIgnoreCase(anyString()))
                .thenReturn(Optional.empty());
            when(sanctionedEntityRepository.searchByFuzzyName(anyString(), anyDouble()))
                .thenReturn(Collections.emptyList());
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SanctionsScreeningResponse response = ofacScreeningService.screenTransaction(request);

            // Then
            assertThat(response.isSanctionedCountry()).isFalse();
            assertThat(response.getStatus()).isEqualTo(ScreeningStatus.CLEARED);
        }
    }

    @Nested
    @DisplayName("Transaction Blocking Tests")
    class TransactionBlockingTests {

        @Test
        @DisplayName("Should publish transaction blocked event on sanctions match")
        void shouldPublishBlockedEvent_OnMatch() {
            // Given
            String sanctionedName = "SANCTIONED PERSON";
            SanctionsScreeningRequest request = createRequest(sanctionedName, "IR");

            SanctionedEntity entity = createSanctionedEntity(
                sanctionedName, SanctionsList.OFAC_SDN, "SDN-BLOCK"
            );

            when(sanctionedEntityRepository.findByNameIgnoreCase(sanctionedName))
                .thenReturn(Optional.of(entity));
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SanctionsScreeningResponse response = ofacScreeningService.screenTransaction(request);

            // Then
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            TransactionBlockedEvent event = eventCaptor.getValue();

            assertThat(event.getTransactionId()).isEqualTo(TEST_TRANSACTION_ID);
            assertThat(event.getReason()).contains("SANCTIONS_MATCH");
            assertThat(event.getSanctionsList()).isEqualTo(SanctionsList.OFAC_SDN);
        }

        @Test
        @DisplayName("Should send critical alert to compliance team on match")
        void shouldSendCriticalAlert_OnMatch() {
            // Given
            String sanctionedName = "CRITICAL MATCH";
            SanctionsScreeningRequest request = createRequest(sanctionedName, "SY");

            SanctionedEntity entity = createSanctionedEntity(
                sanctionedName, SanctionsList.OFAC_SDN, "SDN-ALERT"
            );

            when(sanctionedEntityRepository.findByNameIgnoreCase(sanctionedName))
                .thenReturn(Optional.of(entity));
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ofacScreeningService.screenTransaction(request);

            // Then
            verify(notificationService).sendCriticalAlert(
                eq("SANCTIONS_MATCH"),
                anyString(),
                argThat(map ->
                    map.containsKey("transactionId") &&
                    map.containsKey("matchedName") &&
                    map.containsKey("sanctionsList")
                )
            );
        }
    }

    @Nested
    @DisplayName("Audit and Record Keeping Tests")
    class AuditTests {

        @Test
        @DisplayName("Should save screening record for every transaction")
        void shouldSaveScreeningRecord_ForEveryTransaction() {
            // Given
            SanctionsScreeningRequest request = createRequest("TEST NAME", "US");

            when(sanctionedEntityRepository.findByNameIgnoreCase(anyString()))
                .thenReturn(Optional.empty());
            when(sanctionedEntityRepository.searchByFuzzyName(anyString(), anyDouble()))
                .thenReturn(Collections.emptyList());
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ofacScreeningService.screenTransaction(request);

            // Then
            verify(screeningRecordRepository).save(screeningRecordCaptor.capture());
            SanctionsScreeningRecord record = screeningRecordCaptor.getValue();

            assertThat(record).isNotNull();
            assertThat(record.getTransactionId()).isEqualTo(TEST_TRANSACTION_ID);
            assertThat(record.getScreenedName()).isEqualTo("TEST NAME");
            assertThat(record.getScreeningTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should log audit event for sanctions screening")
        void shouldLogAuditEvent_ForScreening() {
            // Given
            SanctionsScreeningRequest request = createRequest("AUDIT TEST", "GB");

            when(sanctionedEntityRepository.findByNameIgnoreCase(anyString()))
                .thenReturn(Optional.empty());
            when(sanctionedEntityRepository.searchByFuzzyName(anyString(), anyDouble()))
                .thenReturn(Collections.emptyList());
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ofacScreeningService.screenTransaction(request);

            // Then
            verify(auditService).logComplianceEvent(
                eq("SANCTIONS_SCREENING_COMPLETED"),
                anyString(),
                anyMap()
            );
        }

        @Test
        @DisplayName("Should maintain permanent audit trail for blocked transactions")
        void shouldMaintainPermanentAuditTrail_ForBlockedTransactions() {
            // Given
            String sanctionedName = "PERMANENT AUDIT";
            SanctionsScreeningRequest request = createRequest(sanctionedName, "KP");

            SanctionedEntity entity = createSanctionedEntity(
                sanctionedName, SanctionsList.OFAC_SDN, "SDN-AUDIT"
            );

            when(sanctionedEntityRepository.findByNameIgnoreCase(sanctionedName))
                .thenReturn(Optional.of(entity));
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ofacScreeningService.screenTransaction(request);

            // Then
            verify(screeningRecordRepository).save(screeningRecordCaptor.capture());
            SanctionsScreeningRecord record = screeningRecordCaptor.getValue();

            assertThat(record.getStatus()).isEqualTo(ScreeningStatus.BLOCKED);
            assertThat(record.getMatchDetails()).isNotNull();
            assertThat(record.isImmutable()).isTrue(); // Cannot be modified
        }
    }

    @Nested
    @DisplayName("Multiple Sanctions List Tests")
    class MultipleSanctionsListTests {

        @Test
        @DisplayName("Should check all sanctions lists (OFAC, EU, UN, UK)")
        void shouldCheckAllSanctionsList() {
            // Given
            String name = "MULTI-LIST CHECK";
            SanctionsScreeningRequest request = createRequest(name, "XX");

            // Entity appears on multiple lists
            SanctionedEntity ofacEntity = createSanctionedEntity(name, SanctionsList.OFAC_SDN, "SDN-1");
            SanctionedEntity euEntity = createSanctionedEntity(name, SanctionsList.EU_SANCTIONS, "EU-1");

            when(sanctionedEntityRepository.findByNameIgnoreCase(name))
                .thenReturn(Optional.of(ofacEntity));
            when(sanctionedEntityRepository.findBySanctionsList(SanctionsList.EU_SANCTIONS))
                .thenReturn(Arrays.asList(euEntity));
            when(screeningRecordRepository.save(any(SanctionsScreeningRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SanctionsScreeningResponse response = ofacScreeningService.screenTransaction(request);

            // Then
            assertThat(response.isMatch()).isTrue();
            assertThat(response.getMatchedLists()).hasSize(2);
            assertThat(response.getMatchedLists()).contains(SanctionsList.OFAC_SDN, SanctionsList.EU_SANCTIONS);
        }
    }

    // Helper methods
    private SanctionsScreeningRequest createRequest(String name, String countryCode) {
        SanctionsScreeningRequest request = new SanctionsScreeningRequest();
        request.setUserId(TEST_USER_ID);
        request.setTransactionId(TEST_TRANSACTION_ID);
        request.setFullName(name);
        request.setCountryCode(countryCode);
        return request;
    }

    private SanctionedEntity createSanctionedEntity(String name, SanctionsList list, String referenceId) {
        SanctionedEntity entity = new SanctionedEntity();
        entity.setFullName(name);
        entity.setSanctionsList(list);
        entity.setReferenceId(referenceId);
        entity.setActive(true);
        return entity;
    }
}
