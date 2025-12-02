package com.waqiti.payment.integration;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.entity.VoiceCommand;
import com.waqiti.payment.repository.VoiceCommandRepository;
import com.waqiti.payment.service.VoicePaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Voice Payment Integration Tests
 *
 * SCENARIOS TESTED:
 * 1. Parse voice command (NLP integration)
 * 2. Execute voice payment with biometric verification
 * 3. Voice command history tracking
 * 4. Multi-language support
 * 5. Voice biometric enrollment
 * 6. Voice biometric authentication
 * 7. Error handling for invalid commands
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
@DisplayName("Voice Payment Integration Tests")
class VoicePaymentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VoicePaymentService voicePaymentService;

    @Autowired
    private VoiceCommandRepository voiceCommandRepository;

    @Test
    @DisplayName("Parse voice command - Send money intent")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testParseVoiceCommand_SendMoney() throws Exception {
        // ARRANGE
        String transcript = "Send 50 dollars to John Smith for lunch";

        VoiceCommandParseRequest request = VoiceCommandParseRequest.builder()
                .transcript(transcript)
                .language("en-US")
                .build();

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/voice-payments/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("SEND_PAYMENT"))
                .andExpect(jsonPath("$.entities.amount").value(50.0))
                .andExpect(jsonPath("$.entities.currency").value("USD"))
                .andExpect(jsonPath("$.entities.recipient").value("John Smith"))
                .andExpect(jsonPath("$.entities.note").value("lunch"))
                .andExpect(jsonPath("$.confidence").exists())
                .andExpect(jsonPath("$.confidence").value(org.hamcrest.Matchers.greaterThan(0.7)));

        // Verify command was stored
        var commands = voiceCommandRepository.findByUserId(getCurrentUserId());
        assertThat(commands).isNotEmpty();
        assertThat(commands.get(0).getTranscript()).isEqualTo(transcript);
        assertThat(commands.get(0).getIntent()).isEqualTo("SEND_PAYMENT");
    }

    @Test
    @DisplayName("Parse voice command - Check balance intent")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testParseVoiceCommand_CheckBalance() throws Exception {
        // ARRANGE
        String transcript = "What is my current balance";

        VoiceCommandParseRequest request = VoiceCommandParseRequest.builder()
                .transcript(transcript)
                .language("en-US")
                .build();

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/voice-payments/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CHECK_BALANCE"))
                .andExpect(jsonPath("$.confidence").exists());
    }

    @Test
    @DisplayName("Execute voice payment successfully")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testExecuteVoicePayment_Success() throws Exception {
        // ARRANGE
        UUID customerId = createTestCustomer("Alice Johnson", "alice@example.com");
        UUID recipientId = createTestCustomer("Bob Williams", "bob@example.com");
        UUID walletId = createTestWallet(customerId, new BigDecimal("500.00"), "USD");

        MockMultipartFile voiceSample = new MockMultipartFile(
                "voiceSample",
                "voice.wav",
                "audio/wav",
                "mock audio data".getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart("/api/v1/voice-payments/execute")
                        .file(voiceSample)
                        .param("recipientId", recipientId.toString())
                        .param("amount", "50.00")
                        .param("currency", "USD")
                        .param("note", "Voice payment test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.voiceVerified").value(true))
                .andExpect(jsonPath("$.timestamp").exists());

        // Verify wallet was debited
        var balance = walletService.getBalance(walletId);
        assertThat(balance.getAvailable()).isEqualByComparingTo(new BigDecimal("450.00"));

        // Verify voice command was recorded
        var commands = voiceCommandRepository.findByUserId(customerId);
        assertThat(commands).isNotEmpty();
        assertThat(commands.get(0).getSuccess()).isTrue();
    }

    @Test
    @DisplayName("Voice payment fails with insufficient funds")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testExecuteVoicePayment_InsufficientFunds() throws Exception {
        // ARRANGE
        UUID customerId = createTestCustomer("Charlie Brown", "charlie@example.com");
        UUID recipientId = createTestCustomer("Lucy Van Pelt", "lucy@example.com");
        UUID walletId = createTestWallet(customerId, new BigDecimal("30.00"), "USD");

        MockMultipartFile voiceSample = new MockMultipartFile(
                "voiceSample",
                "voice.wav",
                "audio/wav",
                "mock audio data".getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart("/api/v1/voice-payments/execute")
                        .file(voiceSample)
                        .param("recipientId", recipientId.toString())
                        .param("amount", "50.00")
                        .param("currency", "USD"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("Insufficient funds"))
                .andExpect(jsonPath("$.available").value(30.00))
                .andExpect(jsonPath("$.required").value(50.00));

        // Verify wallet balance unchanged
        var balance = walletService.getBalance(walletId);
        assertThat(balance.getAvailable()).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("Voice biometric enrollment")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testVoiceBiometricEnrollment() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();

        MockMultipartFile sample1 = new MockMultipartFile(
                "samples",
                "voice1.wav",
                "audio/wav",
                "sample 1 data".getBytes()
        );

        MockMultipartFile sample2 = new MockMultipartFile(
                "samples",
                "voice2.wav",
                "audio/wav",
                "sample 2 data".getBytes()
        );

        MockMultipartFile sample3 = new MockMultipartFile(
                "samples",
                "voice3.wav",
                "audio/wav",
                "sample 3 data".getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart("/api/v1/voice-payments/biometric/enroll")
                        .file(sample1)
                        .file(sample2)
                        .file(sample3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentId").exists())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.samplesRequired").value(3))
                .andExpect(jsonPath("$.samplesCollected").value(3))
                .andExpect(jsonPath("$.quality").exists())
                .andExpect(jsonPath("$.quality").value(org.hamcrest.Matchers.greaterThan(0.7)));

        // Verify enrollment was stored
        var enrollment = voicePaymentService.getBiometricEnrollment(userId);
        assertThat(enrollment).isNotNull();
        assertThat(enrollment.isActive()).isTrue();
    }

    @Test
    @DisplayName("Voice biometric verification")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testVoiceBiometricVerification() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();

        // Enroll first
        enrollVoiceBiometric(userId);

        MockMultipartFile voiceSample = new MockMultipartFile(
                "voiceSample",
                "verify.wav",
                "audio/wav",
                "verification sample".getBytes()
        );

        // ACT & ASSERT
        mockMvc.perform(multipart("/api/v1/voice-payments/biometric/verify")
                        .file(voiceSample))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.confidence").exists())
                .andExpect(jsonPath("$.confidence").value(org.hamcrest.Matchers.greaterThan(0.8)));
    }

    @Test
    @DisplayName("Voice biometric verification fails with low confidence")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testVoiceBiometricVerification_LowConfidence() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();
        enrollVoiceBiometric(userId);

        // Create deliberately poor quality sample
        MockMultipartFile poorQualitySample = new MockMultipartFile(
                "voiceSample",
                "poor.wav",
                "audio/wav",
                new byte[]{0, 1, 2, 3} // Garbage data
        );

        // ACT & ASSERT
        mockMvc.perform(multipart("/api/v1/voice-payments/biometric/verify")
                        .file(poorQualitySample))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.confidence").value(org.hamcrest.Matchers.lessThan(0.7)))
                .andExpect(jsonPath("$.error").value("Voice verification failed"));
    }

    @Test
    @DisplayName("Get voice command history")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testGetVoiceCommandHistory() throws Exception {
        // ARRANGE
        UUID userId = getCurrentUserId();

        // Create some voice commands
        createVoiceCommand(userId, "Send 20 dollars to Alice", "SEND_PAYMENT", true);
        createVoiceCommand(userId, "Check my balance", "CHECK_BALANCE", true);
        createVoiceCommand(userId, "Show recent transactions", "TRANSACTION_HISTORY", true);

        // ACT & ASSERT
        mockMvc.perform(get("/api/v1/voice-payments/history")
                        .param("page", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commands").isArray())
                .andExpect(jsonPath("$.commands.length()").value(3))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.commands[0].transcript").exists())
                .andExpect(jsonPath("$.commands[0].intent").exists())
                .andExpect(jsonPath("$.commands[0].success").value(true));
    }

    @Test
    @DisplayName("Multi-language voice command parsing - Spanish")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testParseVoiceCommand_Spanish() throws Exception {
        // ARRANGE
        String transcript = "Enviar 100 dólares a María García";

        VoiceCommandParseRequest request = VoiceCommandParseRequest.builder()
                .transcript(transcript)
                .language("es-ES")
                .build();

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/voice-payments/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("SEND_PAYMENT"))
                .andExpect(jsonPath("$.entities.amount").value(100.0))
                .andExpect(jsonPath("$.entities.recipient").value("María García"));
    }

    @Test
    @DisplayName("Parse invalid voice command")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testParseVoiceCommand_Invalid() throws Exception {
        // ARRANGE
        String transcript = "Random gibberish that makes no sense for payments";

        VoiceCommandParseRequest request = VoiceCommandParseRequest.builder()
                .transcript(transcript)
                .language("en-US")
                .build();

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/voice-payments/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("UNKNOWN"))
                .andExpect(jsonPath("$.confidence").value(org.hamcrest.Matchers.lessThan(0.5)));
    }

    @Test
    @DisplayName("Rate limiting prevents voice command spam")
    @WithMockUser(username = "user@example.com", roles = {"USER"})
    void testVoiceCommandRateLimiting() throws Exception {
        // ARRANGE
        String transcript = "Send 10 dollars to Alice";
        VoiceCommandParseRequest request = VoiceCommandParseRequest.builder()
                .transcript(transcript)
                .language("en-US")
                .build();

        // ACT - Send 6 requests rapidly (limit is 5/minute)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/voice-payments/parse")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isOk());
        }

        // ASSERT - 6th request should be rate limited
        mockMvc.perform(post("/api/v1/voice-payments/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.retryAfter").exists());
    }

    // Helper methods

    private void enrollVoiceBiometric(UUID userId) {
        // Mock enrollment implementation
        voicePaymentService.enrollBiometric(userId, new byte[]{1, 2, 3}, new byte[]{4, 5, 6}, new byte[]{7, 8, 9});
    }

    private void createVoiceCommand(UUID userId, String transcript, String intent, boolean success) {
        VoiceCommand command = new VoiceCommand();
        command.setId(UUID.randomUUID());
        command.setUserId(userId);
        command.setTranscript(transcript);
        command.setIntent(intent);
        command.setSuccess(success);
        command.setTimestamp(java.time.LocalDateTime.now());
        voiceCommandRepository.save(command);
    }
}
