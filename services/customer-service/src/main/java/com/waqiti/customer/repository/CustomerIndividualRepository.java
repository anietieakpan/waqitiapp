package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerIndividual;
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
 * Repository interface for CustomerIndividual entity
 *
 * Provides data access methods for individual customer profile management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerIndividualRepository extends JpaRepository<CustomerIndividual, UUID> {

    /**
     * Find individual customer by customer ID
     *
     * @param customerId the unique customer identifier
     * @return Optional containing the individual if found
     */
    Optional<CustomerIndividual> findByCustomerId(String customerId);

    /**
     * Check if individual exists by customer ID
     *
     * @param customerId the unique customer identifier
     * @return true if individual exists
     */
    boolean existsByCustomerId(String customerId);

    /**
     * Find individuals by first name and last name
     *
     * @param firstName the first name
     * @param lastName the last name
     * @return list of matching individuals
     */
    List<CustomerIndividual> findByFirstNameAndLastName(String firstName, String lastName);

    /**
     * Find individuals by last name
     *
     * @param lastName the last name
     * @param pageable pagination information
     * @return page of individuals
     */
    Page<CustomerIndividual> findByLastName(String lastName, Pageable pageable);

    /**
     * Search individuals by name (first or last name)
     *
     * @param searchTerm the search term
     * @param pageable pagination information
     * @return page of matching individuals
     */
    @Query("SELECT ci FROM CustomerIndividual ci WHERE " +
           "LOWER(ci.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(ci.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(ci.middleName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<CustomerIndividual> searchByName(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find individuals by nationality
     *
     * @param nationality the nationality code
     * @param pageable pagination information
     * @return page of individuals
     */
    Page<CustomerIndividual> findByNationality(String nationality, Pageable pageable);

    /**
     * Find individuals by country of residence
     *
     * @param countryCode the country code
     * @param pageable pagination information
     * @return page of individuals
     */
    Page<CustomerIndividual> findByCountryOfResidence(String countryCode, Pageable pageable);

    /**
     * Find individuals by employment status
     *
     * @param employmentStatus the employment status
     * @param pageable pagination information
     * @return page of individuals
     */
    Page<CustomerIndividual> findByEmploymentStatus(String employmentStatus, Pageable pageable);

    /**
     * Find individuals by occupation
     *
     * @param occupation the occupation
     * @param pageable pagination information
     * @return page of individuals
     */
    Page<CustomerIndividual> findByOccupation(String occupation, Pageable pageable);

    /**
     * Find individuals by date of birth
     *
     * @param dateOfBirth the date of birth
     * @return list of individuals
     */
    List<CustomerIndividual> findByDateOfBirth(LocalDate dateOfBirth);

    /**
     * Find individuals born between dates
     *
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of individuals
     */
    @Query("SELECT ci FROM CustomerIndividual ci WHERE ci.dateOfBirth BETWEEN :startDate AND :endDate")
    Page<CustomerIndividual> findByDateOfBirthBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    /**
     * Find individuals by employer name
     *
     * @param employerName the employer name
     * @param pageable pagination information
     * @return page of individuals
     */
    @Query("SELECT ci FROM CustomerIndividual ci WHERE LOWER(ci.employerName) LIKE LOWER(CONCAT('%', :employerName, '%'))")
    Page<CustomerIndividual> findByEmployerName(@Param("employerName") String employerName, Pageable pageable);

    /**
     * Find individuals by marital status
     *
     * @param maritalStatus the marital status
     * @param pageable pagination information
     * @return page of individuals
     */
    Page<CustomerIndividual> findByMaritalStatus(String maritalStatus, Pageable pageable);

    /**
     * Find individuals by gender
     *
     * @param gender the gender
     * @param pageable pagination information
     * @return page of individuals
     */
    Page<CustomerIndividual> findByGender(String gender, Pageable pageable);

    /**
     * Find individual by SSN hash
     *
     * @param ssnHash the SSN hash
     * @return Optional containing the individual if found
     */
    Optional<CustomerIndividual> findBySsnHash(String ssnHash);

    /**
     * Find individual by tax ID hash
     *
     * @param taxIdHash the tax ID hash
     * @return Optional containing the individual if found
     */
    Optional<CustomerIndividual> findByTaxIdHash(String taxIdHash);

    /**
     * Count individuals by employment status
     *
     * @param employmentStatus the employment status
     * @return count of individuals
     */
    long countByEmploymentStatus(String employmentStatus);

    /**
     * Count individuals by nationality
     *
     * @param nationality the nationality code
     * @return count of individuals
     */
    long countByNationality(String nationality);
}
