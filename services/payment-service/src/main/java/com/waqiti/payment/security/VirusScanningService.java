package com.waqiti.payment.security;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Virus Scanning Service using ClamAV
 *
 * Integrates with ClamAV daemon (clamd) for real-time virus/malware scanning
 * of uploaded check images and documents.
 *
 * Security Features:
 * - Real-time virus/malware detection
 * - Socket-based ClamAV integration
 * - Circuit breaker for availability
 * - Comprehensive audit logging
 * - Metrics tracking
 *
 * Compliance:
 * - PCI-DSS Requirement 5.1 (Anti-virus software)
 * - SOC 2 malware protection controls
 * - GLBA data protection requirements
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirusScanningService {

    private final MeterRegistry meterRegistry;

    @Value("${clamav.host:localhost}")
    private String clamavHost;

    @Value("${clamav.port:3310}")
    private int clamavPort;

    @Value("${clamav.timeout:60000}")
    private int connectionTimeout;

    @Value("${clamav.enabled:true}")
    private boolean scanningEnabled;

    @Value("${clamav.chunk-size:2048}")
    private int chunkSize;

    private Counter virusDetectedCounter;
    private Counter scanSuccessCounter;
    private Counter scanFailureCounter;
    private Timer scanTimer;

    private static final String RESPONSE_OK = "stream: OK";
    private static final String RESPONSE_FOUND_SUFFIX = "FOUND";
    private static final int DEFAULT_CHUNK_SIZE = 2048;

    @PostConstruct
    public void init() {
        virusDetectedCounter = Counter.builder("virus_scan.detected")
                .description("Number of viruses detected")
                .tag("service", "check-deposit")
                .register(meterRegistry);

        scanSuccessCounter = Counter.builder("virus_scan.success")
                .description("Number of successful scans")
                .tag("service", "check-deposit")
                .register(meterRegistry);

        scanFailureCounter = Counter.builder("virus_scan.failure")
                .description("Number of failed scans")
                .tag("service", "check-deposit")
                .register(meterRegistry);

        scanTimer = Timer.builder("virus_scan.duration")
                .description("Virus scan duration")
                .tag("service", "check-deposit")
                .register(meterRegistry);

        log.info("Virus scanning service initialized. ClamAV: {}:{}, Enabled: {}",
                 clamavHost, clamavPort, scanningEnabled);
    }

    /**
     * Scan byte array for viruses
     */
    @CircuitBreaker(name = "clamav", fallbackMethod = "scanFallback")
    @Retry(name = "clamav")
    public ScanResult scan(byte[] data, String filename) {
        if (!scanningEnabled) {
            log.debug("Virus scanning is disabled, skipping scan for: {}", filename);
            return ScanResult.clean(filename);
        }

        return scanTimer.record(() -> {
            try {
                log.info("SECURITY: Scanning file for viruses: {} ({} bytes)", filename, data.length);

                String result = scanWithClamAV(data);

                if (result.contains(RESPONSE_FOUND_SUFFIX)) {
                    String virusName = extractVirusName(result);
                    virusDetectedCounter.increment();
                    log.warn("SECURITY ALERT: Virus detected in file: {} - Virus: {}", filename, virusName);
                    return ScanResult.infected(filename, virusName);
                } else if (result.contains(RESPONSE_OK)) {
                    scanSuccessCounter.increment();
                    log.debug("File scan clean: {}", filename);
                    return ScanResult.clean(filename);
                } else {
                    scanFailureCounter.increment();
                    log.error("Unexpected ClamAV response: {}", result);
                    return ScanResult.error(filename, "Unexpected scan response: " + result);
                }

            } catch (Exception e) {
                scanFailureCounter.increment();
                log.error("Virus scan failed for file: {}", filename, e);
                throw new VirusScanException("Virus scan failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Scan input stream for viruses
     */
    public ScanResult scan(InputStream inputStream, String filename) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int bytesRead;

        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }

        return scan(buffer.toByteArray(), filename);
    }

    /**
     * Ping ClamAV to check if it's available
     */
    public boolean ping() {
        if (!scanningEnabled) {
            return true;
        }

        try (Socket socket = new Socket(clamavHost, clamavPort)) {
            socket.setSoTimeout(connectionTimeout);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write("zPING\0".getBytes(StandardCharsets.UTF_8));
            out.flush();

            byte[] response = new byte[4];
            in.read(response);

            String result = new String(response, StandardCharsets.UTF_8);
            boolean available = result.equals("PONG");

            if (available) {
                log.debug("ClamAV is available");
            } else {
                log.warn("ClamAV ping failed. Response: {}", result);
            }

            return available;

        } catch (Exception e) {
            log.error("Failed to ping ClamAV at {}:{}", clamavHost, clamavPort, e);
            return false;
        }
    }

    /**
     * Scan data using ClamAV INSTREAM command
     */
    private String scanWithClamAV(byte[] data) throws IOException {
        try (Socket socket = new Socket(clamavHost, clamavPort)) {
            socket.setSoTimeout(connectionTimeout);

            try (OutputStream out = socket.getOutputStream();
                 InputStream in = socket.getInputStream()) {

                // Send INSTREAM command
                out.write("zINSTREAM\0".getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Stream data in chunks
                int offset = 0;
                while (offset < data.length) {
                    int length = Math.min(chunkSize, data.length - offset);

                    // Send chunk size (4 bytes, network byte order)
                    byte[] chunkLengthBytes = ByteBuffer.allocate(4).putInt(length).array();
                    out.write(chunkLengthBytes);

                    // Send chunk data
                    out.write(data, offset, length);
                    out.flush();

                    offset += length;
                }

                // Send zero-length chunk to signal end of data
                out.write(new byte[]{0, 0, 0, 0});
                out.flush();

                // Read response
                ByteArrayOutputStream response = new ByteArrayOutputStream();
                byte[] buffer = new byte[2048];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    response.write(buffer, 0, bytesRead);
                }

                return response.toString(StandardCharsets.UTF_8).trim();
            }
        }
    }

    /**
     * Extract virus name from ClamAV response
     */
    private String extractVirusName(String response) {
        // Response format: "stream: Eicar-Test-Signature FOUND"
        String[] parts = response.split(":");
        if (parts.length > 1) {
            String virusPart = parts[1].trim();
            return virusPart.replace(" FOUND", "").trim();
        }
        return "Unknown";
    }

    /**
     * Fallback method when ClamAV is unavailable
     */
    private ScanResult scanFallback(byte[] data, String filename, Exception e) {
        log.error("ClamAV circuit breaker open or scan failed. Rejecting file: {}", filename, e);
        scanFailureCounter.increment();

        // SECURITY: Fail closed - reject the file if we can't scan it
        return ScanResult.error(filename, "Virus scanning service unavailable");
    }

    /**
     * Scan result DTO
     */
    public static class ScanResult {
        private final String filename;
        private final boolean clean;
        private final String virusName;
        private final String errorMessage;

        private ScanResult(String filename, boolean clean, String virusName, String errorMessage) {
            this.filename = filename;
            this.clean = clean;
            this.virusName = virusName;
            this.errorMessage = errorMessage;
        }

        public static ScanResult clean(String filename) {
            return new ScanResult(filename, true, null, null);
        }

        public static ScanResult infected(String filename, String virusName) {
            return new ScanResult(filename, false, virusName, null);
        }

        public static ScanResult error(String filename, String errorMessage) {
            return new ScanResult(filename, false, null, errorMessage);
        }

        public boolean isClean() {
            return clean;
        }

        public boolean isInfected() {
            return !clean && virusName != null;
        }

        public boolean hasError() {
            return !clean && errorMessage != null;
        }

        public String getFilename() {
            return filename;
        }

        public String getVirusName() {
            return virusName;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            if (clean) {
                return String.format("ScanResult[%s: CLEAN]", filename);
            } else if (virusName != null) {
                return String.format("ScanResult[%s: INFECTED - %s]", filename, virusName);
            } else {
                return String.format("ScanResult[%s: ERROR - %s]", filename, errorMessage);
            }
        }
    }

    /**
     * Exception for virus scan failures
     */
    public static class VirusScanException extends RuntimeException {
        public VirusScanException(String message) {
            super(message);
        }

        public VirusScanException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
