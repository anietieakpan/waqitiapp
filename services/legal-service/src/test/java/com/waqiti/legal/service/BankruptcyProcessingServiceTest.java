package com.waqiti.legal.service;

import com.waqiti.legal.domain.BankruptcyCase;
import com.waqiti.legal.repository.BankruptcyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BankruptcyProcessingService
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 */
class BankruptcyProcessingServiceTest {

    @Mock
    private BankruptcyRepository bankruptcyRepository;

    @InjectMocks
    private BankruptcyProcessingService bankruptcyProcessingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should create bankruptcy record successfully")
    void shouldCreateBankruptcyRecord() {
        // Given
        String customerId = "CUST-123";
        String customerName = "John Doe";
        String caseNumber = "BK-2025-001";
        String chapter = "CHAPTER_7";
        LocalDate filingDate = LocalDate.now();
        String courtDistrict = "Northern District of California";
        Map<String, String> trusteeInfo = new HashMap<>();
        trusteeInfo.put("name", "Jane Smith");
        trusteeInfo.put("email", "jane.smith@trustee.com");
        BigDecimal totalDebtAmount = new BigDecimal("50000.00");

        BankruptcyCase expected = BankruptcyCase.builder()
                .bankruptcyId("BK-UUID-123")
                .customerId(customerId)
                .customerName(customerName)
                .caseNumber(caseNumber)
                .bankruptcyChapter(BankruptcyCase.BankruptcyChapter.CHAPTER_7)
                .filingDate(filingDate)
                .courtDistrict(courtDistrict)
                .totalDebtAmount(totalDebtAmount)
                .build();

        when(bankruptcyRepository.findByCaseNumber(caseNumber)).thenReturn(Optional.empty());
        when(bankruptcyRepository.save(any(BankruptcyCase.class))).thenReturn(expected);

        // When
        BankruptcyCase result = bankruptcyProcessingService.createBankruptcyRecord(
                customerId, customerName, caseNumber, chapter, filingDate,
                courtDistrict, trusteeInfo, totalDebtAmount, "TEST_USER"
        );

        // Then
        assertNotNull(result);
        assertEquals(customerId, result.getCustomerId());
        assertEquals(caseNumber, result.getCaseNumber());
        assertEquals(BankruptcyCase.BankruptcyChapter.CHAPTER_7, result.getBankruptcyChapter());
        verify(bankruptcyRepository, times(1)).save(any(BankruptcyCase.class));
    }

    @Test
    @DisplayName("Should calculate creditor claim amount")
    void shouldCalculateCreditorClaim() {
        // Given
        String bankruptcyId = "BK-123";
        String customerId = "CUST-123";

        BankruptcyCase bankruptcyCase = BankruptcyCase.builder()
                .bankruptcyId(bankruptcyId)
                .customerId(customerId)
                .build();

        when(bankruptcyRepository.findByBankruptcyId(bankruptcyId))
                .thenReturn(Optional.of(bankruptcyCase));

        // When
        BigDecimal claimAmount = bankruptcyProcessingService.calculateCreditorClaim(bankruptcyId, customerId);

        // Then
        assertNotNull(claimAmount);
        assertTrue(claimAmount.compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @DisplayName("Should file proof of claim successfully")
    void shouldFileProofOfClaim() {
        // Given
        String bankruptcyId = "BK-123";
        BigDecimal claimAmount = new BigDecimal("15000.00");
        String classification = "UNSECURED_NONPRIORITY";

        BankruptcyCase bankruptcyCase = BankruptcyCase.builder()
                .bankruptcyId(bankruptcyId)
                .caseNumber("BK-2025-001")
                .bankruptcyChapter(BankruptcyCase.BankruptcyChapter.CHAPTER_7)
                .proofOfClaimBarDate(LocalDate.now().plusDays(30))
                .build();

        when(bankruptcyRepository.findByBankruptcyId(bankruptcyId))
                .thenReturn(Optional.of(bankruptcyCase));
        when(bankruptcyRepository.save(any(BankruptcyCase.class)))
                .thenReturn(bankruptcyCase);

        // When
        Map<String, Object> result = bankruptcyProcessingService.fileProofOfClaim(
                bankruptcyId, claimAmount, classification, java.util.Collections.emptyList()
        );

        // Then
        assertNotNull(result);
        assertTrue(result.containsKey("claimNumber"));
        assertTrue(result.containsKey("claimAmount"));
        verify(bankruptcyRepository, times(1)).save(any(BankruptcyCase.class));
    }

    @Test
    @DisplayName("Should throw exception when proof of claim bar date has passed")
    void shouldThrowExceptionWhenBarDatePassed() {
        // Given
        String bankruptcyId = "BK-123";
        BigDecimal claimAmount = new BigDecimal("15000.00");
        String classification = "UNSECURED_NONPRIORITY";

        BankruptcyCase bankruptcyCase = BankruptcyCase.builder()
                .bankruptcyId(bankruptcyId)
                .caseNumber("BK-2025-001")
                .bankruptcyChapter(BankruptcyCase.BankruptcyChapter.CHAPTER_7)
                .proofOfClaimBarDate(LocalDate.now().minusDays(1)) // Past date
                .build();

        when(bankruptcyRepository.findByBankruptcyId(bankruptcyId))
                .thenReturn(Optional.of(bankruptcyCase));

        // When / Then
        assertThrows(IllegalStateException.class, () -> {
            bankruptcyProcessingService.fileProofOfClaim(
                    bankruptcyId, claimAmount, classification, java.util.Collections.emptyList()
            );
        });
    }

    @Test
    @DisplayName("Should prepare Chapter 13 repayment plan")
    void shouldPrepareChapter13RepaymentPlan() {
        // Given
        String bankruptcyId = "BK-123";
        int proposedDuration = 60; // months

        BankruptcyCase bankruptcyCase = BankruptcyCase.builder()
                .bankruptcyId(bankruptcyId)
                .bankruptcyChapter(BankruptcyCase.BankruptcyChapter.CHAPTER_13)
                .waqitiClaimAmount(new BigDecimal("30000.00"))
                .claimClassification("UNSECURED_NONPRIORITY")
                .build();

        when(bankruptcyRepository.findByBankruptcyId(bankruptcyId))
                .thenReturn(Optional.of(bankruptcyCase));
        when(bankruptcyRepository.save(any(BankruptcyCase.class)))
                .thenReturn(bankruptcyCase);

        // When
        Map<String, Object> result = bankruptcyProcessingService.prepareRepaymentPlan(
                bankruptcyId, proposedDuration
        );

        // Then
        assertNotNull(result);
        assertTrue(result.containsKey("monthlyPayment"));
        assertTrue(result.containsKey("totalClaim"));
        assertTrue(result.containsKey("planDuration"));
        assertEquals(proposedDuration, result.get("planDuration"));
    }

    @Test
    @DisplayName("Should identify exempt assets for Chapter 7")
    void shouldIdentifyExemptAssets() {
        // Given
        String bankruptcyId = "BK-123";
        String customerId = "CUST-123";

        BankruptcyCase bankruptcyCase = BankruptcyCase.builder()
                .bankruptcyId(bankruptcyId)
                .customerId(customerId)
                .bankruptcyChapter(BankruptcyCase.BankruptcyChapter.CHAPTER_7)
                .build();

        when(bankruptcyRepository.findByBankruptcyId(bankruptcyId))
                .thenReturn(Optional.of(bankruptcyCase));
        when(bankruptcyRepository.save(any(BankruptcyCase.class)))
                .thenReturn(bankruptcyCase);

        // When
        Map<String, Object> result = bankruptcyProcessingService.identifyExemptAssets(
                bankruptcyId, customerId
        );

        // Then
        assertNotNull(result);
        assertTrue(result.containsKey("exemptAssets"));
        assertTrue(result.containsKey("nonExemptAssets"));
    }
}
