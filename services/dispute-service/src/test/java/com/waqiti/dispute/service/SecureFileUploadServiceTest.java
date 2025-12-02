package com.waqiti.dispute.service;

import com.waqiti.dispute.dto.FileUploadResult;
import com.waqiti.dispute.exception.FileUploadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SecureFileUploadService
 *
 * Tests file upload security features including magic byte validation,
 * size limits, and encryption
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@DisplayName("SecureFileUploadService Tests")
class SecureFileUploadServiceTest {

    private SecureFileUploadService fileUploadService;

    @TempDir
    Path tempDir;

    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_DISPUTE_ID = "dispute-456";

    // PDF magic bytes: %PDF
    private static final byte[] PDF_MAGIC_BYTES = {0x25, 0x50, 0x44, 0x46};

    // JPEG magic bytes: FF D8 FF
    private static final byte[] JPEG_MAGIC_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    // PNG magic bytes
    private static final byte[] PNG_MAGIC_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    // Fake/malicious file with wrong extension
    private static final byte[] FAKE_PDF_BYTES = {0x00, 0x00, 0x00, 0x00}; // Not a real PDF

    @BeforeEach
    void setUp() {
        fileUploadService = new SecureFileUploadService();
        ReflectionTestUtils.setField(fileUploadService, "uploadDirectory", tempDir.toString());
        ReflectionTestUtils.setField(fileUploadService, "maxFileSizeBytes", 10485760L); // 10MB
        ReflectionTestUtils.setField(fileUploadService, "clamavEnabled", false);
        ReflectionTestUtils.setField(fileUploadService, "encryptionKeyBase64", "");
    }

    @Test
    @DisplayName("Should successfully upload valid PDF file")
    void testUploadFile_ValidPdf_Success() throws FileUploadException {
        // Given
        byte[] pdfContent = createPdfContent();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                pdfContent
        );

        // When
        FileUploadResult result = fileUploadService.uploadFile(file, TEST_USER_ID, TEST_DISPUTE_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFileId()).isNotNull();
        assertThat(result.getOriginalFilename()).isEqualTo("document.pdf");
        assertThat(result.getMimeType()).isEqualTo("application/pdf");
        assertThat(result.getFileSizeBytes()).isEqualTo(pdfContent.length);
        assertThat(result.getUploadedBy()).isEqualTo(TEST_USER_ID);
        assertThat(result.getDisputeId()).isEqualTo(TEST_DISPUTE_ID);
        assertThat(result.isEncrypted()).isTrue();
        assertThat(result.getFileHash()).isNotNull();
        assertThat(result.getStoragePath()).contains(TEST_USER_ID).contains(TEST_DISPUTE_ID);
    }

    @Test
    @DisplayName("Should successfully upload valid JPEG file")
    void testUploadFile_ValidJpeg_Success() throws FileUploadException {
        // Given
        byte[] jpegContent = createJpegContent();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                jpegContent
        );

        // When
        FileUploadResult result = fileUploadService.uploadFile(file, TEST_USER_ID, TEST_DISPUTE_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMimeType()).isEqualTo("image/jpeg");
        assertThat(result.getOriginalFilename()).isEqualTo("photo.jpg");
    }

    @Test
    @DisplayName("Should successfully upload valid PNG file")
    void testUploadFile_ValidPng_Success() throws FileUploadException {
        // Given
        byte[] pngContent = createPngContent();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                pngContent
        );

        // When
        FileUploadResult result = fileUploadService.uploadFile(file, TEST_USER_ID, TEST_DISPUTE_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMimeType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Should reject empty file")
    void testUploadFile_EmptyFile_ThrowsException() {
        // Given
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        // When & Then
        assertThatThrownBy(() -> fileUploadService.uploadFile(emptyFile, TEST_USER_ID, TEST_DISPUTE_ID))
                .isInstanceOf(FileUploadException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Should reject file exceeding size limit")
    void testUploadFile_FileTooLarge_ThrowsException() {
        // Given
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        System.arraycopy(PDF_MAGIC_BYTES, 0, largeContent, 0, PDF_MAGIC_BYTES.length);

        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                largeContent
        );

        // When & Then
        assertThatThrownBy(() -> fileUploadService.uploadFile(largeFile, TEST_USER_ID, TEST_DISPUTE_ID))
                .isInstanceOf(FileUploadException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    @DisplayName("Should reject file with wrong magic bytes (fake PDF)")
    void testUploadFile_FakePdf_ThrowsException() {
        // Given - File claims to be PDF but has wrong magic bytes
        MockMultipartFile fakeFile = new MockMultipartFile(
                "file",
                "malicious.pdf",
                "application/pdf",
                FAKE_PDF_BYTES
        );

        // When & Then
        assertThatThrownBy(() -> fileUploadService.uploadFile(fakeFile, TEST_USER_ID, TEST_DISPUTE_ID))
                .isInstanceOf(FileUploadException.class)
                .hasMessageContaining("File type not allowed");
    }

    @Test
    @DisplayName("Should reject executable file disguised as PDF")
    void testUploadFile_ExecutableDisguisedAsPdf_ThrowsException() {
        // Given - EXE file (MZ header) with .pdf extension
        byte[] exeContent = {0x4D, 0x5A, 0x90, 0x00}; // MZ header
        MockMultipartFile exeFile = new MockMultipartFile(
                "file",
                "virus.pdf",
                "application/pdf",
                exeContent
        );

        // When & Then
        assertThatThrownBy(() -> fileUploadService.uploadFile(exeFile, TEST_USER_ID, TEST_DISPUTE_ID))
                .isInstanceOf(FileUploadException.class)
                .hasMessageContaining("File type not allowed");
    }

    @Test
    @DisplayName("Should reject PHP file disguised as image")
    void testUploadFile_PhpDisguisedAsImage_ThrowsException() {
        // Given - PHP file with .jpg extension
        byte[] phpContent = "<?php system($_GET['cmd']); ?>".getBytes();
        MockMultipartFile phpFile = new MockMultipartFile(
                "file",
                "webshell.jpg",
                "image/jpeg",
                phpContent
        );

        // When & Then
        assertThatThrownBy(() -> fileUploadService.uploadFile(phpFile, TEST_USER_ID, TEST_DISPUTE_ID))
                .isInstanceOf(FileUploadException.class)
                .hasMessageContaining("File type not allowed");
    }

    @Test
    @DisplayName("Should sanitize filename with special characters")
    void testUploadFile_SpecialCharactersInFilename_Sanitized() throws FileUploadException {
        // Given
        byte[] pdfContent = createPdfContent();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../../etc/passwd.pdf",
                "application/pdf",
                pdfContent
        );

        // When
        FileUploadResult result = fileUploadService.uploadFile(file, TEST_USER_ID, TEST_DISPUTE_ID);

        // Then
        assertThat(result.getSecureFilename()).doesNotContain("..");
        assertThat(result.getSecureFilename()).doesNotContain("/");
        assertThat(result.getSecureFilename()).contains("_");
    }

    @Test
    @DisplayName("Should generate unique filenames for identical file uploads")
    void testUploadFile_SameFileUploadedTwice_UniqueFilenames() throws FileUploadException {
        // Given
        byte[] pdfContent = createPdfContent();
        MockMultipartFile file1 = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdfContent);
        MockMultipartFile file2 = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdfContent);

        // When
        FileUploadResult result1 = fileUploadService.uploadFile(file1, TEST_USER_ID, TEST_DISPUTE_ID);
        FileUploadResult result2 = fileUploadService.uploadFile(file2, TEST_USER_ID, TEST_DISPUTE_ID);

        // Then
        assertThat(result1.getSecureFilename()).isNotEqualTo(result2.getSecureFilename());
        assertThat(result1.getFileId()).isNotEqualTo(result2.getFileId());
    }

    @Test
    @DisplayName("Should create user and dispute-specific directory structure")
    void testUploadFile_DirectoryStructure_UserAndDisputeSpecific() throws FileUploadException {
        // Given
        byte[] pdfContent = createPdfContent();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdfContent);

        // When
        FileUploadResult result = fileUploadService.uploadFile(file, "user-999", "dispute-888");

        // Then
        assertThat(result.getStoragePath()).contains("user-999");
        assertThat(result.getStoragePath()).contains("dispute-888");
    }

    @Test
    @DisplayName("Should reject file with insufficient bytes for validation")
    void testUploadFile_FileTooSmallForValidation_ThrowsException() {
        // Given
        byte[] tinyContent = {0x01, 0x02}; // Only 2 bytes
        MockMultipartFile tinyFile = new MockMultipartFile("file", "tiny.pdf", "application/pdf", tinyContent);

        // When & Then
        assertThatThrownBy(() -> fileUploadService.uploadFile(tinyFile, TEST_USER_ID, TEST_DISPUTE_ID))
                .isInstanceOf(FileUploadException.class)
                .hasMessageContaining("too small");
    }

    // Helper methods to create valid file content with proper magic bytes

    private byte[] createPdfContent() {
        byte[] content = new byte[1024];
        System.arraycopy(PDF_MAGIC_BYTES, 0, content, 0, PDF_MAGIC_BYTES.length);
        // Fill rest with dummy data
        for (int i = PDF_MAGIC_BYTES.length; i < content.length; i++) {
            content[i] = (byte) i;
        }
        return content;
    }

    private byte[] createJpegContent() {
        byte[] content = new byte[1024];
        System.arraycopy(JPEG_MAGIC_BYTES, 0, content, 0, JPEG_MAGIC_BYTES.length);
        for (int i = JPEG_MAGIC_BYTES.length; i < content.length; i++) {
            content[i] = (byte) i;
        }
        return content;
    }

    private byte[] createPngContent() {
        byte[] content = new byte[1024];
        System.arraycopy(PNG_MAGIC_BYTES, 0, content, 0, PNG_MAGIC_BYTES.length);
        for (int i = PNG_MAGIC_BYTES.length; i < content.length; i++) {
            content[i] = (byte) i;
        }
        return content;
    }
}
