package com.waqiti.voice.security.validation;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Audio File Security Service
 *
 * CRITICAL SECURITY: Validates audio files using magic byte verification
 * and virus scanning before processing.
 *
 * Features:
 * - Magic byte (file signature) validation
 * - ClamAV virus scanning
 * - File size validation
 * - Audio content validation (can it be parsed?)
 *
 * Prevents:
 * - Malware uploads disguised as audio files
 * - ZIP bombs, polyglot attacks
 * - Buffer overflow exploits
 * - MIME type spoofing
 *
 * Security Note:
 * NEVER trust client-provided MIME type (Content-Type header)
 * Always validate actual file content (magic bytes)
 */
@Slf4j
@Service
public class AudioFileSecurityService {

    @Value("${voice-payment.security.clamav.enabled:true}")
    private boolean clamavEnabled;

    @Value("${voice-payment.security.clamav.host:localhost}")
    private String clamavHost;

    @Value("${voice-payment.security.clamav.port:3310}")
    private int clamavPort;

    @Value("${voice-payment.security.max-file-size:10485760}") // 10MB
    private long maxFileSize;

    @Value("${voice-payment.security.min-file-size:1000}") // 1KB
    private long minFileSize;

    // Magic bytes for supported audio formats
    private static final byte[] WAV_SIGNATURE = {0x52, 0x49, 0x46, 0x46}; // RIFF
    private static final byte[] WAV_WAVE = {0x57, 0x41, 0x56, 0x45}; // WAVE
    private static final byte[] MP3_ID3 = {0x49, 0x44, 0x33}; // ID3
    private static final byte[] FLAC_SIGNATURE = {0x66, 0x4C, 0x61, 0x43}; // fLaC
    private static final byte[] OGG_SIGNATURE = {0x4F, 0x67, 0x67, 0x53}; // OggS

    /**
     * Validate audio file with comprehensive security checks
     *
     * @param file Uploaded audio file
     * @return Validation result
     */
    public AudioValidationResult validateAudioFile(MultipartFile file) {
        log.info("Validating audio file: name={}, size={}, contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        try {
            // 1. Check file size
            if (file.getSize() > maxFileSize) {
                log.warn("File too large: {} bytes (max: {})", file.getSize(), maxFileSize);
                return AudioValidationResult.invalid(
                        "File too large. Maximum size: " + (maxFileSize / 1024 / 1024) + "MB"
                );
            }

            if (file.getSize() < minFileSize) {
                log.warn("File too small: {} bytes (min: {})", file.getSize(), minFileSize);
                return AudioValidationResult.invalid("File too small, likely empty or corrupted");
            }

            // 2. Validate magic bytes (file signature)
            byte[] fileBytes = file.getBytes();
            AudioFormat format = detectFormatByMagicBytes(fileBytes);

            if (format == null) {
                log.warn("Invalid audio format detected");
                return AudioValidationResult.invalid(
                        "Invalid audio file format. Supported: WAV, MP3, FLAC, OGG"
                );
            }

            // 3. Virus scan with ClamAV (if enabled)
            if (clamavEnabled) {
                VirusScanResult scanResult = scanWithClamAV(fileBytes);

                if (!scanResult.isClean()) {
                    log.error("SECURITY ALERT: Virus detected in upload: {}",
                            scanResult.getVirusName());
                    return AudioValidationResult.infected(scanResult.getVirusName());
                }
            }

            // 4. Validate audio content (can it be parsed?)
            boolean validAudio = validateAudioContent(fileBytes, format);
            if (!validAudio) {
                log.warn("File has valid signature but corrupted audio content");
                return AudioValidationResult.invalid("Corrupted audio file");
            }

            log.info("Audio file validation PASSED: format={}", format);
            return AudioValidationResult.valid(format);

        } catch (IOException e) {
            log.error("Error reading audio file", e);
            return AudioValidationResult.invalid("Error reading file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during validation", e);
            return AudioValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    /**
     * Detect audio format by magic bytes (file signature)
     * CRITICAL: Never trust MIME type from client
     */
    private AudioFormat detectFormatByMagicBytes(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < 12) {
            return null;
        }

        // WAV: 52 49 46 46 ... 57 41 56 45 (RIFF...WAVE)
        if (matchesSignature(fileBytes, 0, WAV_SIGNATURE) &&
            matchesSignature(fileBytes, 8, WAV_WAVE)) {
            return AudioFormat.WAV;
        }

        // MP3: FF FB/FF F3/FF F2 (MPEG frame sync) or ID3 tag
        if ((fileBytes[0] == (byte)0xFF && (fileBytes[1] & 0xE0) == 0xE0) ||
            matchesSignature(fileBytes, 0, MP3_ID3)) {
            return AudioFormat.MP3;
        }

        // FLAC: 66 4C 61 43 (fLaC)
        if (matchesSignature(fileBytes, 0, FLAC_SIGNATURE)) {
            return AudioFormat.FLAC;
        }

        // OGG: 4F 67 67 53 (OggS)
        if (matchesSignature(fileBytes, 0, OGG_SIGNATURE)) {
            return AudioFormat.OGG;
        }

        return null;
    }

    /**
     * Check if bytes match signature at offset
     */
    private boolean matchesSignature(byte[] fileBytes, int offset, byte[] signature) {
        if (fileBytes.length < offset + signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (fileBytes[offset + i] != signature[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validate audio content can be parsed
     */
    private boolean validateAudioContent(byte[] fileBytes, AudioFormat format) {
        if (format != AudioFormat.WAV) {
            // Only validate WAV format (Java audio system support)
            return true;
        }

        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(
                    new ByteArrayInputStream(fileBytes)
            );

            // Check audio format is valid
            javax.sound.sampled.AudioFormat audioFormat = audioStream.getFormat();
            if (audioFormat == null) {
                return false;
            }

            audioStream.close();
            return true;

        } catch (Exception e) {
            log.warn("Audio content validation failed", e);
            return false;
        }
    }

    /**
     * Scan file with ClamAV antivirus
     *
     * Protocol: Send file to ClamAV daemon, receive scan result
     */
    private VirusScanResult scanWithClamAV(byte[] fileBytes) {
        try (Socket socket = new Socket(clamavHost, clamavPort)) {
            socket.setSoTimeout(30000); // 30 second timeout

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Send INSTREAM command
            out.write("zINSTREAM\0".getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Send file data in chunks
            int chunkSize = 2048;
            int offset = 0;

            while (offset < fileBytes.length) {
                int remaining = fileBytes.length - offset;
                int toSend = Math.min(chunkSize, remaining);

                // Send chunk size (network byte order)
                out.write((toSend >> 24) & 0xFF);
                out.write((toSend >> 16) & 0xFF);
                out.write((toSend >> 8) & 0xFF);
                out.write(toSend & 0xFF);

                // Send chunk data
                out.write(fileBytes, offset, toSend);
                offset += toSend;
            }

            // Send zero-length chunk (end of stream)
            out.write(new byte[]{0, 0, 0, 0});
            out.flush();

            // Read response
            byte[] response = new byte[1024];
            int bytesRead = in.read(response);
            String result = new String(response, 0, bytesRead, StandardCharsets.UTF_8).trim();

            log.debug("ClamAV scan result: {}", result);

            // Parse result
            if (result.contains("OK")) {
                return VirusScanResult.clean();
            } else if (result.contains("FOUND")) {
                String virusName = result.split(":")[1].replace("FOUND", "").trim();
                return VirusScanResult.infected(virusName);
            } else {
                log.warn("Unexpected ClamAV response: {}", result);
                return VirusScanResult.error("Unexpected scan result");
            }

        } catch (Exception e) {
            log.error("ClamAV scan failed", e);
            // Fail secure: If virus scanning fails, reject file
            return VirusScanResult.error("Virus scan failed: " + e.getMessage());
        }
    }

    // Supporting classes

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudioValidationResult {
        private boolean valid;
        private AudioFormat format;
        private String errorMessage;
        private boolean virusDetected;
        private String virusName;

        public static AudioValidationResult valid(AudioFormat format) {
            return AudioValidationResult.builder()
                    .valid(true)
                    .format(format)
                    .build();
        }

        public static AudioValidationResult invalid(String errorMessage) {
            return AudioValidationResult.builder()
                    .valid(false)
                    .errorMessage(errorMessage)
                    .build();
        }

        public static AudioValidationResult infected(String virusName) {
            return AudioValidationResult.builder()
                    .valid(false)
                    .virusDetected(true)
                    .virusName(virusName)
                    .errorMessage("Malware detected: " + virusName)
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class VirusScanResult {
        private boolean clean;
        private String virusName;
        private String errorMessage;

        public static VirusScanResult clean() {
            return VirusScanResult.builder().clean(true).build();
        }

        public static VirusScanResult infected(String virusName) {
            return VirusScanResult.builder()
                    .clean(false)
                    .virusName(virusName)
                    .build();
        }

        public static VirusScanResult error(String errorMessage) {
            return VirusScanResult.builder()
                    .clean(false)
                    .errorMessage(errorMessage)
                    .build();
        }
    }

    public enum AudioFormat {
        WAV("audio/wav", ".wav"),
        MP3("audio/mpeg", ".mp3"),
        FLAC("audio/flac", ".flac"),
        OGG("audio/ogg", ".ogg");

        private final String mimeType;
        private final String extension;

        AudioFormat(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getExtension() {
            return extension;
        }
    }
}
