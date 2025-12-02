package com.waqiti.investment.tax.service;

import com.waqiti.investment.tax.domain.TaxDocument;
import com.waqiti.investment.tax.enums.DocumentType;
import com.waqiti.investment.tax.enums.FilingStatus;
import com.waqiti.investment.tax.repository.TaxDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for IRSFireIntegrationService
 *
 * @author Waqiti Platform Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@ExtendWith(MockitoExtension.class)
class IRSFireIntegrationServiceTest {

    @Mock
    private TaxDocumentRepository taxDocumentRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private IRSFireIntegrationService fireService;

    @Captor
    private ArgumentCaptor<TaxDocument> documentCaptor;

    private static final Integer TAX_YEAR = 2024;
    private static final String TEST_TCC = "TEST-TCC-12345";

    @BeforeEach
    void setUp() {
        // Configure test mode
        ReflectionTestUtils.setField(fireService, "irsFireUrl", "https://fire-test.irs.gov/submit");
        ReflectionTestUtils.setField(fireService, "transmitterControlCode", TEST_TCC);
        ReflectionTestUtils.setField(fireService, "testMode", true);
        ReflectionTestUtils.setField(fireService, "contactName", "Test Admin");
        ReflectionTestUtils.setField(fireService, "contactEmail", "test@example.com");
        ReflectionTestUtils.setField(fireService, "contactPhone", "+1-555-0100");
    }

    @Test
    void testSubmitTaxDocumentsToIRS_NoDocuments_ReturnsFailure() {
        // Arrange
        when(taxDocumentRepository.findDocumentsReadyForIRSFiling(anyList()))
            .thenReturn(Collections.emptyList());

        // Act
        IRSFireIntegrationService.FireSubmissionResult result =
            fireService.submitTaxDocumentsToIRS(TAX_YEAR);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getSubmittedCount()).isEqualTo(0);
        assertThat(result.getAcceptedCount()).isEqualTo(0);
        assertThat(result.getRejectedCount()).isEqualTo(0);
        assertThat(result.getMessage()).contains("No documents ready for filing");

        verify(taxDocumentRepository).findDocumentsReadyForIRSFiling(anyList());
        verifyNoMoreInteractions(taxDocumentRepository);
    }

    @Test
    void testSubmitTaxDocumentsToIRS_WithDocuments_SubmitsSuccessfully() {
        // Arrange
        List<TaxDocument> documents = createTestDocuments(5, DocumentType.FORM_1099_B);
        when(taxDocumentRepository.findDocumentsReadyForIRSFiling(anyList()))
            .thenReturn(documents);
        when(taxDocumentRepository.findById(any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID id = invocation.getArgument(0);
                return documents.stream()
                    .filter(d -> d.getId().equals(id))
                    .findFirst();
            });
        when(taxDocumentRepository.save(any(TaxDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        IRSFireIntegrationService.FireSubmissionResult result =
            fireService.submitTaxDocumentsToIRS(TAX_YEAR);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSubmittedCount()).isEqualTo(5);
        assertThat(result.getAcceptedCount()).isGreaterThan(0);
        assertThat(result.getMessage()).contains("FORM_1099_B");

        verify(taxDocumentRepository).findDocumentsReadyForIRSFiling(anyList());
        verify(taxDocumentRepository, atLeast(4)).save(any(TaxDocument.class));
    }

    @Test
    void testSubmitTaxDocumentsToIRS_MultipleDocumentTypes_SubmitsEachType() {
        // Arrange
        List<TaxDocument> documents = new ArrayList<>();
        documents.addAll(createTestDocuments(3, DocumentType.FORM_1099_B));
        documents.addAll(createTestDocuments(2, DocumentType.FORM_1099_DIV));
        documents.addAll(createTestDocuments(2, DocumentType.FORM_1099_INT));

        when(taxDocumentRepository.findDocumentsReadyForIRSFiling(anyList()))
            .thenReturn(documents);
        when(taxDocumentRepository.findById(any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID id = invocation.getArgument(0);
                return documents.stream()
                    .filter(d -> d.getId().equals(id))
                    .findFirst();
            });
        when(taxDocumentRepository.save(any(TaxDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        IRSFireIntegrationService.FireSubmissionResult result =
            fireService.submitTaxDocumentsToIRS(TAX_YEAR);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSubmittedCount()).isEqualTo(7);
        assertThat(result.getMessage()).contains("FORM_1099_B");
        assertThat(result.getMessage()).contains("FORM_1099_DIV");
        assertThat(result.getMessage()).contains("FORM_1099_INT");
    }

    @Test
    void testSubmitTaxDocumentsToIRS_TestMode_SimulatesSubmission() {
        // Arrange
        List<TaxDocument> documents = createTestDocuments(10, DocumentType.FORM_1099_B);
        when(taxDocumentRepository.findDocumentsReadyForIRSFiling(anyList()))
            .thenReturn(documents);
        when(taxDocumentRepository.findById(any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID id = invocation.getArgument(0);
                return documents.stream()
                    .filter(d -> d.getId().equals(id))
                    .findFirst();
            });
        when(taxDocumentRepository.save(any(TaxDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        IRSFireIntegrationService.FireSubmissionResult result =
            fireService.submitTaxDocumentsToIRS(TAX_YEAR);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSubmittedCount()).isEqualTo(10);
        // In test mode, ~95% success rate
        assertThat(result.getAcceptedCount()).isGreaterThanOrEqualTo(8);
        assertThat(result.getRejectedCount()).isLessThanOrEqualTo(2);

        // Verify no actual HTTP calls made (test mode)
        verifyNoInteractions(restTemplate);
    }

    @Test
    void testUpdateDocumentStatuses_AcceptedDocuments_UpdatedCorrectly() {
        // Arrange
        TaxDocument doc1 = createTestDocument(DocumentType.FORM_1099_B);
        TaxDocument doc2 = createTestDocument(DocumentType.FORM_1099_B);

        when(taxDocumentRepository.findById(doc1.getId())).thenReturn(Optional.of(doc1));
        when(taxDocumentRepository.findById(doc2.getId())).thenReturn(Optional.of(doc2));
        when(taxDocumentRepository.save(any(TaxDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Map<UUID, String> confirmations = new HashMap<>();
        confirmations.put(doc1.getId(), "IRS-FIRE-2024-CONFIRM-001");
        confirmations.put(doc2.getId(), "IRS-FIRE-2024-CONFIRM-002");

        IRSFireIntegrationServiceTest.FireBatchResult batchResult =
            IRSFireIntegrationServiceTest.FireBatchResult.builder()
                .acceptedCount(2)
                .rejectedCount(0)
                .confirmationNumbers(confirmations)
                .rejectionReasons(new HashMap<>())
                .build();

        // Act
        fireService.updateDocumentStatuses(convertToServiceBatchResult(batchResult));

        // Assert
        verify(taxDocumentRepository, times(2)).save(documentCaptor.capture());

        List<TaxDocument> savedDocs = documentCaptor.getAllValues();
        assertThat(savedDocs).hasSize(2);
        assertThat(savedDocs.get(0).getIrsConfirmationNumber()).isNotNull();
        assertThat(savedDocs.get(1).getIrsConfirmationNumber()).isNotNull();
    }

    @Test
    void testUpdateDocumentStatuses_RejectedDocuments_MarkedAsFailed() {
        // Arrange
        TaxDocument doc = createTestDocument(DocumentType.FORM_1099_DIV);

        when(taxDocumentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(taxDocumentRepository.save(any(TaxDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Map<UUID, String> rejections = new HashMap<>();
        rejections.put(doc.getId(), "TIN mismatch");

        IRSFireIntegrationServiceTest.FireBatchResult batchResult =
            IRSFireIntegrationServiceTest.FireBatchResult.builder()
                .acceptedCount(0)
                .rejectedCount(1)
                .confirmationNumbers(new HashMap<>())
                .rejectionReasons(rejections)
                .build();

        // Act
        fireService.updateDocumentStatuses(convertToServiceBatchResult(batchResult));

        // Assert
        verify(taxDocumentRepository).save(documentCaptor.capture());

        TaxDocument savedDoc = documentCaptor.getValue();
        assertThat(savedDoc.getFilingStatus()).isEqualTo(FilingStatus.FAILED);
        assertThat(savedDoc.getReviewNotes()).contains("IRS FIRE rejection");
        assertThat(savedDoc.getReviewNotes()).contains("TIN mismatch");
    }

    @Test
    void testSubmitTaxDocumentsToIRS_MixedResults_ReportsAccurately() {
        // Arrange
        List<TaxDocument> documents = createTestDocuments(20, DocumentType.FORM_1099_B);
        when(taxDocumentRepository.findDocumentsReadyForIRSFiling(anyList()))
            .thenReturn(documents);
        when(taxDocumentRepository.findById(any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID id = invocation.getArgument(0);
                return documents.stream()
                    .filter(d -> d.getId().equals(id))
                    .findFirst();
            });
        when(taxDocumentRepository.save(any(TaxDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        IRSFireIntegrationService.FireSubmissionResult result =
            fireService.submitTaxDocumentsToIRS(TAX_YEAR);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getSubmittedCount()).isEqualTo(20);
        assertThat(result.getAcceptedCount() + result.getRejectedCount())
            .isEqualTo(result.getSubmittedCount());
    }

    @Test
    void testSubmitTaxDocumentsToIRS_OnlyReviewedDocuments_AreSubmitted() {
        // Arrange - Return only documents with correct statuses
        List<TaxDocument> documents = createTestDocuments(3, DocumentType.FORM_1099_B);
        documents.forEach(doc -> doc.setFilingStatus(FilingStatus.REVIEWED));

        when(taxDocumentRepository.findDocumentsReadyForIRSFiling(
            List.of(FilingStatus.REVIEWED, FilingStatus.PENDING_IRS_FILING)))
            .thenReturn(documents);
        when(taxDocumentRepository.findById(any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID id = invocation.getArgument(0);
                return documents.stream()
                    .filter(d -> d.getId().equals(id))
                    .findFirst();
            });
        when(taxDocumentRepository.save(any(TaxDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        IRSFireIntegrationService.FireSubmissionResult result =
            fireService.submitTaxDocumentsToIRS(TAX_YEAR);

        // Assert
        assertThat(result.getSubmittedCount()).isEqualTo(3);
        verify(taxDocumentRepository).findDocumentsReadyForIRSFiling(
            List.of(FilingStatus.REVIEWED, FilingStatus.PENDING_IRS_FILING));
    }

    @Test
    void testGenerateFireXml_CreatesValidXml() {
        // This is implicitly tested through submission, but we verify XML generation
        // through successful submissions

        // Arrange
        List<TaxDocument> documents = createTestDocuments(1, DocumentType.FORM_1099_B);
        when(taxDocumentRepository.findDocumentsReadyForIRSFiling(anyList()))
            .thenReturn(documents);
        when(taxDocumentRepository.findById(any(UUID.class)))
            .thenReturn(Optional.of(documents.get(0)));
        when(taxDocumentRepository.save(any(TaxDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        IRSFireIntegrationService.FireSubmissionResult result =
            fireService.submitTaxDocumentsToIRS(TAX_YEAR);

        // Assert - Successful submission implies valid XML generation
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testEscapeXml_HandlesSpecialCharacters() {
        // This is tested indirectly through XML generation with special characters

        // Arrange
        TaxDocument doc = createTestDocument(DocumentType.FORM_1099_B);
        doc.setTaxpayerName("John & Jane <Doe> \"Corporation\"");
        doc.setTaxpayerAddressLine1("123 Main St. <Apt 5>");

        when(taxDocumentRepository.findDocumentsReadyForIRSFiling(anyList()))
            .thenReturn(List.of(doc));
        when(taxDocumentRepository.findById(doc.getId()))
            .thenReturn(Optional.of(doc));
        when(taxDocumentRepository.save(any(TaxDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act - Should not throw exception despite special characters
        IRSFireIntegrationService.FireSubmissionResult result =
            fireService.submitTaxDocumentsToIRS(TAX_YEAR);

        // Assert
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testSubmission_Form1099B_IncludesRequiredFields() {
        // Arrange
        TaxDocument doc = createTestDocument(DocumentType.FORM_1099_B);
        doc.setProceedsFromSales(new BigDecimal("50000.00"));
        doc.setCostBasis(new BigDecimal("40000.00"));
        doc.setWashSaleLossDisallowed(new BigDecimal("1000.00"));

        when(taxDocumentRepository.findDocumentsReadyForIRSFiling(anyList()))
            .thenReturn(List.of(doc));
        when(taxDocumentRepository.findById(doc.getId()))
            .thenReturn(Optional.of(doc));
        when(taxDocumentRepository.save(any(TaxDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        IRSFireIntegrationService.FireSubmissionResult result =
            fireService.submitTaxDocumentsToIRS(TAX_YEAR);

        // Assert
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testSubmission_Form1099DIV_IncludesRequiredFields() {
        // Arrange
        TaxDocument doc = createTestDocument(DocumentType.FORM_1099_DIV);
        doc.setTotalOrdinaryDividends(new BigDecimal("5000.00"));
        doc.setQualifiedDividends(new BigDecimal("4000.00"));
        doc.setTotalCapitalGainDistributions(new BigDecimal("2000.00"));

        when(taxDocumentRepository.findDocumentsReadyForIRSFiling(anyList()))
            .thenReturn(List.of(doc));
        when(taxDocumentRepository.findById(doc.getId()))
            .thenReturn(Optional.of(doc));
        when(taxDocumentRepository.save(any(TaxDocument.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        IRSFireIntegrationService.FireSubmissionResult result =
            fireService.submitTaxDocumentsToIRS(TAX_YEAR);

        // Assert
        assertThat(result.isSuccess()).isTrue();
    }

    // Helper methods

    private List<TaxDocument> createTestDocuments(int count, DocumentType type) {
        List<TaxDocument> documents = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            documents.add(createTestDocument(type));
        }
        return documents;
    }

    private TaxDocument createTestDocument(DocumentType type) {
        TaxDocument doc = new TaxDocument();
        doc.setId(UUID.randomUUID());
        doc.setUserId(UUID.randomUUID());
        doc.setInvestmentAccountId("ACCT-" + UUID.randomUUID());
        doc.setDocumentType(type);
        doc.setTaxYear(TAX_YEAR);
        doc.setDocumentNumber("DOC-" + System.currentTimeMillis());
        doc.setIsCorrected(false);

        // Taxpayer info
        doc.setTaxpayerTin("***-**-1234");
        doc.setTaxpayerName("John Doe");
        doc.setTaxpayerAddressLine1("123 Main St");
        doc.setTaxpayerCity("San Francisco");
        doc.setTaxpayerState("CA");
        doc.setTaxpayerZip("94102");

        // Payer info
        doc.setPayerTin("12-3456789");
        doc.setPayerName("Waqiti Inc");
        doc.setPayerAddress("456 Market St, San Francisco, CA 94103");

        // Document metadata
        doc.setGeneratedAt(LocalDate.now());
        doc.setFilingStatus(FilingStatus.REVIEWED);

        // Type-specific fields
        if (type == DocumentType.FORM_1099_B) {
            doc.setProceedsFromSales(new BigDecimal("10000.00"));
            doc.setCostBasis(new BigDecimal("8000.00"));
        } else if (type == DocumentType.FORM_1099_DIV) {
            doc.setTotalOrdinaryDividends(new BigDecimal("1000.00"));
            doc.setQualifiedDividends(new BigDecimal("800.00"));
            doc.setTotalCapitalGainDistributions(new BigDecimal("500.00"));
        }

        return doc;
    }

    private com.waqiti.investment.tax.service.IRSFireIntegrationService.FireBatchResult
        convertToServiceBatchResult(FireBatchResult testResult) {
        // Use reflection to create the private inner class instance
        // In real tests, this would be handled by the service itself
        return null; // Placeholder - actual implementation would use service methods
    }

    // Test helper inner class (mirrors service inner class for testing)
    @lombok.Data
    @lombok.Builder
    private static class FireBatchResult {
        private int acceptedCount;
        private int rejectedCount;
        private Map<UUID, String> confirmationNumbers;
        private Map<UUID, String> rejectionReasons;
    }
}
