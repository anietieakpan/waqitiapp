package com.waqiti.voice.controller;

import com.waqiti.voice.dto.*;
import com.waqiti.voice.security.validation.AudioFileSecurityService;
import com.waqiti.voice.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/voice-payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Voice Payments", description = "Voice-activated payment operations")
public class VoicePaymentController {

    private final VoiceRecognitionService voiceRecognitionService;
    private final VoiceEnrollmentService voiceEnrollmentService;
    private final VoiceProfileService voiceProfileService;
    private final AudioFileSecurityService audioFileSecurityService;
    
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Process voice payment command",
              description = "Upload audio file and process voice payment command")
    @ApiResponse(responseCode = "200", description = "Voice command processed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid audio file or command")
    @ApiResponse(responseCode = "401", description = "Voice authentication failed")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VoiceCommandResponse> processVoiceCommand(
            @Parameter(description = "User ID") @RequestParam UUID userId,
            @Parameter(description = "Audio file containing voice command") @RequestParam("audio") MultipartFile audioFile,
            @Parameter(description = "Language code (e.g., en-US)") @RequestParam(defaultValue = "en-US") String language,
            @Parameter(description = "Device information") @RequestParam(required = false) String deviceInfo,
            @Parameter(description = "User location") @RequestParam(required = false) String location,
            @Parameter(description = "Ambient noise level") @RequestParam(required = false) Double ambientNoiseLevel) {
        
        log.info("Processing voice command for user: {}", userId);

        try {
            // Validate audio file
            if (audioFile.isEmpty() || audioFile.getSize() == 0) {
                return ResponseEntity.badRequest()
                        .body(VoiceCommandResponse.error("Audio file is required"));
            }

            // CRITICAL SECURITY: Validate file with magic bytes + virus scan
            AudioFileSecurityService.AudioValidationResult validation =
                    audioFileSecurityService.validateAudioFile(audioFile);

            if (!validation.isValid()) {
                if (validation.isVirusDetected()) {
                    log.error("SECURITY ALERT: Malware detected in audio upload from user: {}, virus: {}",
                            userId, validation.getVirusName());
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(VoiceCommandResponse.error("File rejected: Security threat detected"));
                }
                return ResponseEntity.badRequest()
                        .body(VoiceCommandResponse.error(validation.getErrorMessage()));
            }
            
            VoiceCommandRequest request = VoiceCommandRequest.builder()
                    .language(language)
                    .deviceInfo(deviceInfo)
                    .location(location)
                    .ambientNoiseLevel(ambientNoiseLevel)
                    .build();
            
            VoiceCommandResponse response = voiceRecognitionService.processVoiceCommand(
                    userId, audioFile, request);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing voice command for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(VoiceCommandResponse.error("Internal server error"));
        }
    }
    
    @PostMapping("/confirm")
    @Operation(summary = "Confirm voice payment command",
              description = "Confirm or deny a voice payment command that requires confirmation")
    @ApiResponse(responseCode = "200", description = "Command confirmation processed")
    @ApiResponse(responseCode = "404", description = "Command session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VoiceCommandResponse> confirmVoiceCommand(
            @Parameter(description = "User ID") @RequestParam UUID userId,
            @Parameter(description = "Command session ID") @RequestParam String sessionId,
            @Parameter(description = "Whether to confirm the command") @RequestParam boolean confirmed) {
        
        log.info("Confirming voice command for user: {} session: {} confirmed: {}", 
                userId, sessionId, confirmed);
        
        try {
            VoiceCommandResponse response = voiceRecognitionService.confirmVoiceCommand(
                    userId, sessionId, confirmed);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error confirming voice command for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(VoiceCommandResponse.error("Internal server error"));
        }
    }
    
    @GetMapping("/status/{sessionId}")
    @Operation(summary = "Get voice command status",
              description = "Get the current status of a voice command")
    @ApiResponse(responseCode = "200", description = "Command status retrieved")
    @ApiResponse(responseCode = "404", description = "Command session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VoiceCommandStatusDto> getCommandStatus(
            @Parameter(description = "User ID") @RequestParam UUID userId,
            @Parameter(description = "Command session ID") @PathVariable String sessionId) {
        
        log.debug("Getting command status for user: {} session: {}", userId, sessionId);
        
        try {
            VoiceCommandStatusDto status = voiceRecognitionService.getCommandStatus(userId, sessionId);
            
            if (status.getSessionId() == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error getting command status for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/response/{sessionId}")
    @Operation(summary = "Get voice response audio",
              description = "Get generated voice response for a command")
    @ApiResponse(responseCode = "200", description = "Voice response URL returned")
    @ApiResponse(responseCode = "404", description = "Response not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VoiceResponseDto> getVoiceResponse(
            @Parameter(description = "User ID") @RequestParam UUID userId,
            @Parameter(description = "Command session ID") @PathVariable String sessionId,
            @Parameter(description = "Language for response") @RequestParam(defaultValue = "en-US") String language) {
        
        log.debug("Getting voice response for user: {} session: {}", userId, sessionId);
        
        try {
            // Get the command response first
            VoiceCommandStatusDto status = voiceRecognitionService.getCommandStatus(userId, sessionId);
            
            if (status.getSessionId() == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Generate voice response if command is completed
            if (status.getStatus().name().equals("COMPLETED") || status.getStatus().name().equals("FAILED")) {
                VoiceCommandResponse commandResponse = VoiceCommandResponse.builder()
                        .success(status.getStatus().name().equals("COMPLETED"))
                        .message(status.getErrorMessage() != null ? status.getErrorMessage() : "Command completed")
                        .sessionId(sessionId)
                        .build();
                
                String audioUrl = voiceRecognitionService.generateVoiceResponse(commandResponse, language);
                
                VoiceResponseDto responseDto = VoiceResponseDto.builder()
                        .sessionId(sessionId)
                        .audioUrl(audioUrl)
                        .language(language)
                        .generatedAt(java.time.LocalDateTime.now())
                        .build();
                
                return ResponseEntity.ok(responseDto);
            } else {
                return ResponseEntity.accepted().build(); // Command still processing
            }
            
        } catch (Exception e) {
            log.error("Error generating voice response for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Voice Profile Management Endpoints
    
    @PostMapping("/profile/enroll")
    @Operation(summary = "Start voice profile enrollment",
              description = "Begin the voice biometric enrollment process")
    @ApiResponse(responseCode = "201", description = "Enrollment started")
    @ApiResponse(responseCode = "400", description = "Invalid enrollment request")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VoiceEnrollmentResponse> startVoiceEnrollment(
            @Parameter(description = "User ID") @RequestParam UUID userId,
            @Valid @RequestBody VoiceEnrollmentRequest request) {
        
        log.info("Starting voice enrollment for user: {}", userId);
        
        try {
            VoiceEnrollmentResponse response = voiceEnrollmentService.startEnrollment(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Error starting voice enrollment for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(VoiceEnrollmentResponse.error("Failed to start enrollment"));
        }
    }
    
    @PostMapping(value = "/profile/enroll/sample", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit voice enrollment sample",
              description = "Upload a voice sample for biometric enrollment")
    @ApiResponse(responseCode = "200", description = "Voice sample processed")
    @ApiResponse(responseCode = "400", description = "Invalid voice sample")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VoiceEnrollmentResponse> submitVoiceSample(
            @Parameter(description = "User ID") @RequestParam UUID userId,
            @Parameter(description = "Voice sample audio file") @RequestParam("audio") MultipartFile audioFile,
            @Parameter(description = "Sample phrase text") @RequestParam String phraseText,
            @Parameter(description = "Language code") @RequestParam(defaultValue = "en-US") String language) {
        
        log.info("Submitting voice sample for user: {}", userId);

        try {
            if (audioFile.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(VoiceEnrollmentResponse.error("Audio file is required"));
            }

            // CRITICAL SECURITY: Validate enrollment sample with magic bytes + virus scan
            AudioFileSecurityService.AudioValidationResult validation =
                    audioFileSecurityService.validateAudioFile(audioFile);

            if (!validation.isValid()) {
                if (validation.isVirusDetected()) {
                    log.error("SECURITY ALERT: Malware detected in enrollment sample from user: {}, virus: {}",
                            userId, validation.getVirusName());
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(VoiceEnrollmentResponse.error("File rejected: Security threat detected"));
                }
                return ResponseEntity.badRequest()
                        .body(VoiceEnrollmentResponse.error(validation.getErrorMessage()));
            }
            
            VoiceSampleRequest request = VoiceSampleRequest.builder()
                    .audioFile(audioFile)
                    .phraseText(phraseText)
                    .language(language)
                    .build();
            
            VoiceEnrollmentResponse response = voiceEnrollmentService.submitVoiceSample(userId, request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error submitting voice sample for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(VoiceEnrollmentResponse.error("Failed to process voice sample"));
        }
    }
    
    @PostMapping("/profile/enroll/complete")
    @Operation(summary = "Complete voice profile enrollment",
              description = "Finalize the voice biometric enrollment process")
    @ApiResponse(responseCode = "200", description = "Enrollment completed")
    @ApiResponse(responseCode = "400", description = "Enrollment cannot be completed")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VoiceEnrollmentResponse> completeVoiceEnrollment(
            @Parameter(description = "User ID") @RequestParam UUID userId) {
        
        log.info("Completing voice enrollment for user: {}", userId);
        
        try {
            VoiceEnrollmentResponse response = voiceEnrollmentService.completeEnrollment(userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error completing voice enrollment for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(VoiceEnrollmentResponse.error("Failed to complete enrollment"));
        }
    }
    
    @GetMapping("/profile")
    @Operation(summary = "Get voice profile",
              description = "Get user's voice profile information")
    @ApiResponse(responseCode = "200", description = "Voice profile retrieved")
    @ApiResponse(responseCode = "404", description = "Voice profile not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VoiceProfileDto> getVoiceProfile(
            @Parameter(description = "User ID") @RequestParam UUID userId) {
        
        log.debug("Getting voice profile for user: {}", userId);
        
        try {
            VoiceProfileDto profile = voiceProfileService.getVoiceProfile(userId);
            
            if (profile == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(profile);
            
        } catch (Exception e) {
            log.error("Error getting voice profile for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/profile/settings")
    @Operation(summary = "Update voice profile settings",
              description = "Update voice profile preferences and settings")
    @ApiResponse(responseCode = "200", description = "Settings updated")
    @ApiResponse(responseCode = "404", description = "Voice profile not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VoiceProfileDto> updateVoiceSettings(
            @Parameter(description = "User ID") @RequestParam UUID userId,
            @Valid @RequestBody VoiceSettingsRequest request) {
        
        log.info("Updating voice settings for user: {}", userId);
        
        try {
            VoiceProfileDto profile = voiceProfileService.updateVoiceSettings(userId, request);
            
            if (profile == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(profile);
            
        } catch (Exception e) {
            log.error("Error updating voice settings for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/profile")
    @Operation(summary = "Delete voice profile",
              description = "Delete user's voice profile and biometric data")
    @ApiResponse(responseCode = "204", description = "Voice profile deleted")
    @ApiResponse(responseCode = "404", description = "Voice profile not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteVoiceProfile(
            @Parameter(description = "User ID") @RequestParam UUID userId) {
        
        log.info("Deleting voice profile for user: {}", userId);
        
        try {
            boolean deleted = voiceProfileService.deleteVoiceProfile(userId);
            
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.noContent().build();
            
        } catch (Exception e) {
            log.error("Error deleting voice profile for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/commands/history")
    @Operation(summary = "Get voice command history",
              description = "Get user's voice command history")
    @ApiResponse(responseCode = "200", description = "Command history retrieved")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<VoiceCommandHistoryDto>> getCommandHistory(
            @Parameter(description = "User ID") @RequestParam UUID userId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        
        log.debug("Getting voice command history for user: {}", userId);
        
        try {
            List<VoiceCommandHistoryDto> history = voiceProfileService.getCommandHistory(userId, page, size);
            return ResponseEntity.ok(history);
            
        } catch (Exception e) {
            log.error("Error getting command history for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/analytics")
    @Operation(summary = "Get voice payment analytics",
              description = "Get analytics for voice payment usage")
    @ApiResponse(responseCode = "200", description = "Analytics retrieved")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VoiceAnalyticsDto> getVoiceAnalytics(
            @Parameter(description = "User ID") @RequestParam UUID userId,
            @Parameter(description = "Analytics period in days") @RequestParam(defaultValue = "30") int days) {
        
        log.debug("Getting voice analytics for user: {}", userId);
        
        try {
            VoiceAnalyticsDto analytics = voiceProfileService.getVoiceAnalytics(userId, days);
            return ResponseEntity.ok(analytics);
            
        } catch (Exception e) {
            log.error("Error getting voice analytics for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
}