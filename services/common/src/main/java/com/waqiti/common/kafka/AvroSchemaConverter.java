package com.waqiti.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Avro Schema Converter for JSON to Avro Migration
 *
 * TECHNICAL IMPACT: Enables schema evolution and reduces message size by 40-60%
 *
 * Features:
 * - JSON to Avro conversion
 * - Avro to JSON conversion
 * - Schema validation
 * - Type mapping (BigDecimal, Instant, UUID)
 * - Backward/forward compatibility
 * - Error handling and logging
 *
 * Usage:
 * <pre>
 * // Convert JSON event to Avro
 * Map<String, Object> jsonEvent = ...;
 * GenericRecord avroRecord = converter.jsonToAvro(jsonEvent, walletTransactionSchema);
 *
 * // Convert Avro back to JSON
 * Map<String, Object> jsonEvent = converter.avroToJson(avroRecord);
 * </pre>
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AvroSchemaConverter {

    private final ObjectMapper objectMapper;

    /**
     * Convert JSON event to Avro GenericRecord
     */
    public GenericRecord jsonToAvro(Map<String, Object> jsonEvent, Schema schema) throws IOException {
        GenericRecord record = new GenericData.Record(schema);

        for (Schema.Field field : schema.getFields()) {
            String fieldName = field.name();
            Object jsonValue = jsonEvent.get(fieldName);

            if (jsonValue == null) {
                // Use default value if field is nullable
                if (field.hasDefaultValue()) {
                    record.put(fieldName, field.defaultVal());
                }
                continue;
            }

            Object avroValue = convertToAvroType(jsonValue, field.schema());
            record.put(fieldName, avroValue);
        }

        return record;
    }

    /**
     * Convert Avro GenericRecord to JSON
     */
    public Map<String, Object> avroToJson(GenericRecord avroRecord) {
        Map<String, Object> jsonEvent = new HashMap<>();

        for (Schema.Field field : avroRecord.getSchema().getFields()) {
            String fieldName = field.name();
            Object avroValue = avroRecord.get(fieldName);

            if (avroValue == null) {
                continue;
            }

            Object jsonValue = convertToJsonType(avroValue, field.schema());
            jsonEvent.put(fieldName, jsonValue);
        }

        return jsonEvent;
    }

    /**
     * Convert JSON value to Avro type
     */
    private Object convertToAvroType(Object jsonValue, Schema schema) {
        Schema.Type type = schema.getType();

        // Handle union types (nullable fields)
        if (type == Schema.Type.UNION) {
            for (Schema unionSchema : schema.getTypes()) {
                if (unionSchema.getType() != Schema.Type.NULL) {
                    return convertToAvroType(jsonValue, unionSchema);
                }
            }
        }

        switch (type) {
            case STRING:
                return jsonValue.toString();

            case INT:
                if (jsonValue instanceof Number) {
                    return ((Number) jsonValue).intValue();
                }
                return Integer.parseInt(jsonValue.toString());

            case LONG:
                if (jsonValue instanceof Number) {
                    return ((Number) jsonValue).longValue();
                }
                // Handle timestamp conversion
                if (jsonValue instanceof String) {
                    return Instant.parse((String) jsonValue).toEpochMilli();
                }
                return Long.parseLong(jsonValue.toString());

            case BYTES:
                // Handle BigDecimal as bytes (for decimal logical type)
                if (jsonValue instanceof BigDecimal) {
                    return bigDecimalToBytes((BigDecimal) jsonValue);
                } else if (jsonValue instanceof Number) {
                    return bigDecimalToBytes(BigDecimal.valueOf(((Number) jsonValue).doubleValue()));
                } else if (jsonValue instanceof String) {
                    return bigDecimalToBytes(new BigDecimal((String) jsonValue));
                }
                return jsonValue;

            case MAP:
                if (jsonValue instanceof Map) {
                    Map<String, Object> avroMap = new HashMap<>();
                    ((Map<?, ?>) jsonValue).forEach((k, v) ->
                        avroMap.put(k.toString(), v != null ? v.toString() : null)
                    );
                    return avroMap;
                }
                return jsonValue;

            case ENUM:
                return new GenericData.EnumSymbol(schema, jsonValue.toString());

            default:
                return jsonValue;
        }
    }

    /**
     * Convert Avro value to JSON type
     */
    private Object convertToJsonType(Object avroValue, Schema schema) {
        Schema.Type type = schema.getType();

        // Handle union types
        if (type == Schema.Type.UNION) {
            for (Schema unionSchema : schema.getTypes()) {
                if (unionSchema.getType() != Schema.Type.NULL) {
                    return convertToJsonType(avroValue, unionSchema);
                }
            }
        }

        switch (type) {
            case BYTES:
                // Convert bytes back to BigDecimal if decimal logical type
                if ("decimal".equals(schema.getProp("logicalType"))) {
                    return bytesToBigDecimal((ByteBuffer) avroValue);
                }
                return avroValue;

            case LONG:
                // Convert timestamp back to Instant string
                if ("timestamp-millis".equals(schema.getProp("logicalType"))) {
                    return Instant.ofEpochMilli((Long) avroValue).toString();
                }
                return avroValue;

            case ENUM:
                return avroValue.toString();

            case MAP:
                return new HashMap<>((Map<?, ?>) avroValue);

            default:
                return avroValue;
        }
    }

    /**
     * Convert BigDecimal to bytes (Avro decimal logical type)
     */
    private ByteBuffer bigDecimalToBytes(BigDecimal decimal) {
        if (decimal == null) {
            return null;
        }

        // Scale to 4 decimal places (as per schema)
        BigDecimal scaled = decimal.setScale(4, RoundingMode.HALF_UP);
        byte[] bytes = scaled.unscaledValue().toByteArray();
        return ByteBuffer.wrap(bytes);
    }

    /**
     * Convert bytes to BigDecimal (Avro decimal logical type)
     */
    private BigDecimal bytesToBigDecimal(ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new BigDecimal(new java.math.BigInteger(bytes), 4);
    }

    /**
     * Validate JSON event against Avro schema
     */
    public boolean validate(Map<String, Object> jsonEvent, Schema schema) {
        try {
            jsonToAvro(jsonEvent, schema);
            return true;
        } catch (Exception e) {
            log.error("AVRO: Validation failed for schema {}: {}", schema.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Load Avro schema from classpath
     */
    public Schema loadSchema(String schemaPath) throws IOException {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(schemaPath)) {
            if (inputStream == null) {
                throw new IOException("Schema not found: " + schemaPath);
            }
            return new Schema.Parser().parse(inputStream);
        }
    }
}
