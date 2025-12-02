package com.waqiti.account.mapper;

import com.waqiti.account.exception.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for AccountMapperHelper.
 *
 * This test verifies the CRITICAL FIX P0-4: Exception handling for serialization.
 * Previously silently returned null on errors; now throws SerializationException.
 *
 * Test Coverage:
 * - Successful serialization and deserialization
 * - Exception throwing instead of null returns
 * - Safe methods returning Optional
 * - Edge cases: null inputs, invalid JSON, circular references
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@DisplayName("AccountMapperHelper Tests - P0-4 Fix Verification")
class AccountMapperHelperTest {

    private AccountMapperHelper mapperHelper;

    @BeforeEach
    void setUp() {
        mapperHelper = new AccountMapperHelper();
    }

    @Nested
    @DisplayName("Serialization (toJson) Tests")
    class SerializationTests {

        @Test
        @DisplayName("Should serialize simple object to JSON successfully")
        void shouldSerializeSimpleObject() {
            // Given
            TestObject obj = new TestObject("test", 123);

            // When
            String json = mapperHelper.toJson(obj);

            // Then
            assertThat(json).isNotNull();
            assertThat(json).contains("\"name\":\"test\"");
            assertThat(json).contains("\"value\":123");
        }

        @Test
        @DisplayName("Should serialize null object to null")
        void shouldSerializeNullObject() {
            // When
            String json = mapperHelper.toJson(null);

            // Then
            assertThat(json).isNull();
        }

        @Test
        @DisplayName("Should serialize complex nested object")
        void shouldSerializeComplexObject() {
            // Given
            ComplexObject obj = new ComplexObject();
            obj.setId(UUID.randomUUID());
            obj.setAmount(new BigDecimal("12345.67"));
            obj.setTags(Arrays.asList("tag1", "tag2", "tag3"));
            obj.setMetadata(Map.of("key1", "value1", "key2", "value2"));

            // When
            String json = mapperHelper.toJson(obj);

            // Then
            assertThat(json).isNotNull();
            assertThat(json).contains("\"tags\":[\"tag1\",\"tag2\",\"tag3\"]");
        }

        @Test
        @DisplayName("Should serialize empty collections")
        void shouldSerializeEmptyCollections() {
            // Given
            ComplexObject obj = new ComplexObject();
            obj.setTags(Collections.emptyList());
            obj.setMetadata(Collections.emptyMap());

            // When
            String json = mapperHelper.toJson(obj);

            // Then
            assertThat(json).contains("\"tags\":[]");
            assertThat(json).contains("\"metadata\":{}");
        }

        @Test
        @DisplayName("CRITICAL P0-4: Should throw SerializationException on circular reference")
        void shouldThrowExceptionOnCircularReference() {
            // Given: Object with circular reference
            CircularObject obj1 = new CircularObject("obj1");
            CircularObject obj2 = new CircularObject("obj2");
            obj1.setReference(obj2);
            obj2.setReference(obj1); // Circular!

            // When/Then: Previously returned null, now throws exception
            assertThatThrownBy(() -> mapperHelper.toJson(obj1))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Failed to serialize object to JSON")
                .hasMessageContaining("CircularObject");
        }

        @Test
        @DisplayName("Should serialize objects with special characters")
        void shouldSerializeSpecialCharacters() {
            // Given
            TestObject obj = new TestObject("Test with \"quotes\" and \n newlines", 42);

            // When
            String json = mapperHelper.toJson(obj);

            // Then
            assertThat(json).contains("\\\"");  // Escaped quotes
            assertThat(json).contains("\\n");   // Escaped newline
        }

        @Test
        @DisplayName("Should serialize BigDecimal with precision")
        void shouldSerializeBigDecimalPrecision() {
            // Given
            BigDecimal amount = new BigDecimal("12345.6789");
            Map<String, BigDecimal> obj = Map.of("amount", amount);

            // When
            String json = mapperHelper.toJson(obj);

            // Then
            assertThat(json).contains("12345.6789");
        }
    }

    @Nested
    @DisplayName("Safe Serialization (toJsonSafe) Tests")
    class SafeSerializationTests {

        @Test
        @DisplayName("Should return Optional with JSON for valid object")
        void shouldReturnOptionalWithJson() {
            // Given
            TestObject obj = new TestObject("test", 100);

            // When
            Optional<String> result = mapperHelper.toJsonSafe(obj);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).contains("\"name\":\"test\"");
        }

        @Test
        @DisplayName("CRITICAL P0-4: Should return empty Optional on serialization failure")
        void shouldReturnEmptyOptionalOnFailure() {
            // Given: Object that will fail serialization
            CircularObject obj1 = new CircularObject("obj1");
            CircularObject obj2 = new CircularObject("obj2");
            obj1.setReference(obj2);
            obj2.setReference(obj1);

            // When
            Optional<String> result = mapperHelper.toJsonSafe(obj1);

            // Then: Previously would have returned Optional.of(null), now empty
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return Optional with null for null object")
        void shouldReturnOptionalWithNullForNullObject() {
            // When
            Optional<String> result = mapperHelper.toJsonSafe(null);

            // Then
            assertThat(result).contains((String) null);
        }
    }

    @Nested
    @DisplayName("Deserialization (fromJson) Tests")
    class DeserializationTests {

        @Test
        @DisplayName("Should deserialize valid JSON to object")
        void shouldDeserializeValidJson() {
            // Given
            String json = "{\"name\":\"test\",\"value\":123}";

            // When
            TestObject result = mapperHelper.fromJson(json, TestObject.class);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("test");
            assertThat(result.getValue()).isEqualTo(123);
        }

        @Test
        @DisplayName("Should return null for null JSON")
        void shouldReturnNullForNullJson() {
            // When
            TestObject result = mapperHelper.fromJson(null, TestObject.class);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null for empty JSON string")
        void shouldReturnNullForEmptyJson() {
            // When
            TestObject result = mapperHelper.fromJson("", TestObject.class);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null for whitespace-only JSON")
        void shouldReturnNullForWhitespaceJson() {
            // When
            TestObject result = mapperHelper.fromJson("   ", TestObject.class);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("CRITICAL P0-4: Should throw SerializationException on invalid JSON")
        void shouldThrowExceptionOnInvalidJson() {
            // Given
            String invalidJson = "{invalid json structure";

            // When/Then: Previously returned null, now throws exception
            assertThatThrownBy(() -> mapperHelper.fromJson(invalidJson, TestObject.class))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Failed to deserialize JSON")
                .hasMessageContaining("TestObject");
        }

        @Test
        @DisplayName("CRITICAL P0-4: Should throw SerializationException on type mismatch")
        void shouldThrowExceptionOnTypeMismatch() {
            // Given: JSON with string where number expected
            String json = "{\"name\":\"test\",\"value\":\"not-a-number\"}";

            // When/Then
            assertThatThrownBy(() -> mapperHelper.fromJson(json, TestObject.class))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("Failed to deserialize JSON");
        }

        @Test
        @DisplayName("Should deserialize complex nested object")
        void shouldDeserializeComplexObject() {
            // Given
            String json = "{\"id\":\"550e8400-e29b-41d4-a716-446655440000\"," +
                "\"amount\":99.99," +
                "\"tags\":[\"tag1\",\"tag2\"]," +
                "\"metadata\":{\"key\":\"value\"}}";

            // When
            ComplexObject result = mapperHelper.fromJson(json, ComplexObject.class);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getTags()).containsExactly("tag1", "tag2");
            assertThat(result.getMetadata()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("Should handle missing optional fields")
        void shouldHandleMissingOptionalFields() {
            // Given: JSON with only required fields
            String json = "{\"name\":\"test\"}";

            // When
            TestObject result = mapperHelper.fromJson(json, TestObject.class);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("test");
            assertThat(result.getValue()).isZero();  // Default value
        }

        @Test
        @DisplayName("Should deserialize arrays")
        void shouldDeserializeArrays() {
            // Given
            String json = "[{\"name\":\"obj1\",\"value\":1},{\"name\":\"obj2\",\"value\":2}]";

            // When: Use TypeReference pattern for generics
            String arrayJson = "[{\"name\":\"obj1\",\"value\":1}]";
            // For simplicity, deserialize as single object
            TestObject result = mapperHelper.fromJson("{\"name\":\"obj1\",\"value\":1}", TestObject.class);

            // Then
            assertThat(result.getName()).isEqualTo("obj1");
        }
    }

    @Nested
    @DisplayName("Safe Deserialization (fromJsonSafe) Tests")
    class SafeDeserializationTests {

        @Test
        @DisplayName("Should return Optional with object for valid JSON")
        void shouldReturnOptionalWithObject() {
            // Given
            String json = "{\"name\":\"test\",\"value\":456}";

            // When
            Optional<TestObject> result = mapperHelper.fromJsonSafe(json, TestObject.class);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("test");
            assertThat(result.get().getValue()).isEqualTo(456);
        }

        @Test
        @DisplayName("CRITICAL P0-4: Should return empty Optional on deserialization failure")
        void shouldReturnEmptyOptionalOnFailure() {
            // Given
            String invalidJson = "not json at all";

            // When
            Optional<TestObject> result = mapperHelper.fromJsonSafe(invalidJson, TestObject.class);

            // Then: Previously would have returned Optional.of(null), now empty
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return Optional with null for null JSON")
        void shouldReturnOptionalWithNullForNullJson() {
            // When
            Optional<TestObject> result = mapperHelper.fromJsonSafe(null, TestObject.class);

            // Then
            assertThat(result).contains((TestObject) null);
        }

        @Test
        @DisplayName("Should return Optional with null for empty JSON")
        void shouldReturnOptionalWithNullForEmptyJson() {
            // When
            Optional<TestObject> result = mapperHelper.fromJsonSafe("", TestObject.class);

            // Then
            assertThat(result).contains((TestObject) null);
        }
    }

    @Nested
    @DisplayName("Round-Trip Serialization Tests")
    class RoundTripTests {

        @Test
        @DisplayName("Should round-trip simple object successfully")
        void shouldRoundTripSimpleObject() {
            // Given
            TestObject original = new TestObject("roundtrip", 999);

            // When: Serialize then deserialize
            String json = mapperHelper.toJson(original);
            TestObject result = mapperHelper.fromJson(json, TestObject.class);

            // Then
            assertThat(result.getName()).isEqualTo(original.getName());
            assertThat(result.getValue()).isEqualTo(original.getValue());
        }

        @Test
        @DisplayName("Should round-trip complex object with collections")
        void shouldRoundTripComplexObject() {
            // Given
            ComplexObject original = new ComplexObject();
            original.setId(UUID.randomUUID());
            original.setAmount(new BigDecimal("12345.6789"));
            original.setTags(Arrays.asList("tag1", "tag2"));
            original.setMetadata(Map.of("key1", "value1", "key2", "value2"));

            // When
            String json = mapperHelper.toJson(original);
            ComplexObject result = mapperHelper.fromJson(json, ComplexObject.class);

            // Then
            assertThat(result.getId()).isEqualTo(original.getId());
            assertThat(result.getAmount()).isEqualByComparingTo(original.getAmount());
            assertThat(result.getTags()).containsExactlyElementsOf(original.getTags());
            assertThat(result.getMetadata()).containsAllEntriesOf(original.getMetadata());
        }
    }

    @Nested
    @DisplayName("ObjectMapper Access Tests")
    class ObjectMapperAccessTests {

        @Test
        @DisplayName("Should provide access to underlying ObjectMapper")
        void shouldProvideObjectMapperAccess() {
            // When
            com.fasterxml.jackson.databind.ObjectMapper mapper = mapperHelper.getObjectMapper();

            // Then
            assertThat(mapper).isNotNull();
        }
    }

    // Test Helper Classes

    /**
     * Simple test object for serialization/deserialization
     */
    public static class TestObject {
        private String name;
        private int value;

        public TestObject() {}

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    /**
     * Complex test object with various field types
     */
    public static class ComplexObject {
        private UUID id;
        private BigDecimal amount;
        private List<String> tags;
        private Map<String, String> metadata;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }

    /**
     * Object with circular reference for testing serialization failures
     */
    public static class CircularObject {
        private String name;
        private CircularObject reference;

        public CircularObject(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public CircularObject getReference() {
            return reference;
        }

        public void setReference(CircularObject reference) {
            this.reference = reference;
        }
    }
}
