package com.waqiti.billpayment;

import com.waqiti.billpayment.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests with Testcontainers
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public abstract class BaseIntegrationTest {

    @BeforeEach
    public void baseSetup() {
        // Common setup for all integration tests
    }
}
