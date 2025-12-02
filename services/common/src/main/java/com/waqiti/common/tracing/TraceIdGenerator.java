package com.waqiti.common.tracing;

import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Trace ID Generator for Waqiti Platform
 *
 * Generates unique trace and span IDs with the following characteristics:
 * - W3C Trace Context compliant (128-bit trace IDs, 64-bit span IDs)
 * - Includes timestamp information for time-based analysis
 * - High entropy for global uniqueness across distributed systems
 * - Correlation ID integration for business-level tracing
 * - Performance optimized with ThreadLocalRandom and atomic counters
 * - Monotonically increasing sequence numbers for ordering
 *
 * Format:
 * - Trace ID (32 hex chars / 128 bits): [timestamp(8)][sequence(4)][random(20)]
 * - Span ID (16 hex chars / 64 bits): [timestamp(4)][sequence(2)][random(10)]
 * - Correlation ID: wqt-[32 hex chars]
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 1.0
 */
@Slf4j
@Component
public class TraceIdGenerator {

    private static final String CORRELATION_ID_PREFIX = "wqt";
    private static final int TRACE_ID_LENGTH = 32; // 128 bits in hex
    private static final int SPAN_ID_LENGTH = 16;  // 64 bits in hex

    // Atomic counter for sequence numbers (wraps at Long.MAX_VALUE)
    private final AtomicLong traceSequence = new AtomicLong(0);
    private final AtomicLong spanSequence = new AtomicLong(0);

    // Secure random for cryptographically strong random numbers
    private final SecureRandom secureRandom;

    // Node identifier for distributed uniqueness (derived from hostname or generated)
    private final long nodeId;

    /**
     * Constructor - initializes secure random and node identifier
     */
    public TraceIdGenerator() {
        this.secureRandom = new SecureRandom();
        this.nodeId = generateNodeId();

        log.info("TraceIdGenerator initialized with nodeId: {}", String.format("%016x", nodeId));
    }

    /**
     * Generate a new W3C-compliant 128-bit trace ID
     *
     * Format: [timestamp(32 bits)][sequence(16 bits)][node(16 bits)][random(64 bits)]
     * This ensures:
     * - Time-ordered IDs for better database indexing
     * - Uniqueness through sequence + node + random
     * - W3C Trace Context compliance
     *
     * @return 32-character hex string representing 128-bit trace ID
     */
    public String generateTraceId() {
        // Current timestamp in seconds (32 bits)
        long timestampSec = Instant.now().getEpochSecond();

        // Get next sequence number (16 bits)
        long sequence = traceSequence.incrementAndGet() & 0xFFFF;

        // Node identifier (16 bits)
        long node = nodeId & 0xFFFF;

        // Random component (64 bits) for high entropy
        long random = ThreadLocalRandom.current().nextLong();

        // Build 128-bit trace ID
        // High 64 bits: [timestamp(32)][sequence(16)][node(16)]
        long high = (timestampSec << 32) | (sequence << 16) | node;

        // Low 64 bits: [random(64)]
        long low = random;

        // Convert to 32-character hex string
        String traceId = String.format("%016x%016x", high, low);

        log.debug("Generated trace ID: {} (timestamp={}, sequence={}, node={})",
                traceId, timestampSec, sequence, node);

        return traceId;
    }

    /**
     * Generate a new W3C-compliant 64-bit span ID
     *
     * Format: [timestamp(16 bits)][sequence(16 bits)][random(32 bits)]
     *
     * @return 16-character hex string representing 64-bit span ID
     */
    public String generateSpanId() {
        // Current timestamp in milliseconds, truncated to 16 bits
        long timestampMs = System.currentTimeMillis() & 0xFFFF;

        // Get next sequence number (16 bits)
        long sequence = spanSequence.incrementAndGet() & 0xFFFF;

        // Random component (32 bits)
        long random = ThreadLocalRandom.current().nextInt() & 0xFFFFFFFFL;

        // Build 64-bit span ID: [timestamp(16)][sequence(16)][random(32)]
        long spanId = (timestampMs << 48) | (sequence << 32) | random;

        String spanIdHex = String.format("%016x", spanId);

        log.debug("Generated span ID: {} (timestamp={}, sequence={})",
                spanIdHex, timestampMs, sequence);

        return spanIdHex;
    }

    /**
     * Generate a Waqiti correlation ID
     *
     * Format: wqt-[32 hex characters]
     * Based on UUID v4 with dashes removed
     *
     * @return Waqiti correlation ID
     */
    public String generateCorrelationId() {
        UUID uuid = UUID.randomUUID();
        String uuidHex = uuid.toString().replace("-", "");
        String correlationId = CORRELATION_ID_PREFIX + "-" + uuidHex;

        log.debug("Generated correlation ID: {}", correlationId);

        return correlationId;
    }

    /**
     * Generate a correlation ID from an existing trace ID
     * This maintains the relationship between trace ID and correlation ID
     *
     * @param traceId existing trace ID
     * @return correlation ID derived from trace ID
     */
    public String generateCorrelationIdFromTraceId(String traceId) {
        if (traceId == null || traceId.length() != TRACE_ID_LENGTH) {
            log.warn("Invalid trace ID provided: {}, generating new correlation ID", traceId);
            return generateCorrelationId();
        }

        // Use the trace ID as the correlation ID suffix
        String correlationId = CORRELATION_ID_PREFIX + "-" + traceId;

        log.debug("Generated correlation ID from trace ID: {} -> {}", traceId, correlationId);

        return correlationId;
    }

    /**
     * Extract timestamp from a trace ID (if it was generated by this generator)
     *
     * @param traceId trace ID to extract timestamp from
     * @return epoch seconds, or -1 if invalid
     */
    public long extractTimestampFromTraceId(String traceId) {
        if (traceId == null || traceId.length() != TRACE_ID_LENGTH) {
            return -1;
        }

        try {
            // Extract first 8 hex characters (32 bits) which is the timestamp
            String timestampHex = traceId.substring(0, 8);
            long timestamp = Long.parseLong(timestampHex, 16);

            log.debug("Extracted timestamp {} from trace ID {}", timestamp, traceId);

            return timestamp;
        } catch (Exception e) {
            log.warn("Failed to extract timestamp from trace ID: {}", traceId, e);
            return -1;
        }
    }

    /**
     * Extract sequence number from a trace ID
     *
     * @param traceId trace ID to extract sequence from
     * @return sequence number, or -1 if invalid
     */
    public long extractSequenceFromTraceId(String traceId) {
        if (traceId == null || traceId.length() != TRACE_ID_LENGTH) {
            return -1;
        }

        try {
            // Extract characters 8-11 (16 bits) which is the sequence
            String sequenceHex = traceId.substring(8, 12);
            long sequence = Long.parseLong(sequenceHex, 16);

            log.debug("Extracted sequence {} from trace ID {}", sequence, traceId);

            return sequence;
        } catch (Exception e) {
            log.warn("Failed to extract sequence from trace ID: {}", traceId, e);
            return -1;
        }
    }

    /**
     * Validate if a string is a valid trace ID
     *
     * @param traceId trace ID to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidTraceId(String traceId) {
        if (traceId == null) {
            return false;
        }

        // Check length
        if (traceId.length() != TRACE_ID_LENGTH) {
            return false;
        }

        // Check if all characters are valid hex
        return traceId.matches("^[0-9a-f]{32}$");
    }

    /**
     * Validate if a string is a valid span ID
     *
     * @param spanId span ID to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidSpanId(String spanId) {
        if (spanId == null) {
            return false;
        }

        // Check length
        if (spanId.length() != SPAN_ID_LENGTH) {
            return false;
        }

        // Check if all characters are valid hex
        // Also check that it's not all zeros (invalid per W3C spec)
        return spanId.matches("^[0-9a-f]{16}$") && !spanId.equals("0000000000000000");
    }

    /**
     * Validate if a string is a valid Waqiti correlation ID
     *
     * @param correlationId correlation ID to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidCorrelationId(String correlationId) {
        if (correlationId == null) {
            return false;
        }

        // Check format: wqt-[32 hex chars]
        return correlationId.matches("^" + CORRELATION_ID_PREFIX + "-[0-9a-f]{32}$");
    }

    /**
     * Generate a child trace ID based on parent trace ID
     * Maintains timestamp and node, but uses new sequence and random
     *
     * @param parentTraceId parent trace ID
     * @return child trace ID
     */
    public String generateChildTraceId(String parentTraceId) {
        if (!isValidTraceId(parentTraceId)) {
            log.warn("Invalid parent trace ID: {}, generating new trace ID", parentTraceId);
            return generateTraceId();
        }

        try {
            // Extract timestamp and node from parent
            String timestampHex = parentTraceId.substring(0, 8);
            String nodeHex = parentTraceId.substring(12, 16);

            long timestamp = Long.parseLong(timestampHex, 16);
            long node = Long.parseLong(nodeHex, 16);

            // New sequence
            long sequence = traceSequence.incrementAndGet() & 0xFFFF;

            // New random component
            long random = ThreadLocalRandom.current().nextLong();

            // Build child trace ID
            long high = (timestamp << 32) | (sequence << 16) | node;
            String childTraceId = String.format("%016x%016x", high, random);

            log.debug("Generated child trace ID: {} from parent: {}", childTraceId, parentTraceId);

            return childTraceId;
        } catch (Exception e) {
            log.error("Failed to generate child trace ID from parent: {}", parentTraceId, e);
            return generateTraceId();
        }
    }

    /**
     * Convert OpenTelemetry TraceId to hex string
     *
     * @param traceId OpenTelemetry TraceId
     * @return hex string representation
     */
    public String toHexString(io.opentelemetry.api.trace.TraceId traceId) {
        return traceId.toString();
    }

    /**
     * Convert OpenTelemetry SpanId to hex string
     *
     * @param spanId OpenTelemetry SpanId
     * @return hex string representation
     */
    public String toHexString(io.opentelemetry.api.trace.SpanId spanId) {
        return spanId.toString();
    }

    /**
     * Get current trace sequence number
     *
     * @return current sequence number
     */
    public long getCurrentTraceSequence() {
        return traceSequence.get();
    }

    /**
     * Get current span sequence number
     *
     * @return current sequence number
     */
    public long getCurrentSpanSequence() {
        return spanSequence.get();
    }

    /**
     * Reset sequence numbers (useful for testing)
     */
    public void resetSequences() {
        traceSequence.set(0);
        spanSequence.set(0);
        log.info("Trace and span sequences reset to 0");
    }

    /**
     * Generate a unique node identifier
     * Based on hostname hash, or random if hostname unavailable
     *
     * @return node identifier (64 bits)
     */
    private long generateNodeId() {
        try {
            // Try to use hostname for node identification
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            long hash = hostname.hashCode();

            // Mix with MAC address if available
            try {
                java.net.NetworkInterface network = java.net.NetworkInterface.getByInetAddress(
                        java.net.InetAddress.getLocalHost());
                if (network != null) {
                    byte[] mac = network.getHardwareAddress();
                    if (mac != null) {
                        long macHash = ByteBuffer.wrap(mac).getLong();
                        hash ^= macHash;
                    }
                }
            } catch (Exception e) {
                // MAC address not available, continue with hostname hash
                log.debug("MAC address not available for node ID generation");
            }

            log.info("Generated node ID from hostname: {}", hostname);
            return hash;

        } catch (Exception e) {
            // Hostname not available, use secure random
            long randomNodeId = secureRandom.nextLong();
            log.warn("Hostname not available, using random node ID");
            return randomNodeId;
        }
    }

    /**
     * Format trace ID for logging (shortened version)
     *
     * @param traceId trace ID to format
     * @return shortened trace ID (first 8 and last 8 characters)
     */
    public String formatForLogging(String traceId) {
        if (traceId == null || traceId.length() < 16) {
            return traceId;
        }

        return traceId.substring(0, 8) + "..." + traceId.substring(traceId.length() - 8);
    }

    /**
     * Generate trace and span IDs as a pair
     *
     * @return array with [traceId, spanId]
     */
    public String[] generateTraceAndSpanIds() {
        return new String[]{
                generateTraceId(),
                generateSpanId()
        };
    }

    /**
     * Generate complete tracing context (trace ID, span ID, correlation ID)
     *
     * @return TracingContext object
     */
    public TracingContext generateTracingContext() {
        String traceId = generateTraceId();
        String spanId = generateSpanId();
        String correlationId = generateCorrelationIdFromTraceId(traceId);

        return new TracingContext(traceId, spanId, correlationId);
    }

    /**
     * Tracing context holder
     */
    public static class TracingContext {
        private final String traceId;
        private final String spanId;
        private final String correlationId;
        private final long timestamp;

        public TracingContext(String traceId, String spanId, String correlationId) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.correlationId = correlationId;
            this.timestamp = System.currentTimeMillis();
        }

        public String getTraceId() {
            return traceId;
        }

        public String getSpanId() {
            return spanId;
        }

        public String getCorrelationId() {
            return correlationId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("TracingContext{traceId='%s', spanId='%s', correlationId='%s', timestamp=%d}",
                    traceId, spanId, correlationId, timestamp);
        }
    }
}
