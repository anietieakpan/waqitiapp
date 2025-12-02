package com.waqiti.customer.repository;

import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.Customer.CustomerStatus;
import com.waqiti.customer.entity.Customer.CustomerType;
import com.waqiti.customer.entity.Customer.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Customer entity
 *
 * Provides data access methods for customer management operations.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /**
     * Find customer by customer ID
     *
     * @param customerId the unique customer identifier
     * @return Optional containing the customer if found
     */
    Optional<Customer> findByCustomerId(String customerId);

    /**
     * Check if customer exists by customer ID
     *
     * @param customerId the unique customer identifier
     * @return true if customer exists
     */
    boolean existsByCustomerId(String customerId);

    /**
     * Find all customers by type
     *
     * @param customerType the type of customer (INDIVIDUAL or BUSINESS)
     * @param pageable pagination information
     * @return page of customers
     */
    Page<Customer> findByCustomerType(CustomerType customerType, Pageable pageable);

    /**
     * Find all customers by status
     *
     * @param status the customer status
     * @param pageable pagination information
     * @return page of customers
     */
    Page<Customer> findByCustomerStatus(CustomerStatus status, Pageable pageable);

    /**
     * Find all active customers
     *
     * @param pageable pagination information
     * @return page of active customers
     */
    default Page<Customer> findAllActive(Pageable pageable) {
        return findByCustomerStatus(CustomerStatus.ACTIVE, pageable);
    }

    /**
     * Find all blocked customers
     *
     * @param pageable pagination information
     * @return page of blocked customers
     */
    @Query("SELECT c FROM Customer c WHERE c.isBlocked = true")
    Page<Customer> findAllBlocked(Pageable pageable);

    /**
     * Find customers by risk level
     *
     * @param riskLevel the risk level
     * @param pageable pagination information
     * @return page of customers
     */
    Page<Customer> findByRiskLevel(RiskLevel riskLevel, Pageable pageable);

    /**
     * Find high-risk customers (HIGH or CRITICAL)
     *
     * @param pageable pagination information
     * @return page of high-risk customers
     */
    @Query("SELECT c FROM Customer c WHERE c.riskLevel IN ('HIGH', 'CRITICAL') AND c.customerStatus = 'ACTIVE'")
    Page<Customer> findHighRiskCustomers(Pageable pageable);

    /**
     * Find customers by segment
     *
     * @param segment the customer segment
     * @param pageable pagination information
     * @return page of customers
     */
    Page<Customer> findByCustomerSegment(String segment, Pageable pageable);

    /**
     * Find customers onboarded within date range
     *
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of customers
     */
    @Query("SELECT c FROM Customer c WHERE c.onboardingDate BETWEEN :startDate AND :endDate")
    Page<Customer> findByOnboardingDateBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Find inactive customers (no activity in specified days)
     *
     * @param since the date threshold for inactivity
     * @param pageable pagination information
     * @return page of inactive customers
     */
    @Query("SELECT c FROM Customer c WHERE c.lastActivityAt < :since AND c.customerStatus = 'ACTIVE'")
    Page<Customer> findInactiveCustomers(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * Find customers pending KYC verification
     *
     * @param pageable pagination information
     * @return page of customers
     */
    @Query("SELECT c FROM Customer c WHERE c.kycStatus IN ('PENDING', 'IN_PROGRESS')")
    Page<Customer> findPendingKycCustomers(Pageable pageable);

    /**
     * Find customers pending AML verification
     *
     * @param pageable pagination information
     * @return page of customers
     */
    @Query("SELECT c FROM Customer c WHERE c.amlStatus IN ('PENDING', 'IN_PROGRESS', 'FLAGGED')")
    Page<Customer> findPendingAmlCustomers(Pageable pageable);

    /**
     * Find PEP (Politically Exposed Person) customers
     *
     * @param pageable pagination information
     * @return page of PEP customers
     */
    @Query("SELECT c FROM Customer c WHERE c.isPep = true")
    Page<Customer> findPepCustomers(Pageable pageable);

    /**
     * Find sanctioned customers
     *
     * @param pageable pagination information
     * @return page of sanctioned customers
     */
    @Query("SELECT c FROM Customer c WHERE c.isSanctioned = true")
    Page<Customer> findSanctionedCustomers(Pageable pageable);

    /**
     * Find customers by relationship manager
     *
     * @param managerId the relationship manager ID
     * @param pageable pagination information
     * @return page of customers
     */
    Page<Customer> findByRelationshipManagerId(String managerId, Pageable pageable);

    /**
     * Search customers by name (for individual customers)
     *
     * @param searchTerm the search term
     * @param pageable pagination information
     * @return page of customers
     */
    @Query("SELECT c FROM Customer c JOIN CustomerIndividual ci ON c.customerId = ci.customer.customerId " +
           "WHERE LOWER(ci.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(ci.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Customer> searchIndividualsByName(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Search customers by business name
     *
     * @param searchTerm the search term
     * @param pageable pagination information
     * @return page of customers
     */
    @Query("SELECT c FROM Customer c JOIN CustomerBusiness cb ON c.customerId = cb.customer.customerId " +
           "WHERE LOWER(cb.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(cb.legalName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Customer> searchBusinessesByName(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Count customers by status
     *
     * @param status the customer status
     * @return count of customers
     */
    long countByCustomerStatus(CustomerStatus status);

    /**
     * Count customers by type
     *
     * @param type the customer type
     * @return count of customers
     */
    long countByCustomerType(CustomerType type);

    /**
     * Count customers by risk level
     *
     * @param riskLevel the risk level
     * @return count of customers
     */
    long countByRiskLevel(RiskLevel riskLevel);

    /**
     * Find recently onboarded customers (last N days)
     *
     * @param since the date threshold
     * @param pageable pagination information
     * @return page of customers
     */
    @Query("SELECT c FROM Customer c WHERE c.onboardingDate >= :since ORDER BY c.onboarding Date DESC")
    Page<Customer> findRecentlyOnboarded(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * Find customers with tags
     *
     * @param tag the tag to search for
     * @param pageable pagination information
     * @return page of customers
     */
    @Query(value = "SELECT * FROM customer WHERE :tag = ANY(tags)", nativeQuery = true)
    Page<Customer> findByTag(@Param("tag") String tag, Pageable pageable);
}
