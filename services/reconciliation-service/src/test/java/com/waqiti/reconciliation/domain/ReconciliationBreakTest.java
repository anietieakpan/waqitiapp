package com.waqiti.reconciliation.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.waqiti.reconciliation.repository.ReconciliationBreakRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Reconciliation Break Management Tests")
class ReconciliationBreakTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private ReconciliationBreakRepository breakRepository;

    private ReconciliationBreak testBreak;

    @BeforeEach
    void setUp() {
        breakRepository.deleteAll();

        testBreak = ReconciliationBreak.builder()
                .id(UUID.randomUUID())
                .breakReference("BRK-TEST-001")
                .breakType(ReconciliationBreak.BreakType.AMOUNT_VARIANCE)
                .severity(ReconciliationBreak.Severity.HIGH)
                .accountNumber("ACC-12345")
                .currency("USD")
                .amount(new BigDecimal("10000.00"))
                .varianceAmount(new BigDecimal("500.00"))
                .description("Test break for unit testing")
                .detectedAt(LocalDateTime.now())
                .detectedBy("recon-service")
                .detectionMethod("AUTOMATED_MATCHING")
                .status(ReconciliationBreak.BreakStatus.NEW)
                .autoResolutionAttempted(false)
                .autoResolutionSuccessful(false)
                .requiresRegulatoryReporting(false)
                .build();
    }

    @Nested
    @DisplayName("Break Creation Tests")
    class BreakCreationTests {

        @Test
        @Transactional
        @DisplayName("Should create break with all required fields")
        void shouldCreateBreakWithAllRequiredFields() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getBreakReference()).isEqualTo("BRK-TEST-001");
            assertThat(saved.getBreakType()).isEqualTo(ReconciliationBreak.BreakType.AMOUNT_VARIANCE);
            assertThat(saved.getSeverity()).isEqualTo(ReconciliationBreak.Severity.HIGH);
            assertThat(saved.getStatus()).isEqualTo(ReconciliationBreak.BreakStatus.NEW);
        }

        @Test
        @Transactional
        @DisplayName("Should generate unique break references")
        void shouldGenerateUniqueBreakReferences() {
            ReconciliationBreak break1 = testBreak;
            break1.setBreakReference("BRK-001");
            breakRepository.save(break1);

            ReconciliationBreak break2 = ReconciliationBreak.builder()
                    .id(UUID.randomUUID())
                    .breakReference("BRK-002")
                    .breakType(ReconciliationBreak.BreakType.AMOUNT_VARIANCE)
                    .severity(ReconciliationBreak.Severity.MEDIUM)
                    .accountNumber("ACC-67890")
                    .currency("USD")
                    .amount(new BigDecimal("5000.00"))
                    .varianceAmount(new BigDecimal("100.00"))
                    .detectedAt(LocalDateTime.now())
                    .status(ReconciliationBreak.BreakStatus.NEW)
                    .autoResolutionAttempted(false)
                    .autoResolutionSuccessful(false)
                    .requiresRegulatoryReporting(false)
                    .build();
            breakRepository.save(break2);

            List<ReconciliationBreak> breaks = breakRepository.findAll();
            assertThat(breaks).hasSize(2);
            assertThat(breaks).extracting("breakReference").containsExactlyInAnyOrder("BRK-001", "BRK-002");
        }

        @Test
        @Transactional
        @DisplayName("Should support all break types")
        void shouldSupportAllBreakTypes() {
            ReconciliationBreak.BreakType[] types = ReconciliationBreak.BreakType.values();

            for (int i = 0; i < types.length; i++) {
                ReconciliationBreak breakItem = ReconciliationBreak.builder()
                        .id(UUID.randomUUID())
                        .breakReference("BRK-TYPE-" + i)
                        .breakType(types[i])
                        .severity(ReconciliationBreak.Severity.MEDIUM)
                        .accountNumber("ACC-" + i)
                        .currency("USD")
                        .amount(new BigDecimal("1000.00"))
                        .detectedAt(LocalDateTime.now())
                        .status(ReconciliationBreak.BreakStatus.NEW)
                        .autoResolutionAttempted(false)
                        .autoResolutionSuccessful(false)
                        .requiresRegulatoryReporting(false)
                        .build();
                breakRepository.save(breakItem);
            }

            List<ReconciliationBreak> breaks = breakRepository.findAll();
            assertThat(breaks).hasSize(types.length);
        }

        @Test
        @Transactional
        @DisplayName("Should categorize breaks by severity")
        void shouldCategorizeBreaksBySeverity() {
            Map<ReconciliationBreak.Severity, Integer> severityLevels = Map.of(
                    ReconciliationBreak.Severity.EMERGENCY, 5,
                    ReconciliationBreak.Severity.CRITICAL, 4,
                    ReconciliationBreak.Severity.HIGH, 3,
                    ReconciliationBreak.Severity.MEDIUM, 2,
                    ReconciliationBreak.Severity.LOW, 1
            );

            severityLevels.forEach((severity, level) -> {
                assertThat(severity.getLevel()).isEqualTo(level);
            });
        }
    }

    @Nested
    @DisplayName("Break Assignment Tests")
    class BreakAssignmentTests {

        @Test
        @Transactional
        @DisplayName("Should assign break to user")
        void shouldAssignBreakToUser() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.setStatus(ReconciliationBreak.BreakStatus.ASSIGNED);
            saved.setAssignedTo("finance-analyst-1");
            saved.setAssignedAt(LocalDateTime.now());
            
            breakRepository.save(saved);

            ReconciliationBreak assigned = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(assigned.getAssignedTo()).isEqualTo("finance-analyst-1");
            assertThat(assigned.getAssignedAt()).isNotNull();
            assertThat(assigned.getStatus()).isEqualTo(ReconciliationBreak.BreakStatus.ASSIGNED);
        }

        @Test
        @Transactional
        @DisplayName("Should track investigation start time")
        void shouldTrackInvestigationStartTime() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.setStatus(ReconciliationBreak.BreakStatus.INVESTIGATING);
            saved.setInvestigationStartedAt(LocalDateTime.now());
            
            breakRepository.save(saved);

            ReconciliationBreak investigating = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(investigating.getInvestigationStartedAt()).isNotNull();
            assertThat(investigating.getStatus()).isEqualTo(ReconciliationBreak.BreakStatus.INVESTIGATING);
        }

        @Test
        @Transactional
        @DisplayName("Should support break reassignment")
        void shouldSupportBreakReassignment() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.setAssignedTo("analyst-1");
            saved.setAssignedAt(LocalDateTime.now().minusHours(2));
            breakRepository.save(saved);

            saved.setAssignedTo("analyst-2");
            saved.setAssignedAt(LocalDateTime.now());
            breakRepository.save(saved);

            ReconciliationBreak reassigned = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(reassigned.getAssignedTo()).isEqualTo("analyst-2");
        }
    }

    @Nested
    @DisplayName("Break Resolution Tests")
    class BreakResolutionTests {

        @Test
        @Transactional
        @DisplayName("Should resolve break successfully")
        void shouldResolveBreakSuccessfully() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.resolve(ReconciliationBreak.ResolutionType.MANUAL_ADJUSTMENT, 
                         "finance-manager", 
                         "Manual adjustment applied to correct variance");

            breakRepository.save(saved);

            ReconciliationBreak resolved = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(resolved.getStatus()).isEqualTo(ReconciliationBreak.BreakStatus.RESOLVED);
            assertThat(resolved.getResolutionType()).isEqualTo(ReconciliationBreak.ResolutionType.MANUAL_ADJUSTMENT);
            assertThat(resolved.getResolvedBy()).isEqualTo("finance-manager");
            assertThat(resolved.getResolvedAt()).isNotNull();
        }

        @Test
        @Transactional
        @DisplayName("Should track resolution time")
        void shouldTrackResolutionTime() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.setDetectedAt(LocalDateTime.now().minusHours(5));
            saved.resolve(ReconciliationBreak.ResolutionType.MATCHED, "system", "Auto-matched");
            
            breakRepository.save(saved);

            ReconciliationBreak resolved = breakRepository.findById(saved.getId()).orElseThrow();
            long resolutionHours = resolved.getResolutionTimeInHours();
            
            assertThat(resolutionHours).isGreaterThan(0);
            assertThat(resolutionHours).isLessThanOrEqualTo(6);
        }

        @Test
        @Transactional
        @DisplayName("Should support different resolution types")
        void shouldSupportDifferentResolutionTypes() {
            ReconciliationBreak.ResolutionType[] types = ReconciliationBreak.ResolutionType.values();

            for (int i = 0; i < types.length; i++) {
                ReconciliationBreak breakItem = ReconciliationBreak.builder()
                        .id(UUID.randomUUID())
                        .breakReference("BRK-RES-" + i)
                        .breakType(ReconciliationBreak.BreakType.AMOUNT_VARIANCE)
                        .severity(ReconciliationBreak.Severity.MEDIUM)
                        .accountNumber("ACC-" + i)
                        .currency("USD")
                        .amount(new BigDecimal("1000.00"))
                        .detectedAt(LocalDateTime.now())
                        .status(ReconciliationBreak.BreakStatus.RESOLVED)
                        .resolutionType(types[i])
                        .autoResolutionAttempted(false)
                        .autoResolutionSuccessful(false)
                        .requiresRegulatoryReporting(false)
                        .build();
                breakRepository.save(breakItem);
            }

            List<ReconciliationBreak> breaks = breakRepository.findAll();
            assertThat(breaks).hasSize(types.length);
        }

        @Test
        @Transactional
        @DisplayName("Should add resolution notes")
        void shouldAddResolutionNotes() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            String notes = "Variance caused by delayed bank processing. Manual adjustment posted to GL account 1200.";
            saved.setResolutionNotes(notes);
            saved.setStatus(ReconciliationBreak.BreakStatus.RESOLVED);
            
            breakRepository.save(saved);

            ReconciliationBreak resolved = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(resolved.getResolutionNotes()).isEqualTo(notes);
        }

        @Test
        @Transactional
        @DisplayName("Should check if break is resolved")
        void shouldCheckIfBreakIsResolved() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            assertThat(saved.isResolved()).isFalse();

            saved.setStatus(ReconciliationBreak.BreakStatus.RESOLVED);
            assertThat(saved.isResolved()).isTrue();

            saved.setStatus(ReconciliationBreak.BreakStatus.CLOSED);
            assertThat(saved.isResolved()).isTrue();
        }
    }

    @Nested
    @DisplayName("Break Escalation Tests")
    class BreakEscalationTests {

        @Test
        @Transactional
        @DisplayName("Should escalate critical breaks immediately")
        void shouldEscalateCriticalBreaksImmediately() {
            ReconciliationBreak criticalBreak = testBreak;
            criticalBreak.setSeverity(ReconciliationBreak.Severity.CRITICAL);
            
            assertThat(criticalBreak.requiresEscalation()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should escalate emergency breaks immediately")
        void shouldEscalateEmergencyBreaksImmediately() {
            ReconciliationBreak emergencyBreak = testBreak;
            emergencyBreak.setSeverity(ReconciliationBreak.Severity.EMERGENCY);
            
            assertThat(emergencyBreak.requiresEscalation()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should escalate high severity breaks after 4 hours")
        void shouldEscalateHighSeverityBreaksAfter4Hours() {
            ReconciliationBreak highBreak = testBreak;
            highBreak.setSeverity(ReconciliationBreak.Severity.HIGH);
            highBreak.setDetectedAt(LocalDateTime.now().minusHours(5));
            
            assertThat(highBreak.requiresEscalation()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should escalate medium severity breaks after 24 hours")
        void shouldEscalateMediumSeverityBreaksAfter24Hours() {
            ReconciliationBreak mediumBreak = testBreak;
            mediumBreak.setSeverity(ReconciliationBreak.Severity.MEDIUM);
            mediumBreak.setDetectedAt(LocalDateTime.now().minusHours(25));
            
            assertThat(mediumBreak.requiresEscalation()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should escalate low severity breaks after 72 hours")
        void shouldEscalateLowSeverityBreaksAfter72Hours() {
            ReconciliationBreak lowBreak = testBreak;
            lowBreak.setSeverity(ReconciliationBreak.Severity.LOW);
            lowBreak.setDetectedAt(LocalDateTime.now().minusHours(73));
            
            assertThat(lowBreak.requiresEscalation()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should track escalation details")
        void shouldTrackEscalationDetails() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.escalate("senior-finance-manager", "High value unresolved for >4 hours");
            
            breakRepository.save(saved);

            ReconciliationBreak escalated = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(escalated.getStatus()).isEqualTo(ReconciliationBreak.BreakStatus.ESCALATED);
            assertThat(escalated.getEscalatedTo()).isEqualTo("senior-finance-manager");
            assertThat(escalated.getEscalationReason()).contains("High value");
            assertThat(escalated.getEscalatedAt()).isNotNull();
        }

        @Test
        @Transactional
        @DisplayName("Should add escalation comment")
        void shouldAddEscalationComment() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.escalate("manager", "SLA breach");
            
            breakRepository.save(saved);

            ReconciliationBreak escalated = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(escalated.getComments()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Break SLA Tests")
    class BreakSLATests {

        @Test
        @Transactional
        @DisplayName("Should validate emergency break SLA (1 hour)")
        void shouldValidateEmergencyBreakSLA() {
            ReconciliationBreak emergencyBreak = testBreak;
            emergencyBreak.setSeverity(ReconciliationBreak.Severity.EMERGENCY);
            emergencyBreak.setDetectedAt(LocalDateTime.now().minusMinutes(30));
            emergencyBreak.resolve(ReconciliationBreak.ResolutionType.SYSTEM_CORRECTION, "system", "Fixed");
            
            assertThat(emergencyBreak.isWithinSLA()).isTrue();

            ReconciliationBreak lateEmergency = testBreak;
            lateEmergency.setSeverity(ReconciliationBreak.Severity.EMERGENCY);
            lateEmergency.setDetectedAt(LocalDateTime.now().minusHours(2));
            lateEmergency.resolve(ReconciliationBreak.ResolutionType.SYSTEM_CORRECTION, "system", "Fixed");
            
            assertThat(lateEmergency.isWithinSLA()).isFalse();
        }

        @Test
        @Transactional
        @DisplayName("Should validate critical break SLA (4 hours)")
        void shouldValidateCriticalBreakSLA() {
            ReconciliationBreak criticalBreak = testBreak;
            criticalBreak.setSeverity(ReconciliationBreak.Severity.CRITICAL);
            criticalBreak.setDetectedAt(LocalDateTime.now().minusHours(3));
            criticalBreak.resolve(ReconciliationBreak.ResolutionType.MANUAL_ADJUSTMENT, "user", "Resolved");
            
            assertThat(criticalBreak.isWithinSLA()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should validate high severity break SLA (24 hours)")
        void shouldValidateHighSeverityBreakSLA() {
            ReconciliationBreak highBreak = testBreak;
            highBreak.setSeverity(ReconciliationBreak.Severity.HIGH);
            highBreak.setDetectedAt(LocalDateTime.now().minusHours(20));
            highBreak.resolve(ReconciliationBreak.ResolutionType.MATCHED, "system", "Matched");
            
            assertThat(highBreak.isWithinSLA()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should validate medium severity break SLA (72 hours)")
        void shouldValidateMediumSeverityBreakSLA() {
            ReconciliationBreak mediumBreak = testBreak;
            mediumBreak.setSeverity(ReconciliationBreak.Severity.MEDIUM);
            mediumBreak.setDetectedAt(LocalDateTime.now().minusHours(48));
            mediumBreak.resolve(ReconciliationBreak.ResolutionType.DATA_CORRECTION, "analyst", "Corrected");
            
            assertThat(mediumBreak.isWithinSLA()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should validate low severity break SLA (7 days)")
        void shouldValidateLowSeverityBreakSLA() {
            ReconciliationBreak lowBreak = testBreak;
            lowBreak.setSeverity(ReconciliationBreak.Severity.LOW);
            lowBreak.setDetectedAt(LocalDateTime.now().minusDays(5));
            lowBreak.resolve(ReconciliationBreak.ResolutionType.TIMING_DIFFERENCE, "system", "Timing");
            
            assertThat(lowBreak.isWithinSLA()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should identify overdue breaks")
        void shouldIdentifyOverdueBreaks() {
            ReconciliationBreak overdueBreak = testBreak;
            overdueBreak.setDueDate(LocalDateTime.now().minusHours(1));
            overdueBreak.setStatus(ReconciliationBreak.BreakStatus.OPEN);
            
            assertThat(overdueBreak.isOverdue()).isTrue();

            ReconciliationBreak resolvedBreak = testBreak;
            resolvedBreak.setDueDate(LocalDateTime.now().minusHours(1));
            resolvedBreak.setStatus(ReconciliationBreak.BreakStatus.RESOLVED);
            
            assertThat(resolvedBreak.isOverdue()).isFalse();
        }
    }

    @Nested
    @DisplayName("Break Comments Tests")
    class BreakCommentsTests {

        @Test
        @Transactional
        @DisplayName("Should add comments to break")
        void shouldAddCommentsToBreak() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.addComment("Initial investigation started", "analyst-1");
            saved.addComment("Found root cause in bank processing", "analyst-1");
            
            breakRepository.save(saved);

            ReconciliationBreak withComments = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(withComments.getComments()).hasSize(2);
        }

        @Test
        @Transactional
        @DisplayName("Should timestamp comments")
        void shouldTimestampComments() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            LocalDateTime before = LocalDateTime.now();
            saved.addComment("Test comment", "user1");
            LocalDateTime after = LocalDateTime.now();
            
            breakRepository.save(saved);

            ReconciliationBreak withComment = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(withComment.getComments()).isNotEmpty();
        }

        @Test
        @Transactional
        @DisplayName("Should include user in comment")
        void shouldIncludeUserInComment() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.addComment("Requesting approval", "analyst-2");
            
            breakRepository.save(saved);

            ReconciliationBreak withComment = breakRepository.findById(saved.getId()).orElseThrow();
            String comment = withComment.getComments().values().iterator().next();
            
            assertThat(comment).contains("analyst-2");
        }
    }

    @Nested
    @DisplayName("Break Age Tracking Tests")
    class BreakAgeTrackingTests {

        @Test
        @Transactional
        @DisplayName("Should calculate break age for open breaks")
        void shouldCalculateBreakAgeForOpenBreaks() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.setDetectedAt(LocalDateTime.now().minusHours(10));
            
            long ageInHours = saved.getAgeInHours();
            
            assertThat(ageInHours).isGreaterThanOrEqualTo(9);
            assertThat(ageInHours).isLessThanOrEqualTo(11);
        }

        @Test
        @Transactional
        @DisplayName("Should calculate break age for resolved breaks")
        void shouldCalculateBreakAgeForResolvedBreaks() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.setDetectedAt(LocalDateTime.now().minusHours(20));
            saved.setResolvedAt(LocalDateTime.now().minusHours(5));
            
            long ageInHours = saved.getAgeInHours();
            
            assertThat(ageInHours).isLessThanOrEqualTo(16);
        }

        @Test
        @Transactional
        @DisplayName("Should return zero age for breaks without detection time")
        void shouldReturnZeroAgeForBreaksWithoutDetectionTime() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.setDetectedAt(null);
            
            long ageInHours = saved.getAgeInHours();
            
            assertThat(ageInHours).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Break Metadata Tests")
    class BreakMetadataTests {

        @Test
        @Transactional
        @DisplayName("Should store break metadata")
        void shouldStoreBreakMetadata() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("source_system", "BANK_HSBC");
            metadata.put("account_type", "NOSTRO");
            metadata.put("business_unit", "TREASURY");
            
            saved.setMetadata(metadata);
            breakRepository.save(saved);

            ReconciliationBreak withMetadata = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(withMetadata.getMetadata()).hasSize(3);
            assertThat(withMetadata.getMetadata()).containsKey("source_system");
        }

        @Test
        @Transactional
        @DisplayName("Should store affected entry IDs")
        void shouldStoreAffectedEntryIds() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            List<UUID> entryIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            saved.setAffectedEntryIds(entryIds);
            
            breakRepository.save(saved);

            ReconciliationBreak withEntries = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(withEntries.getAffectedEntryIds()).hasSize(3);
        }

        @Test
        @Transactional
        @DisplayName("Should store attachments")
        void shouldStoreAttachments() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            List<String> attachments = List.of(
                    "/documents/break-evidence-001.pdf",
                    "/documents/bank-statement-screenshot.png"
            );
            saved.setAttachments(attachments);
            
            breakRepository.save(saved);

            ReconciliationBreak withAttachments = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(withAttachments.getAttachments()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Auto Resolution Tests")
    class AutoResolutionTests {

        @Test
        @Transactional
        @DisplayName("Should track auto resolution attempts")
        void shouldTrackAutoResolutionAttempts() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.setAutoResolutionAttempted(true);
            saved.setAutoResolutionSuccessful(false);
            
            breakRepository.save(saved);

            ReconciliationBreak attempted = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(attempted.getAutoResolutionAttempted()).isTrue();
            assertThat(attempted.getAutoResolutionSuccessful()).isFalse();
        }

        @Test
        @Transactional
        @DisplayName("Should track successful auto resolution")
        void shouldTrackSuccessfulAutoResolution() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.setAutoResolutionAttempted(true);
            saved.setAutoResolutionSuccessful(true);
            saved.setStatus(ReconciliationBreak.BreakStatus.RESOLVED);
            saved.setResolutionType(ReconciliationBreak.ResolutionType.MATCHED);
            
            breakRepository.save(saved);

            ReconciliationBreak autoResolved = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(autoResolved.getAutoResolutionSuccessful()).isTrue();
            assertThat(autoResolved.isResolved()).isTrue();
        }
    }

    @Nested
    @DisplayName("Regulatory Reporting Tests")
    class RegulatoryReportingTests {

        @Test
        @Transactional
        @DisplayName("Should flag breaks requiring regulatory reporting")
        void shouldFlagBreaksRequiringRegulatoryReporting() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.setRequiresRegulatoryReporting(true);
            saved.setFinancialImpact(new BigDecimal("50000.00"));
            
            breakRepository.save(saved);

            ReconciliationBreak regulatory = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(regulatory.getRequiresRegulatoryReporting()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should track regulatory reporting completion")
        void shouldTrackRegulatoryReportingCompletion() {
            ReconciliationBreak saved = breakRepository.save(testBreak);

            saved.setRequiresRegulatoryReporting(true);
            saved.setRegulatoryReportedAt(LocalDateTime.now());
            saved.setRegulatoryReference("REG-REPORT-2025-001");
            
            breakRepository.save(saved);

            ReconciliationBreak reported = breakRepository.findById(saved.getId()).orElseThrow();
            assertThat(reported.getRegulatoryReportedAt()).isNotNull();
            assertThat(reported.getRegulatoryReference()).isEqualTo("REG-REPORT-2025-001");
        }
    }
}