package com.waqiti.common.kafka.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * Consumer Group ID Configuration Strategy
 *
 * CRITICAL FIX (2025-11-22): Kafka consumer group ID management
 *
 * PROBLEM SOLVED:
 * - Multiple services were using the same default group ID "waqiti-consumer-group"
 * - This caused competing consumers, message loss, and partition rebalancing issues
 *
 * SOLUTION:
 * - Each service has a unique group ID: {spring.application.name}-{consumer.purpose}
 * - Format: "payment-service-transaction-processor"
 * - Ensures proper message distribution and consumer isolation
 *
 * USAGE:
 * 1. Inject this bean: @Autowired private ConsumerGroupIdConfiguration groupIdConfig;
 * 2. Get group ID: String groupId = groupIdConfig.getGroupIdForPurpose("transaction-processor");
 * 3. Use in @KafkaListener: @KafkaListener(groupId = "#{@consumerGroupIdConfiguration.getGroupIdForPurpose('transaction-processor')}")
 *
 * EXAMPLES:
 * - payment-service-transaction-processor
 * - wallet-service-balance-updater
 * - notification-service-email-sender
 * - fraud-detection-service-risk-scorer
 * - ledger-service-entry-recorder
 *
 * @author Waqiti Platform Team
 * @since 2025-11-22
 * @see <a href="https://kafka.apache.org/documentation/#consumerconfigs_group.id">Kafka Consumer Group Documentation</a>
 */
@Slf4j
@Configuration
public class ConsumerGroupIdConfiguration {

    @Value("${spring.application.name:unknown-service}")
    private String applicationName;

    @Value("${kafka.consumer.group-id-prefix:${spring.application.name}}")
    private String groupIdPrefix;

    @Value("${kafka.consumer.group-id-suffix:}")
    private String groupIdSuffix;

    @Value("${kafka.consumer.use-dynamic-group-ids:true}")
    private boolean useDynamicGroupIds;

    private static final String GROUP_ID_SEPARATOR = "-";
    private static final String DEFAULT_PURPOSE = "default";

    @PostConstruct
    public void init() {
        if (!useDynamicGroupIds) {
            log.warn("CRITICAL WARNING: Dynamic group IDs are disabled. " +
                    "This may cause consumer group conflicts across services. " +
                    "Set kafka.consumer.use-dynamic-group-ids=true to enable proper group ID management.");
        } else {
            log.info("Consumer Group ID Configuration initialized: " +
                    "prefix={}, suffix={}, service={}",
                    groupIdPrefix, groupIdSuffix, applicationName);
        }

        validateConfiguration();
    }

    /**
     * Generate a unique consumer group ID for a specific purpose
     *
     * @param purpose The consumer purpose (e.g., "transaction-processor", "notification-sender")
     * @return Unique group ID in format: {service}-{purpose}[-{suffix}]
     */
    public String getGroupIdForPurpose(String purpose) {
        if (!useDynamicGroupIds) {
            // Fallback to static group ID (not recommended for production)
            return groupIdPrefix;
        }

        // Validate purpose
        if (!StringUtils.hasText(purpose)) {
            log.warn("Empty purpose provided, using default");
            purpose = DEFAULT_PURPOSE;
        }

        // Sanitize purpose (remove special characters)
        purpose = sanitizePurpose(purpose);

        // Build group ID: {service}-{purpose}[-{suffix}]
        StringBuilder groupId = new StringBuilder();
        groupId.append(applicationName);
        groupId.append(GROUP_ID_SEPARATOR);
        groupId.append(purpose);

        if (StringUtils.hasText(groupIdSuffix)) {
            groupId.append(GROUP_ID_SEPARATOR);
            groupId.append(groupIdSuffix);
        }

        String finalGroupId = groupId.toString();
        log.debug("Generated consumer group ID: {} for purpose: {}", finalGroupId, purpose);

        return finalGroupId;
    }

    /**
     * Get the default consumer group ID for the service
     *
     * @return Default group ID: {service}-default
     */
    public String getDefaultGroupId() {
        return getGroupIdForPurpose(DEFAULT_PURPOSE);
    }

    /**
     * Generate a DLQ (Dead Letter Queue) consumer group ID
     *
     * @param purpose The original consumer purpose
     * @return DLQ group ID in format: {service}-{purpose}-dlq
     */
    public String getDlqGroupIdForPurpose(String purpose) {
        return getGroupIdForPurpose(purpose + GROUP_ID_SEPARATOR + "dlq");
    }

    /**
     * Validate the consumer group ID configuration
     * Ensures no conflicts and proper setup
     */
    private void validateConfiguration() {
        if (!StringUtils.hasText(applicationName) || "unknown-service".equals(applicationName)) {
            throw new IllegalStateException(
                "spring.application.name must be configured for proper consumer group ID management. " +
                "Set spring.application.name in application.yml or application.properties"
            );
        }

        if (applicationName.contains(GROUP_ID_SEPARATOR + GROUP_ID_SEPARATOR)) {
            throw new IllegalStateException(
                "Application name contains consecutive separators: " + applicationName
            );
        }

        log.info("Consumer Group ID configuration validated successfully for service: {}",
                applicationName);
    }

    /**
     * Sanitize the purpose string to ensure valid group ID
     * - Convert to lowercase
     * - Replace spaces with hyphens
     * - Remove special characters except hyphens
     *
     * @param purpose Raw purpose string
     * @return Sanitized purpose string
     */
    private String sanitizePurpose(String purpose) {
        if (!StringUtils.hasText(purpose)) {
            return DEFAULT_PURPOSE;
        }

        return purpose
            .toLowerCase()
            .trim()
            .replaceAll("\\s+", GROUP_ID_SEPARATOR)  // Replace spaces with hyphens
            .replaceAll("[^a-z0-9\\-]", "")         // Remove special characters
            .replaceAll("-+", GROUP_ID_SEPARATOR);   // Collapse consecutive hyphens
    }

    /**
     * Get all configured group IDs for this service
     * Useful for monitoring and debugging
     *
     * @return String representation of group ID configuration
     */
    public String getConfigurationSummary() {
        return String.format(
            "Consumer Group ID Configuration [service=%s, prefix=%s, suffix=%s, dynamic=%s]",
            applicationName, groupIdPrefix, groupIdSuffix, useDynamicGroupIds
        );
    }

    // Getters for monitoring and debugging

    public String getApplicationName() {
        return applicationName;
    }

    public String getGroupIdPrefix() {
        return groupIdPrefix;
    }

    public String getGroupIdSuffix() {
        return groupIdSuffix;
    }

    public boolean isUseDynamicGroupIds() {
        return useDynamicGroupIds;
    }
}
