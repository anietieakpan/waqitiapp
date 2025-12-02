package com.waqiti.corebanking.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Base class for unit tests with Mockito
 *
 * Provides:
 * - Mockito extension for @Mock, @InjectMocks annotations
 * - ObjectMapper for JSON operations
 * - Common setup for all unit tests
 *
 * Unit tests should:
 * - Test business logic in isolation
 * - Mock all external dependencies
 * - Be fast (<100ms per test)
 * - Not require database or network
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseUnitTest {

    protected ObjectMapper objectMapper;

    @BeforeEach
    void baseSetUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Helper method to convert object to JSON string
     */
    protected String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    /**
     * Helper method to convert JSON string to object
     */
    protected <T> T fromJson(String json, Class<T> clazz) throws Exception {
        return objectMapper.readValue(json, clazz);
    }
}
