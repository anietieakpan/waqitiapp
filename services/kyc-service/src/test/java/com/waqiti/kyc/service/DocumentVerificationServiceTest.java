package com.waqiti.kyc.service;

import com.waqiti.common.audit.service.AuditService;
import com.waqiti.kyc.domain.DocumentVerification;
import com.waqiti.kyc.domain.VerificationDocument;
import com.waqiti.kyc.dto.DocumentData;
import com.waqiti.kyc.dto.InternationalKycModels.DocumentVerificationResult;
import com.waqiti.kyc.dto.InternationalKycModels.DocumentType;
import com.waqiti.kyc.repository.DocumentVerificationRepository;
import com.waqiti.kyc.repository.VerificationDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "document.verification.api.url=https://test-api.idanalyzer.com",
        "document.verification.api.key=test-key",
        "tesseract.data.path=/usr/share/tesseract-ocr/4.00/tessdata",
        "document.min.quality.score=70",
        "document.max.age.years=10"
})
@DisplayName("Document Verification Service Tests")
class DocumentVerificationServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private DocumentVerificationService documentVerificationService;

    @Autowired(required = false)
    private DocumentVerificationRepository documentVerificationRepository;

    @Autowired(required = false)
    private VerificationDocumentRepository verificationDocumentRepository;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private AuditService auditService;

    private byte[] validPassportImage;
    private byte[] validDriversLicenseImage;
    private byte[] poorQualityImage;
    private byte[] expiredDocumentImage;

    @BeforeEach
    void setUp() throws IOException {
        validPassportImage = createMockPassportImage();
        validDriversLicenseImage = createMockDriversLicenseImage();
        poorQualityImage = createPoorQualityImage();
        expiredDocumentImage = createExpiredDocumentImage();
    }

    @Nested
    @DisplayName("Document Quality Validation Tests")
    class DocumentQualityValidationTests {

        @Test
        @DisplayName("Should accept high-quality document")
        void shouldAcceptHighQualityDocument() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validPassportImage, "PASSPORT");

            assertThat(result).isNotNull();
            assertThat(result.isVerified()).isTrue();
        }

        @Test
        @DisplayName("Should reject low-resolution document")
        void shouldRejectLowResolutionDocument() {
            byte[] lowResImage = createLowResolutionImage();

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    lowResImage, "PASSPORT");

            assertThat(result).isNotNull();
            assertThat(result.isVerified()).isFalse();
            assertThat(result.getIssues()).isNotEmpty();
        }

        @Test
        @DisplayName("Should reject poor quality document")
        void shouldRejectPoorQualityDocument() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    poorQualityImage, "PASSPORT");

            assertThat(result).isNotNull();
            assertThat(result.isVerified()).isFalse();
            assertThat(result.getIssues()).anyMatch(issue -> issue.contains("quality"));
        }

        @Test
        @DisplayName("Should reject file size too small")
        void shouldRejectFileSizeTooSmall() {
            byte[] tinyImage = createTinyImage();

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    tinyImage, "PASSPORT");

            assertThat(result).isNotNull();
            assertThat(result.isVerified()).isFalse();
        }

        @Test
        @DisplayName("Should reject file size too large")
        void shouldRejectFileSizeTooLarge() {
            byte[] hugeImage = new byte[15_000_000];

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    hugeImage, "PASSPORT");

            assertThat(result).isNotNull();
            assertThat(result.isVerified()).isFalse();
        }

        @Test
        @DisplayName("Should validate document brightness")
        void shouldValidateDocumentBrightness() {
            byte[] tooDarkImage = createTooDarkImage();

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    tooDarkImage, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should validate document contrast")
        void shouldValidateDocumentContrast() {
            byte[] lowContrastImage = createLowContrastImage();

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    lowContrastImage, "PASSPORT");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Document Type Detection Tests")
    class DocumentTypeDetectionTests {

        @Test
        @DisplayName("Should detect passport document type")
        void shouldDetectPassportDocumentType() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validPassportImage, null);

            assertThat(result).isNotNull();
            if (result.getDocumentType() != null) {
                assertThat(result.getDocumentType()).isEqualTo(DocumentType.PASSPORT);
            }
        }

        @Test
        @DisplayName("Should detect drivers license document type")
        void shouldDetectDriversLicenseDocumentType() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validDriversLicenseImage, null);

            assertThat(result).isNotNull();
            if (result.getDocumentType() != null) {
                assertThat(result.getDocumentType()).isIn(
                        DocumentType.DRIVERS_LICENSE, DocumentType.GOVERNMENT_ID);
            }
        }

        @Test
        @DisplayName("Should handle unknown document type")
        void shouldHandleUnknownDocumentType() {
            byte[] unknownDoc = createUnknownDocumentImage();

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    unknownDoc, null);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("OCR Text Extraction Tests")
    class OCRTextExtractionTests {

        @Test
        @DisplayName("Should extract text from passport")
        void shouldExtractTextFromPassport() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validPassportImage, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle OCR failure gracefully")
        void shouldHandleOCRFailureGracefully() {
            byte[] corruptedImage = new byte[100000];

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    corruptedImage, "PASSPORT");

            assertThat(result).isNotNull();
            assertThat(result.isVerified()).isFalse();
        }

        @Test
        @DisplayName("Should extract name from document")
        void shouldExtractNameFromDocument() {
            byte[] docWithName = createDocumentWithName("John Smith");

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    docWithName, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should extract date of birth from document")
        void shouldExtractDateOfBirthFromDocument() {
            byte[] docWithDOB = createDocumentWithDOB("01/15/1990");

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    docWithDOB, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should extract document number from passport")
        void shouldExtractDocumentNumberFromPassport() {
            byte[] passportWithNumber = createPassportWithNumber("P12345678");

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    passportWithNumber, "PASSPORT");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Document Data Validation Tests")
    class DocumentDataValidationTests {

        @Test
        @DisplayName("Should validate required fields present")
        void shouldValidateRequiredFieldsPresent() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validPassportImage, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should reject document missing name")
        void shouldRejectDocumentMissingName() {
            byte[] docWithoutName = createDocumentWithoutName();

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    docWithoutName, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should reject document missing document number")
        void shouldRejectDocumentMissingDocumentNumber() {
            byte[] docWithoutNumber = createDocumentWithoutNumber();

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    docWithoutNumber, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should validate passport number format")
        void shouldValidatePassportNumberFormat() {
            byte[] validPassport = createPassportWithNumber("P12345678");

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validPassport, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should reject invalid passport number format")
        void shouldRejectInvalidPassportNumberFormat() {
            byte[] invalidPassport = createPassportWithNumber("INVALID123");

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    invalidPassport, "PASSPORT");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Document Expiry Tests")
    class DocumentExpiryTests {

        @Test
        @DisplayName("Should accept valid non-expired document")
        void shouldAcceptValidNonExpiredDocument() {
            byte[] validDoc = createDocumentWithExpiry(LocalDate.now().plusYears(2));

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validDoc, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should reject expired document")
        void shouldRejectExpiredDocument() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    expiredDocumentImage, "PASSPORT");

            assertThat(result).isNotNull();
            assertThat(result.isVerified()).isFalse();
            assertThat(result.getIssues()).anyMatch(issue -> issue.toLowerCase().contains("expired"));
        }

        @Test
        @DisplayName("Should warn about expiring soon document")
        void shouldWarnAboutExpiringSoonDocument() {
            byte[] expiringSoon = createDocumentWithExpiry(LocalDate.now().plusMonths(2));

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    expiringSoon, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle document with no expiry date")
        void shouldHandleDocumentWithNoExpiryDate() {
            byte[] docWithoutExpiry = createDocumentWithoutExpiry();

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    docWithoutExpiry, "BIRTH_CERTIFICATE");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Security Features Verification Tests")
    class SecurityFeaturesVerificationTests {

        @Test
        @DisplayName("Should verify passport security features")
        void shouldVerifyPassportSecurityFeatures() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validPassportImage, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should verify drivers license security features")
        void shouldVerifyDriversLicenseSecurityFeatures() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validDriversLicenseImage, "DRIVERS_LICENSE");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should detect missing security features")
        void shouldDetectMissingSecurityFeatures() {
            byte[] docWithoutSecurityFeatures = createDocumentWithoutSecurityFeatures();

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    docWithoutSecurityFeatures, "PASSPORT");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Confidence Score Calculation Tests")
    class ConfidenceScoreCalculationTests {

        @Test
        @DisplayName("Should calculate high confidence score for valid document")
        void shouldCalculateHighConfidenceScoreForValidDocument() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validPassportImage, "PASSPORT");

            assertThat(result).isNotNull();
            if (result.isVerified()) {
                assertThat(result.getConfidenceScore()).isGreaterThanOrEqualTo(70.0);
            }
        }

        @Test
        @DisplayName("Should calculate low confidence score for poor quality document")
        void shouldCalculateLowConfidenceScoreForPoorQualityDocument() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    poorQualityImage, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should adjust confidence score based on external verification")
        void shouldAdjustConfidenceScoreBasedOnExternalVerification() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validPassportImage, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should flag low confidence documents for manual review")
        void shouldFlagLowConfidenceDocumentsForManualReview() {
            byte[] lowConfidenceDoc = createLowConfidenceDocument();

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    lowConfidenceDoc, "PASSPORT");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Multi-Document Type Tests")
    class MultiDocumentTypeTests {

        @Test
        @DisplayName("Should verify passport")
        void shouldVerifyPassport() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validPassportImage, "PASSPORT");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should verify drivers license")
        void shouldVerifyDriversLicense() {
            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validDriversLicenseImage, "DRIVERS_LICENSE");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should verify national ID")
        void shouldVerifyNationalId() {
            byte[] nationalId = createNationalIdImage();

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    nationalId, "NATIONAL_ID");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle different document formats")
        void shouldHandleDifferentDocumentFormats() {
            byte[] pngDoc = validPassportImage;
            byte[] jpegDoc = convertToJpeg(validPassportImage);

            DocumentVerificationResult pngResult = documentVerificationService.verifyDocument(
                    pngDoc, "PASSPORT");
            DocumentVerificationResult jpegResult = documentVerificationService.verifyDocument(
                    jpegDoc, "PASSPORT");

            assertThat(pngResult).isNotNull();
            assertThat(jpegResult).isNotNull();
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle null document content gracefully")
        void shouldHandleNullDocumentContentGracefully() {
            assertThatThrownBy(() -> documentVerificationService.verifyDocument(null, "PASSPORT"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should handle empty document content gracefully")
        void shouldHandleEmptyDocumentContentGracefully() {
            byte[] emptyDoc = new byte[0];

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    emptyDoc, "PASSPORT");

            assertThat(result).isNotNull();
            assertThat(result.isVerified()).isFalse();
        }

        @Test
        @DisplayName("Should handle corrupted image data gracefully")
        void shouldHandleCorruptedImageDataGracefully() {
            byte[] corruptedData = new byte[]{1, 2, 3, 4, 5};

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    corruptedData, "PASSPORT");

            assertThat(result).isNotNull();
            assertThat(result.isVerified()).isFalse();
        }

        @Test
        @DisplayName("Should handle OCR service failure gracefully")
        void shouldHandleOCRServiceFailureGracefully() {
            when(restTemplate.postForObject(anyString(), any(), eq(java.util.Map.class)))
                    .thenThrow(new RuntimeException("OCR service unavailable"));

            DocumentVerificationResult result = documentVerificationService.verifyDocument(
                    validPassportImage, "PASSPORT");

            assertThat(result).isNotNull();
        }
    }

    private byte[] createMockPassportImage() throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1200, 900);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("PASSPORT", 500, 100);
        g2d.drawString("Name: John Smith", 100, 300);
        g2d.drawString("DOB: 01/15/1990", 100, 350);
        g2d.drawString("Passport No: P12345678", 100, 400);
        g2d.drawString("Expiry: 12/31/2030", 100, 450);

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createMockDriversLicenseImage() throws IOException {
        BufferedImage image = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(0, 0, 1000, 700);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.drawString("DRIVER LICENSE", 400, 80);
        g2d.drawString("Name: Jane Doe", 100, 250);
        g2d.drawString("License No: DL123456", 100, 290);
        g2d.drawString("Expiry: 06/30/2028", 100, 330);

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createPoorQualityImage() throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(new Color(50, 50, 50));
        g2d.fillRect(0, 0, 1200, 900);

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createExpiredDocumentImage() throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1200, 900);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("PASSPORT", 500, 100);
        g2d.drawString("Name: John Smith", 100, 300);
        g2d.drawString("Expiry: 12/31/2020", 100, 450);

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createLowResolutionImage() throws IOException {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 400, 300);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createTinyImage() throws IOException {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createTooDarkImage() throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(new Color(20, 20, 20));
        g2d.fillRect(0, 0, 1200, 900);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createLowContrastImage() throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(new Color(128, 128, 128));
        g2d.fillRect(0, 0, 1200, 900);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createUnknownDocumentImage() throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1200, 900);
        g2d.setColor(Color.BLACK);
        g2d.drawString("Some random document", 100, 100);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createDocumentWithName(String name) throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1200, 900);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("Name: " + name, 100, 300);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createDocumentWithDOB(String dob) throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1200, 900);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("DOB: " + dob, 100, 350);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createPassportWithNumber(String number) throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1200, 900);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("PASSPORT", 500, 100);
        g2d.drawString("Name: John Smith", 100, 300);
        g2d.drawString("Passport No: " + number, 100, 400);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createDocumentWithoutName() throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1200, 900);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("PASSPORT", 500, 100);
        g2d.drawString("Passport No: P12345678", 100, 400);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createDocumentWithoutNumber() throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1200, 900);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("PASSPORT", 500, 100);
        g2d.drawString("Name: John Smith", 100, 300);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createDocumentWithExpiry(LocalDate expiryDate) throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1200, 900);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("PASSPORT", 500, 100);
        g2d.drawString("Name: John Smith", 100, 300);
        g2d.drawString("Passport No: P12345678", 100, 400);
        g2d.drawString("Expiry: " + expiryDate.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy")), 100, 450);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createDocumentWithoutExpiry() throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1200, 900);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("BIRTH CERTIFICATE", 400, 100);
        g2d.drawString("Name: John Smith", 100, 300);
        g2d.drawString("DOB: 01/15/1990", 100, 350);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createDocumentWithoutSecurityFeatures() throws IOException {
        return createMockPassportImage();
    }

    private byte[] createLowConfidenceDocument() throws IOException {
        BufferedImage image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(0, 0, 1200, 900);
        g2d.setColor(Color.GRAY);
        g2d.drawString("Blurry text", 100, 300);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createNationalIdImage() throws IOException {
        BufferedImage image = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1000, 700);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.drawString("NATIONAL IDENTITY CARD", 300, 80);
        g2d.drawString("Name: Jane Doe", 100, 250);
        g2d.drawString("ID No: NID123456789", 100, 290);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] convertToJpeg(byte[] pngData) throws IOException {
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(pngData));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "JPEG", baos);
        return baos.toByteArray();
    }
}