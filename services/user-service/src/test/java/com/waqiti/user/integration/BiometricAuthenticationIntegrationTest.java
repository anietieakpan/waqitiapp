package com.waqiti.user.integration;

import com.waqiti.user.dto.*;
import com.waqiti.user.entity.BiometricCredential;
import com.waqiti.user.repository.BiometricCredentialRepository;
import com.waqiti.user.service.BiometricAuthService;
import com.yubico.webauthn.data.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Biometric Authentication Integration Tests (WebAuthn)
 *
 * SCENARIOS TESTED:
 * 1. Get registration options
 * 2. Register new biometric credential
 * 3. Get authentication options
 * 4. Authenticate with biometric
 * 5. List registered devices
 * 6. Remove biometric device
 * 7. Rename biometric device
 * 8. Invalid credential handling
 * 9. Duplicate credential prevention
 * 10. User verification requirement
 *
 * WEBAUTHN COMPLIANCE:
 * - PublicKeyCredentialCreationOptions
 * - PublicKeyCredentialRequestOptions
 * - Attestation verification
 * - Assertion verification
 * - Challenge validation
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
@DisplayName("Biometric Authentication (WebAuthn) Integration Tests")
class BiometricAuthenticationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BiometricAuthService biometricAuthService;

    @Autowired
    private BiometricCredentialRepository credentialRepository;

    @Test
    @DisplayName("Get registration options for new device")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testGetRegistrationOptions() throws Exception {
        // ACT & ASSERT
        mockMvc.perform(get("/api/v1/auth/webauthn/register/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.userName").exists())
                .andExpect(jsonPath("$.rpId").value("waqiti.com"))
                .andExpect(jsonPath("$.rpName").value("Waqiti"))
                .andExpect(jsonPath("$.timeout").value(60000))
                .andExpect(jsonPath("$.attestation").value("none"))
                .andExpect(jsonPath("$.authenticatorSelection").exists())
                .andExpect(jsonPath("$.authenticatorSelection.authenticatorAttachment").value("platform"))
                .andExpect(jsonPath("$.authenticatorSelection.userVerification").value("required"))
                .andExpect(jsonPath("$.excludeCredentials").isArray());
    }

    @Test
    @DisplayName("Register new biometric credential successfully")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testRegisterBiometricCredential() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();

        // Create mock WebAuthn registration data
        BiometricRegistrationRequest request = BiometricRegistrationRequest.builder()
                .credentialId(generateBase64CredentialId())
                .clientDataJSON(generateMockClientDataJSON("webauthn.create"))
                .attestationObject(generateMockAttestationObject())
                .deviceName("My MacBook Pro")
                .transports(new String[]{"internal", "hybrid"})
                .build();

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/auth/webauthn/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialId").exists())
                .andExpect(jsonPath("$.deviceName").value("My MacBook Pro"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Biometric device registered successfully"));

        // Verify credential was stored
        var credentials = credentialRepository.findByUserId(userId);
        assertThat(credentials).isNotEmpty();
        assertThat(credentials.get(0).getDeviceName()).isEqualTo("My MacBook Pro");
        assertThat(credentials.get(0).getUserVerified()).isTrue();
    }

    @Test
    @DisplayName("Prevent duplicate credential registration")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testPreventDuplicateCredential() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();
        String credentialId = generateBase64CredentialId();

        // Register first time
        BiometricRegistrationRequest request = BiometricRegistrationRequest.builder()
                .credentialId(credentialId)
                .clientDataJSON(generateMockClientDataJSON("webauthn.create"))
                .attestationObject(generateMockAttestationObject())
                .deviceName("Device 1")
                .build();

        biometricAuthService.registerCredential(userId, request);

        // ACT & ASSERT - Try to register same credential again
        mockMvc.perform(post("/api/v1/auth/webauthn/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Credential already registered"));
    }

    @Test
    @DisplayName("Get authentication options")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testGetAuthenticationOptions() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();

        // Register a credential first
        registerMockBiometricCredential(userId, "Test Device");

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/auth/webauthn/authenticate/options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new BiometricAuthOptionsRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").exists())
                .andExpect(jsonPath("$.timeout").value(60000))
                .andExpect(jsonPath("$.rpId").value("waqiti.com"))
                .andExpect(jsonPath("$.allowCredentials").isArray())
                .andExpect(jsonPath("$.allowCredentials[0].id").exists())
                .andExpect(jsonPath("$.allowCredentials[0].type").value("public-key"))
                .andExpect(jsonPath("$.userVerification").value("required"));
    }

    @Test
    @DisplayName("Authenticate with biometric successfully")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testAuthenticateWithBiometric() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();
        String credentialId = registerMockBiometricCredential(userId, "Auth Device");

        // Create mock authentication assertion
        BiometricAuthenticationRequest request = BiometricAuthenticationRequest.builder()
                .credentialId(credentialId)
                .clientDataJSON(generateMockClientDataJSON("webauthn.get"))
                .authenticatorData(generateMockAuthenticatorData())
                .signature(generateMockSignature())
                .userHandle(encodeBase64(userId.toString()))
                .build();

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/auth/webauthn/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.expiresIn").exists())
                .andExpect(jsonPath("$.userId").value(userId.toString()));

        // Verify last used timestamp was updated
        var credential = credentialRepository.findByCredentialId(credentialId).orElseThrow();
        assertThat(credential.getLastUsedAt()).isNotNull();
        assertThat(credential.getSignCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Authentication fails with invalid signature")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testAuthenticationFailsWithInvalidSignature() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();
        String credentialId = registerMockBiometricCredential(userId, "Test Device");

        // Create request with invalid signature
        BiometricAuthenticationRequest request = BiometricAuthenticationRequest.builder()
                .credentialId(credentialId)
                .clientDataJSON(generateMockClientDataJSON("webauthn.get"))
                .authenticatorData(generateMockAuthenticatorData())
                .signature("invalid_signature_data")
                .build();

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/auth/webauthn/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.error").value("Authentication failed: Invalid signature"));
    }

    @Test
    @DisplayName("Get list of registered biometric devices")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testGetBiometricDevices() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();

        // Register multiple devices
        registerMockBiometricCredential(userId, "MacBook Pro");
        registerMockBiometricCredential(userId, "iPhone 15");
        registerMockBiometricCredential(userId, "Windows PC");

        // ACT & ASSERT
        mockMvc.perform(get("/api/v1/auth/webauthn/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices").isArray())
                .andExpect(jsonPath("$.devices.length()").value(3))
                .andExpect(jsonPath("$.devices[0].credentialId").exists())
                .andExpect(jsonPath("$.devices[0].deviceName").exists())
                .andExpect(jsonPath("$.devices[0].deviceType").exists())
                .andExpect(jsonPath("$.devices[0].authenticatorType").exists())
                .andExpect(jsonPath("$.devices[0].createdAt").exists())
                .andExpect(jsonPath("$.devices[0].userVerified").exists());
    }

    @Test
    @DisplayName("Remove biometric device")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testRemoveBiometricDevice() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();
        String credentialId = registerMockBiometricCredential(userId, "Device to Remove");

        // ACT & ASSERT
        mockMvc.perform(delete("/api/v1/auth/webauthn/devices/{credentialId}", credentialId))
                .andExpect(status().isOk());

        // Verify device was removed
        var credential = credentialRepository.findByCredentialId(credentialId);
        assertThat(credential).isEmpty();
    }

    @Test
    @DisplayName("Update biometric device name")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testUpdateDeviceName() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();
        String credentialId = registerMockBiometricCredential(userId, "Old Name");

        BiometricDeviceUpdateRequest request = BiometricDeviceUpdateRequest.builder()
                .deviceName("New Device Name")
                .build();

        // ACT & ASSERT
        mockMvc.perform(patch("/api/v1/auth/webauthn/devices/{credentialId}", credentialId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk());

        // Verify name was updated
        var credential = credentialRepository.findByCredentialId(credentialId).orElseThrow();
        assertThat(credential.getDeviceName()).isEqualTo("New Device Name");
    }

    @Test
    @DisplayName("User cannot access another user's biometric devices")
    @WithMockUser(username = "user1@example.com", roles = {"USER"})
    void testUserCannotAccessOtherUsersDevices() throws Exception {
        // ARRANGE
        UUID user1Id = getCurrentUserId();
        UUID user2Id = createTestUser("User Two", "user2@example.com");

        String user2CredentialId = registerMockBiometricCredential(user2Id, "User 2 Device");

        // ACT & ASSERT - User 1 tries to delete User 2's device
        mockMvc.perform(delete("/api/v1/auth/webauthn/devices/{credentialId}", user2CredentialId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));

        // Verify device was not deleted
        var credential = credentialRepository.findByCredentialId(user2CredentialId);
        assertThat(credential).isPresent();
    }

    @Test
    @DisplayName("Sign counter must increment on each authentication")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testSignCounterIncrement() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();
        String credentialId = registerMockBiometricCredential(userId, "Counter Test");

        // Get initial sign count
        var credential1 = credentialRepository.findByCredentialId(credentialId).orElseThrow();
        long initialCount = credential1.getSignCount();

        // Authenticate
        BiometricAuthenticationRequest request = BiometricAuthenticationRequest.builder()
                .credentialId(credentialId)
                .clientDataJSON(generateMockClientDataJSON("webauthn.get"))
                .authenticatorData(generateMockAuthenticatorData())
                .signature(generateMockSignature())
                .build();

        biometricAuthService.authenticate(request);

        // ASSERT - Sign count should have incremented
        var credential2 = credentialRepository.findByCredentialId(credentialId).orElseThrow();
        assertThat(credential2.getSignCount()).isGreaterThan(initialCount);
    }

    @Test
    @DisplayName("Detect cloned credentials via sign counter anomaly")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testClonedCredentialDetection() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();
        String credentialId = registerMockBiometricCredential(userId, "Original Device");

        // Authenticate once to increment counter
        biometricAuthService.authenticate(createMockAuthRequest(credentialId, 1));

        // Create request with lower sign count (indicating cloned credential)
        BiometricAuthenticationRequest clonedRequest = createMockAuthRequest(credentialId, 0);

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/auth/webauthn/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(clonedRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Possible cloned credential detected"));
    }

    // Helper methods

    private String registerMockBiometricCredential(UUID userId, String deviceName) {
        String credentialId = generateBase64CredentialId();

        BiometricCredential credential = new BiometricCredential();
        credential.setId(UUID.randomUUID());
        credential.setUserId(userId);
        credential.setCredentialId(credentialId);
        credential.setPublicKey(generateMockPublicKey());
        credential.setDeviceName(deviceName);
        credential.setDeviceType("PLATFORM");
        credential.setAuthenticatorType("FINGERPRINT");
        credential.setUserVerified(true);
        credential.setSignCount(0L);
        credential.setCreatedAt(java.time.LocalDateTime.now());

        credentialRepository.save(credential);

        return credentialId;
    }

    private String generateBase64CredentialId() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().getBytes());
    }

    private String generateMockClientDataJSON(String type) {
        String clientData = String.format(
                "{\"type\":\"%s\",\"challenge\":\"test_challenge\",\"origin\":\"https://example.com\"}",
                type
        );
        return encodeBase64(clientData);
    }

    private String generateMockAttestationObject() {
        // Simplified mock attestation object
        return encodeBase64("mock_attestation_object_data");
    }

    private String generateMockAuthenticatorData() {
        // Simplified mock authenticator data
        return encodeBase64("mock_authenticator_data");
    }

    private String generateMockSignature() {
        // Simplified mock signature
        return encodeBase64("mock_signature_data");
    }

    private String generateMockPublicKey() {
        return encodeBase64("mock_public_key_data");
    }

    private String encodeBase64(String data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes());
    }

    private BiometricAuthenticationRequest createMockAuthRequest(String credentialId, int signCount) {
        return BiometricAuthenticationRequest.builder()
                .credentialId(credentialId)
                .clientDataJSON(generateMockClientDataJSON("webauthn.get"))
                .authenticatorData(generateMockAuthenticatorDataWithSignCount(signCount))
                .signature(generateMockSignature())
                .build();
    }

    private String generateMockAuthenticatorDataWithSignCount(int signCount) {
        // In real implementation, this would properly encode the sign count
        // For testing, we simplify
        return encodeBase64("mock_auth_data_sign_count_" + signCount);
    }
}
