package com.waqiti.common.kafka.schema;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Schema Migration Service
 * 
 * Handles:
 * - Schema registration
 * - Schema evolution
 * - Compatibility checking
 * - Schema versioning
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaMigrationService {

    private final SchemaRegistryClient schemaRegistryClient;

    /**
     * Register a new schema or schema version
     */
    public int registerSchema(String subject, Schema schema) throws IOException, RestClientException {
        log.info("Registering schema for subject: {}", subject);
        return schemaRegistryClient.register(subject, schema);
    }

    /**
     * Check if a schema is compatible with the current version
     */
    public boolean isCompatible(String subject, Schema schema) {
        try {
            return schemaRegistryClient.testCompatibility(subject, schema);
        } catch (Exception e) {
            log.error("Error checking schema compatibility for subject: {}", subject, e);
            return false;
        }
    }

    /**
     * Get the latest schema for a subject
     */
    public Schema getLatestSchema(String subject) throws IOException, RestClientException {
        String schemaString = schemaRegistryClient.getLatestSchemaMetadata(subject).getSchema();
        return new Schema.Parser().parse(schemaString);
    }

    /**
     * Get all versions of a schema
     */
    public List<Integer> getSchemaVersions(String subject) throws IOException, RestClientException {
        return schemaRegistryClient.getAllVersions(subject);
    }

    /**
     * Evolve schema with backward compatibility check
     */
    public int evolveSchema(String subject, Schema newSchema) throws IOException, RestClientException {
        log.info("Evolving schema for subject: {}", subject);
        
        // Check compatibility first
        if (!isCompatible(subject, newSchema)) {
            throw new SchemaEvolutionException("Schema is not compatible with previous version");
        }
        
        return registerSchema(subject, newSchema);
    }

    /**
     * Delete a schema subject
     */
    public List<Integer> deleteSubject(String subject) throws IOException, RestClientException {
        log.warn("Deleting schema subject: {}", subject);
        return schemaRegistryClient.deleteSubject(subject);
    }

    /**
     * Get schema by ID
     */
    public Schema getSchemaById(int id) throws IOException, RestClientException {
        io.confluent.kafka.schemaregistry.client.SchemaMetadata schemaMetadata = 
            schemaRegistryClient.getSchemaMetadata(null, id);
        return new Schema.Parser().parse(schemaMetadata.getSchema());
    }

    public static class SchemaEvolutionException extends RuntimeException {
        public SchemaEvolutionException(String message) {
            super(message);
        }
    }
}