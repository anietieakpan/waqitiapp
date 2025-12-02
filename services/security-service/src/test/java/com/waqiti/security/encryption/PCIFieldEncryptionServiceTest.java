package com.waqiti.security.encryption;

import com.waqiti.security.audit.AuditService;
import com.waqiti.security.exception.PCIEncryptionException;
import com.waqiti.security.keymanagement.EncryptionKeyManager;
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

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.pci.encryption.enabled=true",
        "security.pci.encryption.key.rotation.hours=24",
        "security.pci.audit.enabled=true"
})
@DisplayName("PCI Field Encryption Service Tests")
class PCIFieldEncryptionServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private PCIFieldEncryptionService encryptionService;

    @MockBean
    private EncryptionKeyManager keyManager;

    @MockBean
    private AuditService auditService;

    private SecretKey testEncryptionKey;
    private String testContextId;

    @BeforeEach
    void setUp() throws Exception {
        testContextId = UUID.randomUUID().toString();
        
        // Generate test AES-256 key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        testEncryptionKey = keyGen.generateKey();

        // Mock key manager to return test key
        when(keyManager.getOrGenerateKey(anyString(), eq(256))).thenReturn(testEncryptionKey);
    }

    @Nested
    @DisplayName("PAN Encryption Tests")
    class PANEncryptionTests {

        @Test
        @DisplayName("Should encrypt valid PAN successfully")
        void shouldEncryptValidPANSuccessfully() {
            String pan = "4111111111111111"; // Valid test PAN

            PCIFieldEncryptionService.EncryptionResult result =
                    encryptionService.encryptPAN(pan, testContextId);

            assertThat(result).isNotNull();
            assertThat(result.getEncryptedData()).isNotNull();
            assertThat(result.getEncryptedData()).isNotEqualTo(pan);
            assertThat(result.getIv()).isNotNull();
            assertThat(result.getKeyType()).isEqualTo(PCIFieldEncryptionService.KeyType.PAN_ENCRYPTION);
            assertThat(result.getIv().length).isEqualTo(12); // GCM IV length
        }

        @Test
        @DisplayName("Should encrypt and decrypt PAN correctly")
        void shouldEncryptAndDecryptPANCorrectly() {
            String originalPan = "4111111111111111";

            PCIFieldEncryptionService.EncryptionResult encrypted =
                    encryptionService.encryptPAN(originalPan, testContextId);

            String decrypted = encryptionService.decryptPAN(encrypted, testContextId);

            assertThat(decrypted).isEqualTo(originalPan);
        }

        @Test
        @DisplayName("Should reject invalid PAN format")
        void shouldRejectInvalidPANFormat() {
            String invalidPan = "1234"; // Too short

            assertThatThrownBy(() -> encryptionService.encryptPAN(invalidPan, testContextId))
                    .isInstanceOf(PCIEncryptionException.class);
        }

        @Test
        @DisplayName("Should reject PAN with non-numeric characters")
        void shouldRejectPANWithNonNumericCharacters() {
            String invalidPan = "411111111111111a"; // Contains letter

            assertThatThrownBy(() -> encryptionService.encryptPAN(invalidPan, testContextId))
                    .isInstanceOf(PCIEncryptionException.class);
        }

        @Test
        @DisplayName("Should reject null PAN")
        void shouldRejectNullPAN() {
            assertThatThrownBy(() -> encryptionService.encryptPAN(null, testContextId))
                    .isInstanceOf(PCIEncryptionException.class);
        }

        @Test
        @DisplayName("Should reject empty PAN")
        void shouldRejectEmptyPAN() {
            assertThatThrownBy(() -> encryptionService.encryptPAN("", testContextId))
                    .isInstanceOf(PCIEncryptionException.class);
        }

        @Test
        @DisplayName("Should produce different ciphertext for same PAN (unique IV)")
        void shouldProduceDifferentCiphertextForSamePAN() {
            String pan = "4111111111111111";

            PCIFieldEncryptionService.EncryptionResult result1 =
                    encryptionService.encryptPAN(pan, testContextId);
            PCIFieldEncryptionService.EncryptionResult result2 =
                    encryptionService.encryptPAN(pan, testContextId);

            // Same plaintext should produce different ciphertext due to unique IV
            assertThat(result1.getEncryptedData()).isNotEqualTo(result2.getEncryptedData());
            assertThat(result1.getIv()).isNotEqualTo(result2.getIv());
        }

        @Test
        @DisplayName("Should audit successful PAN encryption")
        void shouldAuditSuccessfulPANEncryption() {
            String pan = "4111111111111111";

            encryptionService.encryptPAN(pan, testContextId);

            verify(auditService, times(1)).log(
                    eq("PAN_ENCRYPTED"),
                    eq(testContextId),
                    any(PCIFieldEncryptionService.KeyType.class),
                    eq(true)
            );
        }

        @Test
        @DisplayName("Should use AES-256-GCM encryption")
        void shouldUseAES256GCMEncryption() {
            String pan = "4111111111111111";

            PCIFieldEncryptionService.EncryptionResult result =
                    encryptionService.encryptPAN(pan, testContextId);

            // Verify key length is 256-bit (requested from key manager)
            verify(keyManager).getOrGenerateKey(anyString(), eq(256));
            
            // GCM tag is 128 bits (16 bytes), added to ciphertext
            byte[] encrypted = Base64.getDecoder().decode(result.getEncryptedData());
            assertThat(encrypted.length).isGreaterThan(pan.length()); // Ciphertext + auth tag
        }
    }

    @Nested
    @DisplayName("CVV Encryption Tests")
    class CVVEncryptionTests {

        @Test
        @DisplayName("Should encrypt valid CVV successfully")
        void shouldEncryptValidCVVSuccessfully() {
            String cvv = "123";

            PCIFieldEncryptionService.EncryptionResult result =
                    encryptionService.encryptCVV(cvv, testContextId);

            assertThat(result).isNotNull();
            assertThat(result.getEncryptedData()).isNotNull();
            assertThat(result.getEncryptedData()).isNotEqualTo(cvv);
            assertThat(result.getKeyType()).isEqualTo(PCIFieldEncryptionService.KeyType.CVV_ENCRYPTION);
        }

        @Test
        @DisplayName("Should encrypt and decrypt CVV correctly")
        void shouldEncryptAndDecryptCVVCorrectly() {
            String originalCvv = "456";

            PCIFieldEncryptionService.EncryptionResult encrypted =
                    encryptionService.encryptCVV(originalCvv, testContextId);

            String decrypted = encryptionService.decryptCVV(encrypted, testContextId);

            assertThat(decrypted).isEqualTo(originalCvv);
        }

        @Test
        @DisplayName("Should support 3-digit CVV")
        void shouldSupport3DigitCVV() {
            String cvv = "123";

            PCIFieldEncryptionService.EncryptionResult result =
                    encryptionService.encryptCVV(cvv, testContextId);

            assertThat(result).isNotNull();
            assertThat(result.getEncryptedData()).isNotNull();
        }

        @Test
        @DisplayName("Should support 4-digit CVV (AMEX)")
        void shouldSupport4DigitCVV() {
            String cvv = "1234";

            PCIFieldEncryptionService.EncryptionResult result =
                    encryptionService.encryptCVV(cvv, testContextId);

            assertThat(result).isNotNull();
            assertThat(result.getEncryptedData()).isNotNull();
        }

        @Test
        @DisplayName("Should reject invalid CVV format")
        void shouldRejectInvalidCVVFormat() {
            String invalidCvv = "12"; // Too short

            assertThatThrownBy(() -> encryptionService.encryptCVV(invalidCvv, testContextId))
                    .isInstanceOf(PCIEncryptionException.class);
        }

        @Test
        @DisplayName("Should reject CVV with non-numeric characters")
        void shouldRejectCVVWithNonNumericCharacters() {
            String invalidCvv = "12a";

            assertThatThrownBy(() -> encryptionService.encryptCVV(invalidCvv, testContextId))
                    .isInstanceOf(PCIEncryptionException.class);
        }

        @Test
        @DisplayName("Should never log CVV in plaintext")
        void shouldNeverLogCVVInPlaintext() {
            String cvv = "123";

            encryptionService.encryptCVV(cvv, testContextId);

            // Verify audit log doesn't contain plaintext CVV
            verify(auditService, never()).log(contains(cvv), any(), any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("Cardholder Name Encryption Tests")
    class CardholderNameEncryptionTests {

        @Test
        @DisplayName("Should encrypt cardholder name successfully")
        void shouldEncryptCardholderNameSuccessfully() {
            String name = "John Doe";

            PCIFieldEncryptionService.EncryptionResult result =
                    encryptionService.encryptCardholderName(name, testContextId);

            assertThat(result).isNotNull();
            assertThat(result.getEncryptedData()).isNotNull();
            assertThat(result.getEncryptedData()).isNotEqualTo(name);
            assertThat(result.getKeyType()).isEqualTo(PCIFieldEncryptionService.KeyType.CARDHOLDER_NAME);
        }

        @Test
        @DisplayName("Should encrypt and decrypt cardholder name correctly")
        void shouldEncryptAndDecryptCardholderNameCorrectly() {
            String originalName = "Jane Smith";

            PCIFieldEncryptionService.EncryptionResult encrypted =
                    encryptionService.encryptCardholderName(originalName, testContextId);

            String decrypted = encryptionService.decryptCardholderName(encrypted, testContextId);

            assertThat(decrypted).isEqualTo(originalName);
        }

        @Test
        @DisplayName("Should handle special characters in name")
        void shouldHandleSpecialCharactersInName() {
            String name = "José María O'Brien-Smith";

            PCIFieldEncryptionService.EncryptionResult encrypted =
                    encryptionService.encryptCardholderName(name, testContextId);
            String decrypted = encryptionService.decryptCardholderName(encrypted, testContextId);

            assertThat(decrypted).isEqualTo(name);
        }

        @Test
        @DisplayName("Should handle long cardholder names")
        void shouldHandleLongCardholderNames() {
            String longName = "A".repeat(100);

            PCIFieldEncryptionService.EncryptionResult encrypted =
                    encryptionService.encryptCardholderName(longName, testContextId);
            String decrypted = encryptionService.decryptCardholderName(encrypted, testContextId);

            assertThat(decrypted).isEqualTo(longName);
        }
    }

    @Nested
    @DisplayName("PAN Masking Tests")
    class PANMaskingTests {

        @Test
        @DisplayName("Should mask PAN correctly (show last 4 digits)")
        void shouldMaskPANCorrectly() {
            String pan = "4111111111111111";

            String masked = encryptionService.maskPAN(pan);

            assertThat(masked).isEqualTo("************1111");
        }

        @Test
        @DisplayName("Should mask short PAN (13 digits)")
        void shouldMaskShortPAN() {
            String pan = "4111111111111";

            String masked = encryptionService.maskPAN(pan);

            assertThat(masked).isEqualTo("*********1111");
        }

        @Test
        @DisplayName("Should mask long PAN (19 digits)")
        void shouldMaskLongPAN() {
            String pan = "4111111111111111111";

            String masked = encryptionService.maskPAN(pan);

            assertThat(masked).isEqualTo("***************1111");
        }

        @Test
        @DisplayName("Should handle null PAN in masking")
        void shouldHandleNullPANInMasking() {
            String masked = encryptionService.maskPAN(null);

            assertThat(masked).isEqualTo("****");
        }

        @Test
        @DisplayName("Should never return full PAN from masking")
        void shouldNeverReturnFullPANFromMasking() {
            String pan = "4111111111111111";

            String masked = encryptionService.maskPAN(pan);

            assertThat(masked).isNotEqualTo(pan);
            assertThat(masked).doesNotContain("41111111");
        }
    }

    @Nested
    @DisplayName("Key Management Tests")
    class KeyManagementTests {

        @Test
        @DisplayName("Should use separate keys for different data types")
        void shouldUseSeparateKeysForDifferentDataTypes() {
            String pan = "4111111111111111";
            String cvv = "123";

            encryptionService.encryptPAN(pan, testContextId);
            encryptionService.encryptCVV(cvv, testContextId);

            verify(keyManager).getOrGenerateKey(
                    eq(PCIFieldEncryptionService.KeyType.PAN_ENCRYPTION.getKeyId()), eq(256));
            verify(keyManager).getOrGenerateKey(
                    eq(PCIFieldEncryptionService.KeyType.CVV_ENCRYPTION.getKeyId()), eq(256));
        }

        @Test
        @DisplayName("Should request 256-bit encryption keys")
        void shouldRequest256BitEncryptionKeys() {
            String pan = "4111111111111111";

            encryptionService.encryptPAN(pan, testContextId);

            verify(keyManager).getOrGenerateKey(anyString(), eq(256));
        }

        @Test
        @DisplayName("Should handle key manager failures gracefully")
        void shouldHandleKeyManagerFailuresGracefully() {
            when(keyManager.getOrGenerateKey(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("Key generation failed"));

            String pan = "4111111111111111";

            assertThatThrownBy(() -> encryptionService.encryptPAN(pan, testContextId))
                    .isInstanceOf(PCIEncryptionException.class)
                    .hasMessageContaining("Failed to encrypt PAN");
        }
    }

    @Nested
    @DisplayName("Encryption Algorithm Tests")
    class EncryptionAlgorithmTests {

        @Test
        @DisplayName("Should use unique IV for each encryption")
        void shouldUseUniqueIVForEachEncryption() {
            String pan = "4111111111111111";

            PCIFieldEncryptionService.EncryptionResult result1 =
                    encryptionService.encryptPAN(pan, testContextId);
            PCIFieldEncryptionService.EncryptionResult result2 =
                    encryptionService.encryptPAN(pan, testContextId);

            assertThat(result1.getIv()).isNotEqualTo(result2.getIv());
        }

        @Test
        @DisplayName("Should produce authenticated ciphertext (GCM)")
        void shouldProduceAuthenticatedCiphertext() {
            String pan = "4111111111111111";

            PCIFieldEncryptionService.EncryptionResult result =
                    encryptionService.encryptPAN(pan, testContextId);

            // GCM adds 128-bit authentication tag
            byte[] encrypted = Base64.getDecoder().decode(result.getEncryptedData());
            assertThat(encrypted.length).isEqualTo(pan.length() + 16); // Plaintext + 16-byte tag
        }

        @Test
        @DisplayName("Should detect tampering (authentication failure)")
        void shouldDetectTampering() {
            String pan = "4111111111111111";

            PCIFieldEncryptionService.EncryptionResult result =
                    encryptionService.encryptPAN(pan, testContextId);

            // Tamper with ciphertext
            byte[] tampered = Base64.getDecoder().decode(result.getEncryptedData());
            tampered[0] ^= 1; // Flip one bit
            String tamperedEncrypted = Base64.getEncoder().encodeToString(tampered);

            PCIFieldEncryptionService.EncryptionResult tamperedResult =
                    new PCIFieldEncryptionService.EncryptionResult(
                            tamperedEncrypted, result.getIv(), result.getKeyType());

            assertThatThrownBy(() -> encryptionService.decryptPAN(tamperedResult, testContextId))
                    .isInstanceOf(PCIEncryptionException.class)
                    .hasMessageContaining("decryption failed");
        }

        @Test
        @DisplayName("Should use 12-byte IV for GCM mode")
        void shouldUse12ByteIVForGCMMode() {
            String pan = "4111111111111111";

            PCIFieldEncryptionService.EncryptionResult result =
                    encryptionService.encryptPAN(pan, testContextId);

            assertThat(result.getIv()).hasSize(12); // 96 bits
        }
    }

    @Nested
    @DisplayName("Audit and Compliance Tests")
    class AuditAndComplianceTests {

        @Test
        @DisplayName("Should audit all encryption operations")
        void shouldAuditAllEncryptionOperations() {
            String pan = "4111111111111111";
            String cvv = "123";
            String name = "John Doe";

            encryptionService.encryptPAN(pan, testContextId);
            encryptionService.encryptCVV(cvv, testContextId);
            encryptionService.encryptCardholderName(name, testContextId);

            verify(auditService, atLeast(3)).log(anyString(), anyString(), any(), anyBoolean());
        }

        @Test
        @DisplayName("Should audit encryption failures")
        void shouldAuditEncryptionFailures() {
            when(keyManager.getOrGenerateKey(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("Key failure"));

            String pan = "4111111111111111";

            try {
                encryptionService.encryptPAN(pan, testContextId);
            } catch (PCIEncryptionException e) {
                // Expected
            }

            verify(auditService).log(
                    eq("PAN_ENCRYPTION_FAILED"),
                    eq(testContextId),
                    any(PCIFieldEncryptionService.KeyType.class),
                    eq(false)
            );
        }

        @Test
        @DisplayName("Should never log plaintext sensitive data")
        void shouldNeverLogPlaintextSensitiveData() {
            String pan = "4111111111111111";

            encryptionService.encryptPAN(pan, testContextId);

            // Verify audit service never receives plaintext PAN
            verify(auditService, never()).log(contains(pan), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("Should include context ID in audit logs")
        void shouldIncludeContextIDInAuditLogs() {
            String pan = "4111111111111111";

            encryptionService.encryptPAN(pan, testContextId);

            verify(auditService).log(anyString(), eq(testContextId), any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should encrypt PAN in under 100ms")
        void shouldEncryptPANInUnder100ms() {
            String pan = "4111111111111111";

            long startTime = System.currentTimeMillis();
            encryptionService.encryptPAN(pan, testContextId);
            long endTime = System.currentTimeMillis();

            long duration = endTime - startTime;
            assertThat(duration).isLessThan(100);
        }

        @Test
        @DisplayName("Should handle concurrent encryption operations")
        void shouldHandleConcurrentEncryptionOperations() throws Exception {
            String pan = "4111111111111111";
            int threadCount = 10;

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    encryptionService.encryptPAN(pan, testContextId);
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            verify(keyManager, times(threadCount)).getOrGenerateKey(anyString(), eq(256));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw PCIEncryptionException on encryption failure")
        void shouldThrowPCIEncryptionExceptionOnEncryptionFailure() {
            when(keyManager.getOrGenerateKey(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("Crypto error"));

            String pan = "4111111111111111";

            assertThatThrownBy(() -> encryptionService.encryptPAN(pan, testContextId))
                    .isInstanceOf(PCIEncryptionException.class);
        }

        @Test
        @DisplayName("Should throw PCIEncryptionException on decryption failure")
        void shouldThrowPCIEncryptionExceptionOnDecryptionFailure() {
            String pan = "4111111111111111";

            PCIFieldEncryptionService.EncryptionResult result =
                    encryptionService.encryptPAN(pan, testContextId);

            // Use wrong key for decryption
            SecretKey wrongKey;
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                wrongKey = keyGen.generateKey();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            when(keyManager.getOrGenerateKey(anyString(), eq(256))).thenReturn(wrongKey);

            assertThatThrownBy(() -> encryptionService.decryptPAN(result, testContextId))
                    .isInstanceOf(PCIEncryptionException.class);
        }

        @Test
        @DisplayName("Should handle null encryption result gracefully")
        void shouldHandleNullEncryptionResultGracefully() {
            assertThatThrownBy(() -> encryptionService.decryptPAN(null, testContextId))
                    .isInstanceOf(PCIEncryptionException.class);
        }
    }
}