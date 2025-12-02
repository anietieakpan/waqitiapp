package com.waqiti.compliance.scheduler;

import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.compliance.model.SanctionsScreeningResult;
import com.waqiti.compliance.repository.TransactionRepository;
import com.waqiti.compliance.repository.UserRepository;
import com.waqiti.compliance.service.SanctionsScreeningService;
import com.waqiti.compliance.service.impl.OFACSanctionsScreeningServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for Automated Sanctions Screening Scheduler
 * Tests daily list updates, weekly rescreening, and emergency triggers
 */
@ExtendWith(MockitoExtension.class)
class AutomatedSanctionsScreeningSchedulerTest {

    @Mock
    private OFACSanctionsScreeningServiceImpl ofacScreeningService;

    @Mock
    private SanctionsScreeningService sanctionsScreeningService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ComprehensiveAuditService auditService;

    @InjectMocks
    private AutomatedSanctionsScreeningScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "screeningEnabled", true);
        ReflectionTestUtils.setField(scheduler, "batchSize", 1000);
        ReflectionTestUtils.setField(scheduler, "parallelThreads", 10);
    }

    @Test
    void testDailySanctionsListUpdate_Success() {
        // Given
        when(ofacScreeningService.updateSanctionsLists()).thenReturn(15420);

        // When
        scheduler.dailySanctionsListUpdate();

        // Then
        verify(ofacScreeningService, times(1)).updateSanctionsLists();
        verify(auditService, times(1)).auditSystemOperation(
            eq("SANCTIONS_DAILY_UPDATE_COMPLETED"),
            eq("SCHEDULER"),
            contains("15420 entries updated"),
            any()
        );
    }

    @Test
    void testDailySanctionsListUpdate_WhenDisabled() {
        // Given
        ReflectionTestUtils.setField(scheduler, "screeningEnabled", false);

        // When
        scheduler.dailySanctionsListUpdate();

        // Then
        verify(ofacScreeningService, never()).updateSanctionsLists();
    }

    @Test
    void testDailySanctionsListUpdate_Failure() {
        // Given
        when(ofacScreeningService.updateSanctionsLists())
            .thenThrow(new RuntimeException("API connection failed"));

        // When/Then
        assertThrows(RuntimeException.class, () -> scheduler.dailySanctionsListUpdate());
        
        verify(auditService, times(1)).auditCriticalComplianceEvent(
            eq("SANCTIONS_DAILY_UPDATE_FAILED"),
            eq("SCHEDULER"),
            contains("Daily sanctions list update failed"),
            any()
        );
    }

    @Test
    void testWeeklyCustomerRescreening_Success() {
        // Given
        List<UUID> activeUserIds = Arrays.asList(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        when(userRepository.findAllActiveUserIds()).thenReturn(activeUserIds);
        when(userRepository.findById(any())).thenReturn(java.util.Optional.of(createMockUser()));
        
        SanctionsScreeningResult cleanResult = SanctionsScreeningResult.builder()
            .hasMatch(false)
            .build();
        when(ofacScreeningService.screenUser(any(), any(), any(), any(), any()))
            .thenReturn(cleanResult);

        // When
        scheduler.weeklyCustomerRescreening();

        // Then
        verify(userRepository, times(1)).findAllActiveUserIds();
        verify(auditService, times(1)).auditSystemOperation(
            eq("SANCTIONS_WEEKLY_RESCREENING_COMPLETED"),
            eq("SCHEDULER"),
            contains("users screened"),
            any()
        );
    }

    @Test
    void testWeeklyCustomerRescreening_WithMatches() {
        // Given
        List<UUID> activeUserIds = Arrays.asList(UUID.randomUUID());
        when(userRepository.findAllActiveUserIds()).thenReturn(activeUserIds);
        when(userRepository.findById(any())).thenReturn(java.util.Optional.of(createMockUser()));
        
        SanctionsScreeningResult matchResult = SanctionsScreeningResult.builder()
            .hasMatch(true)
            .matchScore(0.95)
            .build();
        when(ofacScreeningService.screenUser(any(), any(), any(), any(), any()))
            .thenReturn(matchResult);

        // When
        scheduler.weeklyCustomerRescreening();

        // Then
        verify(ofacScreeningService, times(activeUserIds.size()))
            .screenUser(any(), any(), any(), any(), any());
    }

    @Test
    void testMonthlyHighValueTransactionScreening_Success() {
        // Given
        LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
        BigDecimal highValueThreshold = new BigDecimal("10000");
        
        List<UUID> highValueTxIds = Arrays.asList(
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        when(transactionRepository.findHighValueTransactionIds(any(), eq(highValueThreshold)))
            .thenReturn(highValueTxIds);
        when(transactionRepository.findById(any()))
            .thenReturn(java.util.Optional.of(createMockTransaction()));

        // When
        scheduler.monthlyHighValueTransactionScreening();

        // Then
        verify(transactionRepository, times(1))
            .findHighValueTransactionIds(any(), any());
        verify(auditService, times(1)).auditSystemOperation(
            eq("SANCTIONS_MONTHLY_TRANSACTION_SCREENING_COMPLETED"),
            eq("SCHEDULER"),
            contains("transactions screened"),
            any()
        );
    }

    @Test
    void testHourlyNewUserScreening_Success() {
        // Given
        List<UUID> newUserIds = Arrays.asList(UUID.randomUUID());
        when(userRepository.findNewUsersSince(any())).thenReturn(newUserIds);
        when(userRepository.findById(any())).thenReturn(java.util.Optional.of(createMockUser()));
        
        SanctionsScreeningResult cleanResult = SanctionsScreeningResult.builder()
            .hasMatch(false)
            .build();
        when(ofacScreeningService.screenUser(any(), any(), any(), any(), any()))
            .thenReturn(cleanResult);

        // When
        scheduler.hourlyNewUserScreening();

        // Then
        verify(userRepository, times(1)).findNewUsersSince(any());
        verify(ofacScreeningService, times(newUserIds.size()))
            .screenUser(any(), any(), any(), any(), any());
    }

    @Test
    void testHourlyNewUserScreening_NoNewUsers() {
        // Given
        when(userRepository.findNewUsersSince(any())).thenReturn(Arrays.asList());

        // When
        scheduler.hourlyNewUserScreening();

        // Then
        verify(userRepository, times(1)).findNewUsersSince(any());
        verify(ofacScreeningService, never()).screenUser(any(), any(), any(), any(), any());
    }

    @Test
    void testTriggerEmergencyScreening() {
        // Given
        String reason = "New sanctions imposed on Russia";
        List<UUID> activeUserIds = Arrays.asList(UUID.randomUUID());
        when(userRepository.findAllActiveUserIds()).thenReturn(activeUserIds);
        when(userRepository.findById(any())).thenReturn(java.util.Optional.of(createMockUser()));
        
        SanctionsScreeningResult cleanResult = SanctionsScreeningResult.builder()
            .hasMatch(false)
            .build();
        when(ofacScreeningService.screenUser(any(), any(), any(), any(), any()))
            .thenReturn(cleanResult);

        // When
        scheduler.triggerEmergencyScreening(reason);

        // Then
        verify(auditService, times(1)).auditCriticalComplianceEvent(
            eq("SANCTIONS_EMERGENCY_SCREENING_TRIGGERED"),
            eq("SCHEDULER"),
            contains(reason),
            any()
        );
        verify(userRepository, times(1)).findAllActiveUserIds();
    }

    @Test
    void testScreenUser_UserNotFound() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.empty());

        // When/Then
        // The scheduler should handle the exception gracefully
        List<UUID> userIds = Arrays.asList(userId);
        when(userRepository.findAllActiveUserIds()).thenReturn(userIds);
        
        // Should not throw exception but log error
        assertDoesNotThrow(() -> scheduler.weeklyCustomerRescreening());
    }

    @Test
    void testBatchProcessing() {
        // Given - Create more users than batch size
        ReflectionTestUtils.setField(scheduler, "batchSize", 2);
        
        List<UUID> userIds = Arrays.asList(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        when(userRepository.findAllActiveUserIds()).thenReturn(userIds);
        when(userRepository.findById(any())).thenReturn(java.util.Optional.of(createMockUser()));
        
        SanctionsScreeningResult cleanResult = SanctionsScreeningResult.builder()
            .hasMatch(false)
            .build();
        when(ofacScreeningService.screenUser(any(), any(), any(), any(), any()))
            .thenReturn(cleanResult);

        // When
        scheduler.weeklyCustomerRescreening();

        // Then - All users should be screened despite batching
        verify(ofacScreeningService, times(userIds.size()))
            .screenUser(any(), any(), any(), any(), any());
    }

    // Helper methods

    private com.waqiti.compliance.entity.User createMockUser() {
        return com.waqiti.compliance.entity.User.builder()
            .id(UUID.randomUUID())
            .fullName("John Smith")
            .dateOfBirth("1980-01-01")
            .country("US")
            .nationalId("123456789")
            .build();
    }

    private com.waqiti.compliance.entity.Transaction createMockTransaction() {
        return com.waqiti.compliance.entity.Transaction.builder()
            .id(UUID.randomUUID())
            .senderId(UUID.randomUUID())
            .recipientId(UUID.randomUUID())
            .amount(new BigDecimal("15000"))
            .currency("USD")
            .senderCountry("US")
            .recipientCountry("GB")
            .build();
    }
}