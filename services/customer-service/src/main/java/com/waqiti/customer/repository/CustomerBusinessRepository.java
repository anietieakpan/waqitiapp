package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerBusiness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CustomerBusiness entity
 *
 * Provides data access methods for business customer profile management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerBusinessRepository extends JpaRepository<CustomerBusiness, UUID> {

    /**
     * Find business customer by customer ID
     *
     * @param customerId the unique customer identifier
     * @return Optional containing the business if found
     */
    Optional<CustomerBusiness> findByCustomerId(String customerId);

    /**
     * Check if business exists by customer ID
     *
     * @param customerId the unique customer identifier
     * @return true if business exists
     */
    boolean existsByCustomerId(String customerId);

    /**
     * Find businesses by business name
     *
     * @param businessName the business name
     * @param pageable pagination information
     * @return page of businesses
     */
    @Query("SELECT cb FROM CustomerBusiness cb WHERE LOWER(cb.businessName) LIKE LOWER(CONCAT('%', :businessName, '%'))")
    Page<CustomerBusiness> findByBusinessName(@Param("businessName") String businessName, Pageable pageable);

    /**
     * Find businesses by legal name
     *
     * @param legalName the legal name
     * @param pageable pagination information
     * @return page of businesses
     */
    @Query("SELECT cb FROM CustomerBusiness cb WHERE LOWER(cb.legalName) LIKE LOWER(CONCAT('%', :legalName, '%'))")
    Page<CustomerBusiness> findByLegalName(@Param("legalName") String legalName, Pageable pageable);

    /**
     * Search businesses by name (business name or legal name)
     *
     * @param searchTerm the search term
     * @param pageable pagination information
     * @return page of matching businesses
     */
    @Query("SELECT cb FROM CustomerBusiness cb WHERE " +
           "LOWER(cb.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(cb.legalName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<CustomerBusiness> searchByName(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find businesses by industry
     *
     * @param industry the industry
     * @param pageable pagination information
     * @return page of businesses
     */
    Page<CustomerBusiness> findByIndustry(String industry, Pageable pageable);

    /**
     * Find businesses by industry code
     *
     * @param industryCode the industry code
     * @param pageable pagination information
     * @return page of businesses
     */
    Page<CustomerBusiness> findByIndustryCode(String industryCode, Pageable pageable);

    /**
     * Find businesses by business type
     *
     * @param businessType the business type
     * @param pageable pagination information
     * @return page of businesses
     */
    Page<CustomerBusiness> findByBusinessType(String businessType, Pageable pageable);

    /**
     * Find businesses by country of incorporation
     *
     * @param countryCode the country code
     * @param pageable pagination information
     * @return page of businesses
     */
    Page<CustomerBusiness> findByCountryOfIncorporation(String countryCode, Pageable pageable);

    /**
     * Find publicly traded businesses
     *
     * @param pageable pagination information
     * @return page of publicly traded businesses
     */
    @Query("SELECT cb FROM CustomerBusiness cb WHERE cb.isPubliclyTraded = true")
    Page<CustomerBusiness> findPubliclyTradedBusinesses(Pageable pageable);

    /**
     * Find business by stock symbol
     *
     * @param stockSymbol the stock symbol
     * @return Optional containing the business if found
     */
    Optional<CustomerBusiness> findByStockSymbol(String stockSymbol);

    /**
     * Find business by registration number
     *
     * @param registrationNumber the registration number
     * @return Optional containing the business if found
     */
    Optional<CustomerBusiness> findByRegistrationNumber(String registrationNumber);

    /**
     * Find business by tax ID hash
     *
     * @param taxIdHash the tax ID hash
     * @return Optional containing the business if found
     */
    Optional<CustomerBusiness> findByTaxIdHash(String taxIdHash);

    /**
     * Find businesses incorporated within date range
     *
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of businesses
     */
    @Query("SELECT cb FROM CustomerBusiness cb WHERE cb.dateOfIncorporation BETWEEN :startDate AND :endDate")
    Page<CustomerBusiness> findByDateOfIncorporationBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    /**
     * Find businesses by employee count range
     *
     * @param minEmployees minimum number of employees
     * @param maxEmployees maximum number of employees
     * @param pageable pagination information
     * @return page of businesses
     */
    @Query("SELECT cb FROM CustomerBusiness cb WHERE cb.numberOfEmployees BETWEEN :min AND :max")
    Page<CustomerBusiness> findByEmployeeCountRange(
        @Param("min") Integer minEmployees,
        @Param("max") Integer maxEmployees,
        Pageable pageable
    );

    /**
     * Find small businesses (less than 50 employees)
     *
     * @param pageable pagination information
     * @return page of small businesses
     */
    @Query("SELECT cb FROM CustomerBusiness cb WHERE cb.numberOfEmployees < 50")
    Page<CustomerBusiness> findSmallBusinesses(Pageable pageable);

    /**
     * Find medium businesses (50-249 employees)
     *
     * @param pageable pagination information
     * @return page of medium businesses
     */
    @Query("SELECT cb FROM CustomerBusiness cb WHERE cb.numberOfEmployees >= 50 AND cb.numberOfEmployees < 250")
    Page<CustomerBusiness> findMediumBusinesses(Pageable pageable);

    /**
     * Find large businesses (250+ employees)
     *
     * @param pageable pagination information
     * @return page of large businesses
     */
    @Query("SELECT cb FROM CustomerBusiness cb WHERE cb.numberOfEmployees >= 250")
    Page<CustomerBusiness> findLargeBusinesses(Pageable pageable);

    /**
     * Count businesses by industry
     *
     * @param industry the industry
     * @return count of businesses
     */
    long countByIndustry(String industry);

    /**
     * Count businesses by business type
     *
     * @param businessType the business type
     * @return count of businesses
     */
    long countByBusinessType(String businessType);

    /**
     * Count publicly traded businesses
     *
     * @return count of publicly traded businesses
     */
    @Query("SELECT COUNT(cb) FROM CustomerBusiness cb WHERE cb.isPubliclyTraded = true")
    long countPubliclyTraded();
}
