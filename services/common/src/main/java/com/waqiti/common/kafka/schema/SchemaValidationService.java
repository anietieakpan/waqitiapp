package com.waqiti.common.kafka.schema;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Schema Validation Service
 * 
 * Validates messages against registered schemas and provides
 * data quality assurance for Kafka messages.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaValidationService {

    private final SchemaRegistryClient schemaRegistryClient;

    /**
     * Validate a message against its schema
     */
    public boolean validateMessage(String subject, byte[] messageData) {
        try {
            // Get the latest schema for the subject
            String schemaString = schemaRegistryClient.getLatestSchemaMetadata(subject).getSchema();
            Schema schema = new Schema.Parser().parse(schemaString);
            
            // Create decoder
            Decoder decoder = DecoderFactory.get().binaryDecoder(messageData, null);
            
            // Create reader
            DatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            
            // Try to read the message - if it succeeds, it's valid
            GenericRecord record = reader.read(null, decoder);
            
            log.debug("Message validation successful for subject: {}", subject);
            return true;
            
        } catch (Exception e) {
            log.error("Message validation failed for subject: {}", subject, e);
            return false;
        }
    }

    /**
     * Validate a message against a specific schema version
     */
    public boolean validateMessage(String subject, int version, byte[] messageData) {
        try {
            // Get the specific schema version
            String schemaString = schemaRegistryClient.getSchemaMetadata(subject, version).getSchema();
            Schema schema = new Schema.Parser().parse(schemaString);
            
            // Create decoder
            Decoder decoder = DecoderFactory.get().binaryDecoder(messageData, null);
            
            // Create reader
            DatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            
            // Try to read the message
            GenericRecord record = reader.read(null, decoder);
            
            log.debug("Message validation successful for subject: {}, version: {}", subject, version);
            return true;
            
        } catch (Exception e) {
            log.error("Message validation failed for subject: {}, version: {}", subject, version, e);
            return false;
        }
    }

    /**
     * Validate schema format
     */
    public boolean validateSchema(String schemaJson) {
        try {
            Schema.Parser parser = new Schema.Parser();
            parser.parse(schemaJson);
            return true;
        } catch (Exception e) {
            log.error("Schema validation failed", e);
            return false;
        }
    }

    /**
     * Get validation report for a message
     */
    public ValidationReport getValidationReport(String subject, byte[] messageData) {
        try {
            String schemaString = schemaRegistryClient.getLatestSchemaMetadata(subject).getSchema();
            Schema schema = new Schema.Parser().parse(schemaString);
            Decoder decoder = DecoderFactory.get().binaryDecoder(messageData, null);
            DatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            GenericRecord record = reader.read(null, decoder);
            
            return ValidationReport.builder()
                    .valid(true)
                    .subject(subject)
                    .schemaVersion(schemaRegistryClient.getLatestSchemaMetadata(subject).getVersion())
                    .message("Validation successful")
                    .build();
                    
        } catch (Exception e) {
            return ValidationReport.builder()
                    .valid(false)
                    .subject(subject)
                    .error(e.getMessage())
                    .message("Validation failed: " + e.getMessage())
                    .build();
        }
    }

    public static class ValidationReport {
        private final boolean valid;
        private final String subject;
        private final Integer schemaVersion;
        private final String message;
        private final String error;

        private ValidationReport(boolean valid, String subject, Integer schemaVersion, String message, String error) {
            this.valid = valid;
            this.subject = subject;
            this.schemaVersion = schemaVersion;
            this.message = message;
            this.error = error;
        }

        public static ValidationReportBuilder builder() {
            return new ValidationReportBuilder();
        }

        // Getters
        public boolean isValid() { return valid; }
        public String getSubject() { return subject; }
        public Integer getSchemaVersion() { return schemaVersion; }
        public String getMessage() { return message; }
        public String getError() { return error; }

        public static class ValidationReportBuilder {
            private boolean valid;
            private String subject;
            private Integer schemaVersion;
            private String message;
            private String error;

            public ValidationReportBuilder valid(boolean valid) {
                this.valid = valid;
                return this;
            }

            public ValidationReportBuilder subject(String subject) {
                this.subject = subject;
                return this;
            }

            public ValidationReportBuilder schemaVersion(Integer schemaVersion) {
                this.schemaVersion = schemaVersion;
                return this;
            }

            public ValidationReportBuilder message(String message) {
                this.message = message;
                return this;
            }

            public ValidationReportBuilder error(String error) {
                this.error = error;
                return this;
            }

            public ValidationReport build() {
                return new ValidationReport(valid, subject, schemaVersion, message, error);
            }
        }
    }
}