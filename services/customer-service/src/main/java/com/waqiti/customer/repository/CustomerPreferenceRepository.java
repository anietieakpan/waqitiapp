package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerPreference;
import com.waqiti.customer.entity.CustomerPreference.StatementDelivery;
import com.waqiti.customer.entity.CustomerPreference.StatementFrequency;
import com.waqiti.customer.entity.CustomerPreference.TwoFactorMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CustomerPreference entity
 *
 * Provides data access methods for customer preference management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerPreferenceRepository extends JpaRepository<CustomerPreference, UUID> {

    /**
     * Find preference by customer ID
     *
     * @param customerId the customer ID
     * @return Optional containing the preference if found
     */
    Optional<CustomerPreference> findByCustomerId(String customerId);

    /**
     * Check if preference exists by customer ID
     *
     * @param customerId the customer ID
     * @return true if preference exists
     */
    boolean existsByCustomerId(String customerId);

    /**
     * Find customers with email notifications enabled
     *
     * @param pageable pagination information
     * @return page of preferences
     */
    @Query("SELECT p FROM CustomerPreference p WHERE p.notificationEmail = true")
    Page<CustomerPreference> findWithEmailNotificationsEnabled(Pageable pageable);

    /**
     * Find customers with SMS notifications enabled
     *
     * @param pageable pagination information
     * @return page of preferences
     */
    @Query("SELECT p FROM CustomerPreference p WHERE p.notificationSms = true")
    Page<CustomerPreference> findWithSmsNotificationsEnabled(Pageable pageable);

    /**
     * Find customers with push notifications enabled
     *
     * @param pageable pagination information
     * @return page of preferences
     */
    @Query("SELECT p FROM CustomerPreference p WHERE p.notificationPush = true")
    Page<CustomerPreference> findWithPushNotificationsEnabled(Pageable pageable);

    /**
     * Find customers with marketing emails enabled
     *
     * @param pageable pagination information
     * @return page of preferences
     */
    @Query("SELECT p FROM CustomerPreference p WHERE p.marketingEmail = true")
    Page<CustomerPreference> findWithMarketingEmailEnabled(Pageable pageable);

    /**
     * Find customers with marketing SMS enabled
     *
     * @param pageable pagination information
     * @return page of preferences
     */
    @Query("SELECT p FROM CustomerPreference p WHERE p.marketingSms = true")
    Page<CustomerPreference> findWithMarketingSmsEnabled(Pageable pageable);

    /**
     * Find customers with two-factor authentication enabled
     *
     * @param pageable pagination information
     * @return page of preferences
     */
    @Query("SELECT p FROM CustomerPreference p WHERE p.twoFactorEnabled = true")
    Page<CustomerPreference> findWithTwoFactorEnabled(Pageable pageable);

    /**
     * Find customers with biometric authentication enabled
     *
     * @param pageable pagination information
     * @return page of preferences
     */
    @Query("SELECT p FROM CustomerPreference p WHERE p.biometricEnabled = true")
    Page<CustomerPreference> findWithBiometricEnabled(Pageable pageable);

    /**
     * Find preferences by statement delivery method
     *
     * @param statementDelivery the statement delivery method
     * @param pageable pagination information
     * @return page of preferences
     */
    Page<CustomerPreference> findByStatementDelivery(StatementDelivery statementDelivery, Pageable pageable);

    /**
     * Find preferences by statement frequency
     *
     * @param statementFrequency the statement frequency
     * @param pageable pagination information
     * @return page of preferences
     */
    Page<CustomerPreference> findByStatementFrequency(StatementFrequency statementFrequency, Pageable pageable);

    /**
     * Find preferences by two-factor method
     *
     * @param twoFactorMethod the two-factor method
     * @param pageable pagination information
     * @return page of preferences
     */
    Page<CustomerPreference> findByTwoFactorMethod(TwoFactorMethod twoFactorMethod, Pageable pageable);

    /**
     * Find customers with all notifications disabled
     *
     * @param pageable pagination information
     * @return page of preferences
     */
    @Query("SELECT p FROM CustomerPreference p WHERE " +
           "p.notificationEmail = false AND " +
           "p.notificationSms = false AND " +
           "p.notificationPush = false AND " +
           "p.notificationInApp = false")
    Page<CustomerPreference> findWithAllNotificationsDisabled(Pageable pageable);

    /**
     * Find customers with all marketing disabled
     *
     * @param pageable pagination information
     * @return page of preferences
     */
    @Query("SELECT p FROM CustomerPreference p WHERE " +
           "p.marketingEmail = false AND " +
           "p.marketingSms = false")
    Page<CustomerPreference> findWithAllMarketingDisabled(Pageable pageable);

    /**
     * Find customers with all marketing enabled
     *
     * @param pageable pagination information
     * @return page of preferences
     */
    @Query("SELECT p FROM CustomerPreference p WHERE " +
           "p.marketingEmail = true AND " +
           "p.marketingSms = true")
    Page<CustomerPreference> findWithAllMarketingEnabled(Pageable pageable);

    /**
     * Count customers with email notifications enabled
     *
     * @return count of customers
     */
    @Query("SELECT COUNT(p) FROM CustomerPreference p WHERE p.notificationEmail = true")
    long countWithEmailNotificationsEnabled();

    /**
     * Count customers with SMS notifications enabled
     *
     * @return count of customers
     */
    @Query("SELECT COUNT(p) FROM CustomerPreference p WHERE p.notificationSms = true")
    long countWithSmsNotificationsEnabled();

    /**
     * Count customers with two-factor enabled
     *
     * @return count of customers
     */
    @Query("SELECT COUNT(p) FROM CustomerPreference p WHERE p.twoFactorEnabled = true")
    long countWithTwoFactorEnabled();

    /**
     * Count customers with biometric enabled
     *
     * @return count of customers
     */
    @Query("SELECT COUNT(p) FROM CustomerPreference p WHERE p.biometricEnabled = true")
    long countWithBiometricEnabled();

    /**
     * Count preferences by statement delivery method
     *
     * @param statementDelivery the statement delivery method
     * @return count of preferences
     */
    long countByStatementDelivery(StatementDelivery statementDelivery);

    /**
     * Count preferences by statement frequency
     *
     * @param statementFrequency the statement frequency
     * @return count of preferences
     */
    long countByStatementFrequency(StatementFrequency statementFrequency);
}
