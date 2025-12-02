package com.waqiti.savings.repository;

/**
 * Alias repository for SavingsContributionRepository.
 * Provides backward compatibility for code referencing ContributionRepository.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
public interface ContributionRepository extends SavingsContributionRepository {
    // All methods inherited from SavingsContributionRepository
    // This is just an alias for consistent naming across services
}
