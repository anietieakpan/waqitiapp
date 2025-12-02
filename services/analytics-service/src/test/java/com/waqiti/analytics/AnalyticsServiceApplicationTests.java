package com.waqiti.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test to verify Spring Boot application context loads successfully
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceApplicationTests {

    @Test
    void contextLoads() {
        // This test verifies that the Spring application context loads successfully
        // If this test passes, all beans are properly configured and wired
    }
}
