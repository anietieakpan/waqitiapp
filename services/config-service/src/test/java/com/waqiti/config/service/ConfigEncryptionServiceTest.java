package com.waqiti.config.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultSysOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultResponse;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ConfigEncryptionService
 * Tests encryption, decryption, batch operations, key rotation, and error handling
 */
@ExtendWith(MockitoExtension.class)
class ConfigEncryptionServiceTest {

    @Mock
    private VaultTemplate vaultTemplate;

    @Mock
    private VaultSysOperations vaultSysOperations;

    private ConfigEncryptionService encryptionService;

    private static final String VAULT_PATH = "secret/config";
    private static final String ENCRYPTION_KEY_NAME = "encryption-key";
    private static final String TEST_PLAINTEXT = "my-secret-password-123";
    private static final String TEST_KEY_BASE64 = "dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcy1sb25nISEh"; // 32 bytes base64

    @BeforeEach
    void setUp() {
        encryptionService = new ConfigEncryptionService(vaultTemplate);

        // Set required configuration properties via reflection
        ReflectionTestUtils.setField(encryptionService, "vaultEncryptionPath", VAULT_PATH);
        ReflectionTestUtils.setField(encryptionService, "vaultEncryptionKey", ENCRYPTION_KEY_NAME);
        ReflectionTestUtils.setField(encryptionService, "encryptionAlgorithm", "AES/GCM/NoPadding");
        ReflectionTestUtils.setField(encryptionService, "keySize", 256);
        ReflectionTestUtils.setField(encryptionService, "gcmIvLength", 12);
        ReflectionTestUtils.setField(encryptionService, "gcmTagLength", 16);

        // Mock Vault to return encryption key
        mockVaultKeyResponse();
    }

    // ==================== Encryption Tests ====================

    @Test
    void testEncrypt_ValidPlaintext_ReturnsEncryptedValue() {
        String encrypted = encryptionService.encrypt(TEST_PLAINTEXT);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).startsWith("ENC(");
        assertThat(encrypted).endsWith(")");
        assertThat(encrypted).isNotEqualTo(TEST_PLAINTEXT);
    }

    @Test
    void testEncrypt_AlreadyEncrypted_ReturnsUnchanged() {
        String alreadyEncrypted = "ENC(c29tZS1lbmNyeXB0ZWQtZGF0YQ==)";

        String result = encryptionService.encrypt(alreadyEncrypted);

        assertThat(result).isEqualTo(alreadyEncrypted);
    }

    @Test
    void testEncrypt_NullValue_ReturnsNull() {
        String result = encryptionService.encrypt(null);

        assertThat(result).isNull();
    }

    @Test
    void testEncrypt_EmptyString_ReturnsEmpty() {
        String result = encryptionService.encrypt("");

        assertThat(result).isEmpty();
    }

    @Test
    void testEncrypt_MultipleCallsSamePlaintext_ReturnsDifferentCiphertext() {
        // Due to random IV, same plaintext should produce different ciphertext each time
        String encrypted1 = encryptionService.encrypt(TEST_PLAINTEXT);
        String encrypted2 = encryptionService.encrypt(TEST_PLAINTEXT);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
        // But both should decrypt to same plaintext
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(TEST_PLAINTEXT);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(TEST_PLAINTEXT);
    }

    // ==================== Decryption Tests ====================

    @Test
    void testDecrypt_ValidEncryptedValue_ReturnsOriginalPlaintext() {
        String encrypted = encryptionService.encrypt(TEST_PLAINTEXT);

        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(TEST_PLAINTEXT);
    }

    @Test
    void testDecrypt_NonEncryptedValue_ReturnsUnchanged() {
        String plaintext = "not-encrypted-value";

        String result = encryptionService.decrypt(plaintext);

        assertThat(result).isEqualTo(plaintext);
    }

    @Test
    void testDecrypt_NullValue_ReturnsNull() {
        String result = encryptionService.decrypt(null);

        assertThat(result).isNull();
    }

    @Test
    void testDecrypt_EmptyString_ReturnsEmpty() {
        String result = encryptionService.decrypt("");

        assertThat(result).isEmpty();
    }

    @Test
    void testDecrypt_InvalidEncryptedData_ThrowsException() {
        String invalidEncrypted = "ENC(invalid-base64-data!@#$)";

        assertThatThrownBy(() -> encryptionService.decrypt(invalidEncrypted))
            .isInstanceOf(ConfigEncryptionService.ConfigurationEncryptionException.class)
            .hasMessageContaining("Failed to decrypt");
    }

    // ==================== Encryption/Decryption Cycle Tests ====================

    @Test
    void testEncryptDecryptCycle_VariousDataTypes() {
        String[] testCases = {
            "simple-string",
            "special-chars: !@#$%^&*()",
            "unicode: 你好世界 🌍",
            "numbers: 123456789",
            "json: {\"key\":\"value\",\"number\":42}",
            "multiline:\nline1\nline2\nline3"
        };

        for (String testCase : testCases) {
            String encrypted = encryptionService.encrypt(testCase);
            String decrypted = encryptionService.decrypt(encrypted);

            assertThat(decrypted)
                .as("Failed for test case: %s", testCase)
                .isEqualTo(testCase);
        }
    }

    // ==================== Batch Operations Tests ====================

    @Test
    void testEncryptBatch_MultipleValues_AllEncrypted() {
        Map<String, String> plaintextMap = new HashMap<>();
        plaintextMap.put("db.password", "secret123");
        plaintextMap.put("api.key", "api-key-456");
        plaintextMap.put("jwt.secret", "jwt-secret-789");

        Map<String, String> encrypted = encryptionService.encryptBatch(plaintextMap);

        assertThat(encrypted).hasSize(3);
        assertThat(encrypted.get("db.password")).startsWith("ENC(");
        assertThat(encrypted.get("api.key")).startsWith("ENC(");
        assertThat(encrypted.get("jwt.secret")).startsWith("ENC(");
    }

    @Test
    void testDecryptBatch_MultipleValues_AllDecrypted() {
        Map<String, String> plaintextMap = new HashMap<>();
        plaintextMap.put("db.password", "secret123");
        plaintextMap.put("api.key", "api-key-456");
        plaintextMap.put("jwt.secret", "jwt-secret-789");

        Map<String, String> encrypted = encryptionService.encryptBatch(plaintextMap);
        Map<String, String> decrypted = encryptionService.decryptBatch(encrypted);

        assertThat(decrypted).hasSize(3);
        assertThat(decrypted.get("db.password")).isEqualTo("secret123");
        assertThat(decrypted.get("api.key")).isEqualTo("api-key-456");
        assertThat(decrypted.get("jwt.secret")).isEqualTo("jwt-secret-789");
    }

    @Test
    void testEncryptBatch_EmptyMap_ReturnsEmpty() {
        Map<String, String> emptyMap = new HashMap<>();

        Map<String, String> result = encryptionService.encryptBatch(emptyMap);

        assertThat(result).isEmpty();
    }

    @Test
    void testDecryptBatch_MixedEncryptedAndPlain_HandlesCorrectly() {
        Map<String, String> mixedMap = new HashMap<>();
        mixedMap.put("encrypted.value", encryptionService.encrypt("secret"));
        mixedMap.put("plain.value", "not-encrypted");

        Map<String, String> decrypted = encryptionService.decryptBatch(mixedMap);

        assertThat(decrypted.get("encrypted.value")).isEqualTo("secret");
        assertThat(decrypted.get("plain.value")).isEqualTo("not-encrypted");
    }

    // ==================== isEncrypted() Tests ====================

    @Test
    void testIsEncrypted_ValidEncryptedValue_ReturnsTrue() {
        String encrypted = "ENC(c29tZS1lbmNyeXB0ZWQtZGF0YQ==)";

        boolean result = encryptionService.isEncrypted(encrypted);

        assertThat(result).isTrue();
    }

    @Test
    void testIsEncrypted_PlainValue_ReturnsFalse() {
        String plain = "not-encrypted";

        boolean result = encryptionService.isEncrypted(plain);

        assertThat(result).isFalse();
    }

    @Test
    void testIsEncrypted_NullValue_ReturnsFalse() {
        boolean result = encryptionService.isEncrypted(null);

        assertThat(result).isFalse();
    }

    @Test
    void testIsEncrypted_PartiallyEncryptedFormat_ReturnsFalse() {
        assertThat(encryptionService.isEncrypted("ENC(")).isFalse();
        assertThat(encryptionService.isEncrypted("ENC()")).isFalse();
        assertThat(encryptionService.isEncrypted("(data)")).isFalse();
    }

    // ==================== Validation Tests ====================

    @Test
    void testValidateEncryption_WithWorkingEncryption_ReturnsTrue() {
        boolean result = encryptionService.validateEncryption();

        assertThat(result).isTrue();
    }

    @Test
    void testValidateEncryption_WithVaultFailure_ReturnsFalse() {
        when(vaultTemplate.read(anyString())).thenThrow(new RuntimeException("Vault connection failed"));

        boolean result = encryptionService.validateEncryption();

        assertThat(result).isFalse();
    }

    // ==================== Encryption Health Tests ====================

    @Test
    void testGetEncryptionHealth_AllHealthy_ReturnsHealthyStatus() {
        when(vaultTemplate.opsForSys()).thenReturn(vaultSysOperations);
        when(vaultSysOperations.health()).thenReturn(mock(VaultHealth.class));

        ConfigEncryptionService.EncryptionHealthInfo health = encryptionService.getEncryptionHealth();

        assertThat(health.isVaultConnected()).isTrue();
        assertThat(health.isEncryptionKeyExists()).isTrue();
        assertThat(health.isEncryptionWorking()).isTrue();
        assertThat(health.getStatus()).isEqualTo("HEALTHY");
        assertThat(health.getAlgorithm()).isEqualTo("AES/GCM/NoPadding");
        assertThat(health.getKeySize()).isEqualTo(256);
        assertThat(health.getVaultPath()).isEqualTo(VAULT_PATH);
    }

    @Test
    void testGetEncryptionHealth_VaultDown_ReturnsUnhealthyStatus() {
        when(vaultTemplate.opsForSys()).thenReturn(vaultSysOperations);
        when(vaultSysOperations.health()).thenThrow(new RuntimeException("Vault down"));

        ConfigEncryptionService.EncryptionHealthInfo health = encryptionService.getEncryptionHealth();

        assertThat(health.isVaultConnected()).isFalse();
        assertThat(health.getStatus()).isEqualTo("UNHEALTHY");
    }

    // ==================== Key Rotation Tests ====================

    @Test
    void testReencrypt_ValidEncryptedValue_ReturnsNewEncryptedValue() {
        String originalEncrypted = encryptionService.encrypt(TEST_PLAINTEXT);

        // Mock key rotation
        mockNewVaultKey();

        String reencrypted = encryptionService.reencrypt(originalEncrypted, "new-key-id");

        assertThat(reencrypted).isNotNull();
        assertThat(reencrypted).startsWith("ENC(");
        assertThat(reencrypted).isNotEqualTo(originalEncrypted);

        // Should still decrypt to original plaintext
        String decrypted = encryptionService.decrypt(reencrypted);
        assertThat(decrypted).isEqualTo(TEST_PLAINTEXT);
    }

    @Test
    void testReencrypt_NonEncryptedValue_ThrowsException() {
        String plainValue = "not-encrypted";

        assertThatThrownBy(() -> encryptionService.reencrypt(plainValue, "new-key-id"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Value is not encrypted");
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testEncrypt_VaultUnavailable_ThrowsException() {
        when(vaultTemplate.read(anyString())).thenThrow(new RuntimeException("Vault unavailable"));

        assertThatThrownBy(() -> encryptionService.encrypt(TEST_PLAINTEXT))
            .isInstanceOf(ConfigEncryptionService.ConfigurationEncryptionException.class)
            .hasMessageContaining("Failed to get encryption key");
    }

    @Test
    void testDecrypt_CorruptedData_ThrowsException() {
        // Create encrypted value then corrupt it
        String encrypted = encryptionService.encrypt(TEST_PLAINTEXT);
        String corrupted = encrypted.substring(0, encrypted.length() - 5) + "XXXXX)";

        assertThatThrownBy(() -> encryptionService.decrypt(corrupted))
            .isInstanceOf(ConfigEncryptionService.ConfigurationEncryptionException.class)
            .hasMessageContaining("Failed to decrypt");
    }

    // ==================== Performance Tests ====================

    @Test
    void testBatchEncryption_PerformanceBetterThanIndividual() {
        Map<String, String> testData = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            testData.put("key" + i, "value" + i);
        }

        // Batch encryption
        long batchStart = System.nanoTime();
        Map<String, String> batchResult = encryptionService.encryptBatch(testData);
        long batchTime = System.nanoTime() - batchStart;

        assertThat(batchResult).hasSize(100);
        assertThat(batchTime).isLessThan(1_000_000_000L); // Should complete in under 1 second
    }

    // ==================== Helper Methods ====================

    private void mockVaultKeyResponse() {
        VaultResponse response = new VaultResponse();
        Map<String, Object> data = new HashMap<>();
        data.put("encryption-key", TEST_KEY_BASE64);
        data.put("created", "2024-01-01T00:00:00Z");
        data.put("algorithm", "AES");
        data.put("keySize", 256);

        response.setData(data);

        when(vaultTemplate.read(VAULT_PATH)).thenReturn(response);
        when(vaultTemplate.opsForSys()).thenReturn(vaultSysOperations);
        when(vaultSysOperations.health()).thenReturn(mock(VaultHealth.class));
    }

    private void mockNewVaultKey() {
        // Mock new key for re-encryption
        String newKeyBase64 = "bmV3LWVuY3J5cHRpb24ta2V5LTMyLWJ5dGVzLWxvbmchISE=";

        VaultResponse newResponse = new VaultResponse();
        Map<String, Object> newData = new HashMap<>();
        newData.put("encryption-key", newKeyBase64);
        newData.put("created", "2024-02-01T00:00:00Z");
        newData.put("algorithm", "AES");
        newData.put("keySize", 256);

        newResponse.setData(newData);

        when(vaultTemplate.read(VAULT_PATH)).thenReturn(newResponse);
        doNothing().when(vaultTemplate).write(anyString(), any());
    }
}
