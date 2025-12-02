package com.waqiti.compliance.pci;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for FF3-1 Format-Preserving Encryption
 *
 * Test Coverage:
 * - Encryption/decryption correctness
 * - Format preservation
 * - Security properties
 * - PCI-DSS compliance validation
 * - Edge cases and error handling
 * - Performance benchmarks
 *
 * @author Waqiti Security Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FF3-1 Format-Preserving Encryption Service Tests")
class FormatPreservingEncryptionServiceTest {

    @Mock
    private KeyManagementService keyManagementService;

    @Mock
    private AuditService auditService;

    private FormatPreservingEncryptionService fpeService;
    private SecretKey testKey;

    @BeforeEach
    void setUp() throws Exception {
        fpeService = new FormatPreservingEncryptionService(keyManagementService, auditService);

        // Generate 256-bit AES key for testing
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        testKey = keyGen.generateKey();

        when(keyManagementService.getKey(anyString())).thenReturn(testKey);
    }

    @Nested
    @DisplayName("Encryption Tests")
    class EncryptionTests {

        @Test
        @DisplayName("Should successfully encrypt valid credit card number")
        void shouldEncryptValidPAN() {
            // GIVEN
            String plaintext = "4532015112830366"; // Valid Visa test card
            String keyId = "card-encryption-key-v1";

            // WHEN
            String ciphertext = fpeService.encrypt(plaintext, keyId);

            // THEN
            assertThat(ciphertext).isNotNull();
            assertThat(ciphertext).hasSize(plaintext.length());
            assertThat(ciphertext).isNotEqualTo(plaintext);
            assertThat(ciphertext).matches("\\d+"); // All digits preserved

            verify(auditService).logEncryption(
                eq("FF3-1"),
                eq(keyId),
                eq(plaintext.length()),
                eq("PAN_TOKENIZATION"),
                eq(true)
            );
        }

        @Test
        @DisplayName("Should preserve format for different card lengths")
        void shouldPreserveFormatForDifferentLengths() {
            String[] testCards = {
                "4111111111111111",     // 16 digits (Visa)
                "378282246310005",      // 15 digits (Amex)
                "30569309025904",       // 14 digits (Diners)
                "6011111111111117",     // 16 digits (Discover)
                "5555555555554444"      // 16 digits (Mastercard)
            };

            for (String plaintext : testCards) {
                // WHEN
                String ciphertext = fpeService.encrypt(plaintext, "test-key");

                // THEN
                assertThat(ciphertext)
                    .hasSize(plaintext.length())
                    .matches("\\d+")
                    .isNotEqualTo(plaintext);
            }
        }

        @Test
        @DisplayName("Should produce different ciphertexts for same plaintext (probabilistic encryption)")
        void shouldProduceDifferentCiphertexts() {
            // GIVEN
            String plaintext = "4532015112830366";
            Set<String> ciphertexts = new HashSet<>();

            // WHEN
            for (int i = 0; i < 10; i++) {
                String ciphertext = fpeService.encrypt(plaintext, "test-key");
                ciphertexts.add(ciphertext);
            }

            // THEN
            // Due to random tweak, should produce different ciphertexts
            // Note: In production with stored tweaks, this would be deterministic
            assertThat(ciphertexts.size()).isGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("Decryption Tests")
    class DecryptionTests {

        @Test
        @DisplayName("Should successfully decrypt to original plaintext")
        void shouldDecryptToOriginalPlaintext() {
            // GIVEN
            String originalPAN = "4532015112830366";
            String keyId = "card-encryption-key-v1";

            // WHEN
            String encrypted = fpeService.encrypt(originalPAN, keyId);
            String decrypted = fpeService.decrypt(encrypted, keyId);

            // THEN
            assertThat(decrypted).isEqualTo(originalPAN);

            verify(auditService).logDecryption(
                eq("FF3-1"),
                eq(keyId),
                anyInt(),
                eq("PAN_DETOKENIZATION"),
                eq(true)
            );
        }

        @Test
        @DisplayName("Should handle encrypt/decrypt roundtrip for multiple cards")
        void shouldHandleMultipleRoundtrips() {
            String[] testCards = {
                "4111111111111111",
                "378282246310005",
                "5555555555554444"
            };

            for (String original : testCards) {
                // WHEN
                String encrypted = fpeService.encrypt(original, "test-key");
                String decrypted = fpeService.decrypt(encrypted, "test-key");

                // THEN
                assertThat(decrypted).isEqualTo(original);
            }
        }
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should reject null input")
        void shouldRejectNullInput() {
            assertThatThrownBy(() -> fpeService.encrypt(null, "test-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
        }

        @Test
        @DisplayName("Should reject empty input")
        void shouldRejectEmptyInput() {
            assertThatThrownBy(() -> fpeService.encrypt("", "test-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("Should reject non-numeric input")
        void shouldRejectNonNumericInput() {
            assertThatThrownBy(() -> fpeService.encrypt("4532-0151-1283-0366", "test-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain only digits");
        }

        @Test
        @DisplayName("Should reject input shorter than minimum length")
        void shouldRejectTooShortInput() {
            assertThatThrownBy(() -> fpeService.encrypt("12345", "test-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length must be between");
        }

        @Test
        @DisplayName("Should reject input longer than maximum length")
        void shouldRejectTooLongInput() {
            String tooLong = "12345678901234567890"; // 20 digits
            assertThatThrownBy(() -> fpeService.encrypt(tooLong, "test-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length must be between");
        }
    }

    @Nested
    @DisplayName("Security Properties Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should not leak information about plaintext in ciphertext")
        void shouldNotLeakPlaintextInformation() {
            // GIVEN
            String pan1 = "4111111111111111";
            String pan2 = "4111111111111112"; // Only last digit different

            // WHEN
            String cipher1 = fpeService.encrypt(pan1, "test-key");
            String cipher2 = fpeService.encrypt(pan2, "test-key");

            // THEN
            // Ciphertexts should be completely different (no pattern leakage)
            int differences = 0;
            for (int i = 0; i < cipher1.length(); i++) {
                if (cipher1.charAt(i) != cipher2.charAt(i)) {
                    differences++;
                }
            }

            // Expect significant differences (avalanche effect)
            assertThat(differences).isGreaterThan(8);
        }

        @Test
        @DisplayName("Should use different keys for different keyIds")
        void shouldUseDifferentKeysForDifferentKeyIds() throws Exception {
            // GIVEN
            String plaintext = "4532015112830366";
            SecretKey key1 = KeyGenerator.getInstance("AES").generateKey();
            SecretKey key2 = KeyGenerator.getInstance("AES").generateKey();

            when(keyManagementService.getKey("key1")).thenReturn(key1);
            when(keyManagementService.getKey("key2")).thenReturn(key2);

            // WHEN
            String cipher1 = fpeService.encrypt(plaintext, "key1");
            String cipher2 = fpeService.encrypt(plaintext, "key2");

            // THEN
            assertThat(cipher1).isNotEqualTo(cipher2);
        }

        @Test
        @DisplayName("Should handle key rotation securely")
        void shouldHandleKeyRotation() throws Exception {
            // GIVEN
            String plaintext = "4532015112830366";
            SecretKey oldKey = KeyGenerator.getInstance("AES").generateKey();
            SecretKey newKey = KeyGenerator.getInstance("AES").generateKey();

            when(keyManagementService.getKey("old-key")).thenReturn(oldKey);
            when(keyManagementService.getKey("new-key")).thenReturn(newKey);

            // WHEN
            String encryptedWithOldKey = fpeService.encrypt(plaintext, "old-key");
            String decryptedWithOldKey = fpeService.decrypt(encryptedWithOldKey, "old-key");

            String reEncryptedWithNewKey = fpeService.encrypt(decryptedWithOldKey, "new-key");
            String decryptedWithNewKey = fpeService.decrypt(reEncryptedWithNewKey, "new-key");

            // THEN
            assertThat(decryptedWithOldKey).isEqualTo(plaintext);
            assertThat(decryptedWithNewKey).isEqualTo(plaintext);
            assertThat(encryptedWithOldKey).isNotEqualTo(reEncryptedWithNewKey);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should log encryption failures")
        void shouldLogEncryptionFailures() {
            // GIVEN
            when(keyManagementService.getKey(anyString()))
                .thenThrow(new RuntimeException("Vault unavailable"));

            // WHEN/THEN
            assertThatThrownBy(() -> fpeService.encrypt("4532015112830366", "test-key"))
                .isInstanceOf(FormatPreservingEncryptionService.EncryptionException.class)
                .hasMessageContaining("encryption failed");

            verify(auditService).logEncryptionFailure(
                eq("FF3-1"),
                eq("test-key"),
                anyString()
            );
        }

        @Test
        @DisplayName("Should log decryption failures")
        void shouldLogDecryptionFailures() {
            // GIVEN
            when(keyManagementService.getKey(anyString()))
                .thenThrow(new RuntimeException("Vault unavailable"));

            // WHEN/THEN
            assertThatThrownBy(() -> fpeService.decrypt("4532015112830366", "test-key"))
                .isInstanceOf(FormatPreservingEncryptionService.EncryptionException.class)
                .hasMessageContaining("decryption failed");

            verify(auditService).logDecryptionFailure(
                eq("FF3-1"),
                eq("test-key"),
                anyString()
            );
        }

        @Test
        @DisplayName("Should reject keys that are not 256-bit")
        void shouldRejectInvalidKeySize() throws Exception {
            // GIVEN
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128); // Wrong key size
            SecretKey invalidKey = keyGen.generateKey();

            when(keyManagementService.getKey("invalid-key")).thenReturn(invalidKey);

            // WHEN/THEN
            assertThatThrownBy(() -> fpeService.encrypt("4532015112830366", "invalid-key"))
                .isInstanceOf(FormatPreservingEncryptionService.EncryptionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256-bit AES key");
        }
    }

    @Nested
    @DisplayName("PCI-DSS Compliance Tests")
    class PCIDSSComplianceTests {

        @Test
        @DisplayName("Should maintain BIN (first 6 digits) format")
        void shouldMaintainBINFormat() {
            // GIVEN
            String visa = "4532015112830366"; // BIN: 453201

            // WHEN
            String encrypted = fpeService.encrypt(visa, "test-key");

            // THEN
            // Format preserved: still 16 digits
            assertThat(encrypted).hasSize(16);
            assertThat(encrypted).matches("\\d{16}");

            // BIN may change but format is preserved (all digits)
            assertThat(encrypted.substring(0, 6)).matches("\\d{6}");
        }

        @Test
        @DisplayName("Should maintain last 4 digits format")
        void shouldMaintainLast4Format() {
            // GIVEN
            String pan = "4532015112830366"; // Last 4: 0366

            // WHEN
            String encrypted = fpeService.encrypt(pan, "test-key");

            // THEN
            assertThat(encrypted.substring(12)).matches("\\d{4}");
        }

        @Test
        @DisplayName("Should support Luhn algorithm compatible output")
        void shouldSupportLuhnAlgorithm() {
            // GIVEN
            String validPAN = "4532015112830366"; // Passes Luhn check

            // WHEN
            String encrypted = fpeService.encrypt(validPAN, "test-key");

            // THEN
            // Encrypted value maintains numeric format (Luhn can be computed)
            assertThat(encrypted).matches("\\d+");

            // Note: Encrypted value may not pass Luhn, but format is compatible
            // For production, can optionally adjust last digit to pass Luhn
        }

        @Test
        @DisplayName("Should audit all tokenization operations")
        void shouldAuditAllTokenizations() {
            // GIVEN
            String pan = "4532015112830366";
            String keyId = "prod-card-key-v1";

            // WHEN
            fpeService.encrypt(pan, keyId);
            fpeService.decrypt(pan, keyId);

            // THEN
            verify(auditService).logEncryption(
                eq("FF3-1"),
                eq(keyId),
                eq(16),
                eq("PAN_TOKENIZATION"),
                eq(true)
            );

            verify(auditService).logDecryption(
                eq("FF3-1"),
                eq(keyId),
                anyInt(),
                eq("PAN_DETOKENIZATION"),
                eq(true)
            );
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should encrypt within acceptable time limit (< 5ms)")
        void shouldEncryptWithinTimeLimit() {
            // GIVEN
            String pan = "4532015112830366";
            int iterations = 100;

            // WHEN
            long startTime = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                fpeService.encrypt(pan, "test-key");
            }
            long endTime = System.nanoTime();

            // THEN
            long avgTimeMs = (endTime - startTime) / iterations / 1_000_000;
            assertThat(avgTimeMs).isLessThan(5L); // < 5ms per operation
        }

        @Test
        @DisplayName("Should be thread-safe for concurrent operations")
        void shouldBeThreadSafe() throws InterruptedException {
            // GIVEN
            String pan = "4532015112830366";
            int numThreads = 10;
            Thread[] threads = new Thread[numThreads];
            Set<String> results = new HashSet<>();

            // WHEN
            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Thread(() -> {
                    String encrypted = fpeService.encrypt(pan, "test-key");
                    synchronized (results) {
                        results.add(encrypted);
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // THEN
            // All operations should complete without errors
            assertThat(results).hasSize(numThreads);
        }
    }
}
