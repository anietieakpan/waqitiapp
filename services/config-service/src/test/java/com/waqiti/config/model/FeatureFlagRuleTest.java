package com.waqiti.config.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for FeatureFlagRule evaluation logic
 * Tests all rule types, operators, and composite rules
 */
class FeatureFlagRuleTest {

    // ==================== USER Rule Tests ====================

    @Test
    void testUserRule_UserInList_ReturnsTrue() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.USER)
            .operator(FeatureFlagRule.RuleOperator.IN)
            .values(Arrays.asList("user1", "user2", "user3"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("userId", "user2");

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testUserRule_UserNotInList_ReturnsFalse() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.USER)
            .operator(FeatureFlagRule.RuleOperator.IN)
            .values(Arrays.asList("user1", "user2", "user3"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("userId", "user999");

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    @Test
    void testUserRule_NoUserIdInContext_ReturnsFalse() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.USER)
            .operator(FeatureFlagRule.RuleOperator.IN)
            .values(Arrays.asList("user1", "user2"))
            .active(true)
            .build();

        Map<String, Object> context = new HashMap<>();

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    @Test
    void testUserRule_NotEqualsOperator_ReturnsCorrectly() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.USER)
            .operator(FeatureFlagRule.RuleOperator.NOT_IN)
            .values(Arrays.asList("blocked-user1", "blocked-user2"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("userId", "allowed-user");

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    // ==================== GROUP Rule Tests ====================

    @Test
    void testGroupRule_UserInGroup_ReturnsTrue() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.GROUP)
            .values(Arrays.asList("admin", "power-user"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of(
            "userGroups", Arrays.asList("user", "power-user", "beta-tester")
        );

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testGroupRule_UserNotInGroup_ReturnsFalse() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.GROUP)
            .values(Arrays.asList("admin", "power-user"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of(
            "userGroups", Arrays.asList("user", "guest")
        );

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    @Test
    void testGroupRule_NoUserGroups_ReturnsFalse() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.GROUP)
            .values(Arrays.asList("admin"))
            .active(true)
            .build();

        Map<String, Object> context = new HashMap<>();

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    // ==================== ENVIRONMENT Rule Tests ====================

    @Test
    void testEnvironmentRule_MatchingEnvironment_ReturnsTrue() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.ENVIRONMENT)
            .values(Arrays.asList("staging", "production"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("environment", "production");

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testEnvironmentRule_NonMatchingEnvironment_ReturnsFalse() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.ENVIRONMENT)
            .values(Arrays.asList("staging", "production"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("environment", "development");

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    // ==================== PERCENTAGE Rule Tests ====================

    @Test
    void testPercentageRule_ZeroPercent_ReturnsFalse() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.PERCENTAGE)
            .percentage(0)
            .active(true)
            .build();

        Map<String, Object> context = Map.of("userId", "any-user");

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    @Test
    void testPercentageRule_HundredPercent_ReturnsTrue() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.PERCENTAGE)
            .percentage(100)
            .active(true)
            .build();

        Map<String, Object> context = Map.of("userId", "any-user");

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testPercentageRule_ConsistentHashing_SameUserSameResult() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.PERCENTAGE)
            .percentage(50)
            .active(true)
            .build();

        Map<String, Object> context = Map.of("userId", "test-user-123");

        boolean result1 = rule.evaluate(context);
        boolean result2 = rule.evaluate(context);
        boolean result3 = rule.evaluate(context);

        assertThat(result1).isEqualTo(result2);
        assertThat(result2).isEqualTo(result3);
    }

    @Test
    void testPercentageRule_NoUserId_ReturnsFalse() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.PERCENTAGE)
            .percentage(50)
            .active(true)
            .build();

        Map<String, Object> context = new HashMap<>();

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    // ==================== TIME_WINDOW Rule Tests ====================

    @Test
    void testTimeWindowRule_CurrentTimeInWindow_ReturnsTrue() {
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.HOURS);
        Instant end = now.plus(1, ChronoUnit.HOURS);

        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.TIME_WINDOW)
            .startTime(start.toString())
            .endTime(end.toString())
            .active(true)
            .build();

        Map<String, Object> context = new HashMap<>();

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testTimeWindowRule_CurrentTimeBeforeWindow_ReturnsFalse() {
        Instant now = Instant.now();
        Instant start = now.plus(1, ChronoUnit.HOURS);
        Instant end = now.plus(2, ChronoUnit.HOURS);

        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.TIME_WINDOW)
            .startTime(start.toString())
            .endTime(end.toString())
            .active(true)
            .build();

        Map<String, Object> context = new HashMap<>();

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    @Test
    void testTimeWindowRule_CurrentTimeAfterWindow_ReturnsFalse() {
        Instant now = Instant.now();
        Instant start = now.minus(2, ChronoUnit.HOURS);
        Instant end = now.minus(1, ChronoUnit.HOURS);

        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.TIME_WINDOW)
            .startTime(start.toString())
            .endTime(end.toString())
            .active(true)
            .build();

        Map<String, Object> context = new HashMap<>();

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    // ==================== GEOGRAPHIC Rule Tests ====================

    @Test
    void testGeographicRule_CountryInList_ReturnsTrue() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.GEOGRAPHIC)
            .values(Arrays.asList("US", "CA", "GB"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("country", "CA");

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testGeographicRule_CountryNotInList_ReturnsFalse() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.GEOGRAPHIC)
            .values(Arrays.asList("US", "CA", "GB"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("country", "FR");

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    // ==================== DEVICE Rule Tests ====================

    @Test
    void testDeviceRule_DeviceTypeMatches_ReturnsTrue() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.DEVICE)
            .values(Arrays.asList("ios", "android"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("deviceType", "ios");

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testDeviceRule_DeviceTypeDoesNotMatch_ReturnsFalse() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.DEVICE)
            .values(Arrays.asList("ios", "android"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("deviceType", "web");

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    // ==================== ATTRIBUTE Rule Tests ====================

    @Test
    void testAttributeRule_EqualsOperator_ReturnsCorrectly() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.ATTRIBUTE)
            .attribute("subscriptionTier")
            .operator(FeatureFlagRule.RuleOperator.EQUALS)
            .values(Arrays.asList("premium", "enterprise"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("subscriptionTier", "premium");

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testAttributeRule_ContainsOperator_ReturnsCorrectly() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.ATTRIBUTE)
            .attribute("email")
            .operator(FeatureFlagRule.RuleOperator.CONTAINS)
            .values(Arrays.asList("@company.com"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("email", "user@company.com");

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testAttributeRule_StartsWithOperator_ReturnsCorrectly() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.ATTRIBUTE)
            .attribute("username")
            .operator(FeatureFlagRule.RuleOperator.STARTS_WITH)
            .values(Arrays.asList("admin_"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("username", "admin_john");

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testAttributeRule_EndsWithOperator_ReturnsCorrectly() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.ATTRIBUTE)
            .attribute("email")
            .operator(FeatureFlagRule.RuleOperator.ENDS_WITH)
            .values(Arrays.asList(".edu"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("email", "student@university.edu");

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testAttributeRule_MatchesRegexOperator_ReturnsCorrectly() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.ATTRIBUTE)
            .attribute("phone")
            .operator(FeatureFlagRule.RuleOperator.MATCHES_REGEX)
            .values(Arrays.asList("\\d{3}-\\d{3}-\\d{4}"))
            .active(true)
            .build();

        Map<String, Object> context = Map.of("phone", "555-123-4567");

        boolean result = rule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testAttributeRule_AttributeNotInContext_ReturnsFalse() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.ATTRIBUTE)
            .attribute("nonExistentAttribute")
            .operator(FeatureFlagRule.RuleOperator.EQUALS)
            .values(Arrays.asList("value"))
            .active(true)
            .build();

        Map<String, Object> context = new HashMap<>();

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    // ==================== COMPOSITE Rule Tests ====================

    @Test
    void testCompositeRule_AndOperator_AllRulesPass_ReturnsTrue() {
        FeatureFlagRule userRule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.USER)
            .operator(FeatureFlagRule.RuleOperator.IN)
            .values(Arrays.asList("user1"))
            .active(true)
            .build();

        FeatureFlagRule environmentRule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.ENVIRONMENT)
            .values(Arrays.asList("production"))
            .active(true)
            .build();

        FeatureFlagRule compositeRule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.COMPOSITE)
            .logicOperator(FeatureFlagRule.LogicOperator.AND)
            .childRules(Arrays.asList(userRule, environmentRule))
            .active(true)
            .build();

        Map<String, Object> context = Map.of(
            "userId", "user1",
            "environment", "production"
        );

        boolean result = compositeRule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testCompositeRule_AndOperator_OneRuleFails_ReturnsFalse() {
        FeatureFlagRule userRule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.USER)
            .operator(FeatureFlagRule.RuleOperator.IN)
            .values(Arrays.asList("user1"))
            .active(true)
            .build();

        FeatureFlagRule environmentRule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.ENVIRONMENT)
            .values(Arrays.asList("production"))
            .active(true)
            .build();

        FeatureFlagRule compositeRule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.COMPOSITE)
            .logicOperator(FeatureFlagRule.LogicOperator.AND)
            .childRules(Arrays.asList(userRule, environmentRule))
            .active(true)
            .build();

        Map<String, Object> context = Map.of(
            "userId", "user1",
            "environment", "development"  // This doesn't match
        );

        boolean result = compositeRule.evaluate(context);

        assertThat(result).isFalse();
    }

    @Test
    void testCompositeRule_OrOperator_OneRulePasses_ReturnsTrue() {
        FeatureFlagRule userRule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.USER)
            .operator(FeatureFlagRule.RuleOperator.IN)
            .values(Arrays.asList("user1"))
            .active(true)
            .build();

        FeatureFlagRule environmentRule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.ENVIRONMENT)
            .values(Arrays.asList("production"))
            .active(true)
            .build();

        FeatureFlagRule compositeRule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.COMPOSITE)
            .logicOperator(FeatureFlagRule.LogicOperator.OR)
            .childRules(Arrays.asList(userRule, environmentRule))
            .active(true)
            .build();

        Map<String, Object> context = Map.of(
            "userId", "user1",  // This matches
            "environment", "development"  // This doesn't match
        );

        boolean result = compositeRule.evaluate(context);

        assertThat(result).isTrue();
    }

    @Test
    void testCompositeRule_OrOperator_NoRulesPass_ReturnsFalse() {
        FeatureFlagRule userRule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.USER)
            .operator(FeatureFlagRule.RuleOperator.IN)
            .values(Arrays.asList("user1"))
            .active(true)
            .build();

        FeatureFlagRule environmentRule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.ENVIRONMENT)
            .values(Arrays.asList("production"))
            .active(true)
            .build();

        FeatureFlagRule compositeRule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.COMPOSITE)
            .logicOperator(FeatureFlagRule.LogicOperator.OR)
            .childRules(Arrays.asList(userRule, environmentRule))
            .active(true)
            .build();

        Map<String, Object> context = Map.of(
            "userId", "user999",
            "environment", "development"
        );

        boolean result = compositeRule.evaluate(context);

        assertThat(result).isFalse();
    }

    @Test
    void testCompositeRule_NestedComposite_EvaluatesCorrectly() {
        // Inner composite: (user1 OR user2)
        FeatureFlagRule innerComposite = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.COMPOSITE)
            .logicOperator(FeatureFlagRule.LogicOperator.OR)
            .childRules(Arrays.asList(
                FeatureFlagRule.builder()
                    .type(FeatureFlagRule.RuleType.USER)
                    .operator(FeatureFlagRule.RuleOperator.EQUALS)
                    .values(Arrays.asList("user1"))
                    .active(true)
                    .build(),
                FeatureFlagRule.builder()
                    .type(FeatureFlagRule.RuleType.USER)
                    .operator(FeatureFlagRule.RuleOperator.EQUALS)
                    .values(Arrays.asList("user2"))
                    .active(true)
                    .build()
            ))
            .active(true)
            .build();

        // Outer composite: (user1 OR user2) AND production
        FeatureFlagRule outerComposite = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.COMPOSITE)
            .logicOperator(FeatureFlagRule.LogicOperator.AND)
            .childRules(Arrays.asList(
                innerComposite,
                FeatureFlagRule.builder()
                    .type(FeatureFlagRule.RuleType.ENVIRONMENT)
                    .values(Arrays.asList("production"))
                    .active(true)
                    .build()
            ))
            .active(true)
            .build();

        Map<String, Object> context = Map.of(
            "userId", "user2",
            "environment", "production"
        );

        boolean result = outerComposite.evaluate(context);

        assertThat(result).isTrue();
    }

    // ==================== Active/Inactive Rule Tests ====================

    @Test
    void testInactiveRule_ReturnsFlase() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.PERCENTAGE)
            .percentage(100)
            .active(false)  // Rule is inactive
            .build();

        Map<String, Object> context = Map.of("userId", "user1");

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyContext_MostRulesReturnFalse() {
        Map<String, Object> emptyContext = new HashMap<>();

        FeatureFlagRule[] rules = {
            FeatureFlagRule.builder().type(FeatureFlagRule.RuleType.USER).active(true).build(),
            FeatureFlagRule.builder().type(FeatureFlagRule.RuleType.GROUP).active(true).build(),
            FeatureFlagRule.builder().type(FeatureFlagRule.RuleType.ENVIRONMENT).active(true).build(),
            FeatureFlagRule.builder().type(FeatureFlagRule.RuleType.PERCENTAGE).active(true).build(),
            FeatureFlagRule.builder().type(FeatureFlagRule.RuleType.GEOGRAPHIC).active(true).build(),
            FeatureFlagRule.builder().type(FeatureFlagRule.RuleType.DEVICE).active(true).build()
        };

        for (FeatureFlagRule rule : rules) {
            assertThat(rule.evaluate(emptyContext)).isFalse();
        }
    }

    @Test
    void testNullValues_HandledGracefully() {
        FeatureFlagRule rule = FeatureFlagRule.builder()
            .type(FeatureFlagRule.RuleType.USER)
            .operator(FeatureFlagRule.RuleOperator.IN)
            .values(null)  // Null values
            .active(true)
            .build();

        Map<String, Object> context = Map.of("userId", "user1");

        boolean result = rule.evaluate(context);

        assertThat(result).isFalse();
    }
}
