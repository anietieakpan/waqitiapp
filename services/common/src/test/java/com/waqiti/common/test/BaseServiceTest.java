package com.waqiti.common.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Base class for service unit tests
 *
 * Provides common test infrastructure:
 * - Spring test context
 * - Mockito support
 * - Test profiles
 * - Common assertions
 *
 * Usage:
 * <pre>
 * @ExtendWith(MockitoExtension.class)
 * class MyServiceTest extends BaseServiceTest {
 *     @Mock
 *     private MyDependency dependency;
 *
 *     @InjectMocks
 *     private MyService service;
 *
 *     @Test
 *     void testMyFeature() {
 *         // Test implementation
 *     }
 * }
 * </pre>
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@ExtendWith({SpringExtension.class, MockitoExtension.class})
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseServiceTest {

    @BeforeEach
    public void baseSetUp() {
        // Common setup for all service tests
    }

    /**
     * Utility method to capture time for performance testing
     */
    protected long measureExecutionTime(Runnable operation) {
        long start = System.currentTimeMillis();
        operation.run();
        return System.currentTimeMillis() - start;
    }
}
