package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerSatisfaction;
import com.waqiti.customer.entity.CustomerSatisfaction.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CustomerSatisfaction entity
 *
 * Provides data access methods for customer satisfaction tracking.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerSatisfactionRepository extends JpaRepository<CustomerSatisfaction, UUID> {

    /**
     * Find satisfaction by satisfaction ID
     *
     * @param satisfactionId the unique satisfaction identifier
     * @return Optional containing the satisfaction if found
     */
    Optional<CustomerSatisfaction> findBySatisfactionId(String satisfactionId);

    /**
     * Find all satisfaction records for a customer
     *
     * @param customerId the customer ID
     * @return list of satisfaction records
     */
    List<CustomerSatisfaction> findByCustomerId(String customerId);

    /**
     * Find satisfaction by customer ID with pagination
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of satisfaction records
     */
    Page<CustomerSatisfaction> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Find latest satisfaction for a customer
     *
     * @param customerId the customer ID
     * @return Optional containing the latest satisfaction
     */
    @Query("SELECT s FROM CustomerSatisfaction s WHERE s.customerId = :customerId ORDER BY s.surveyDate DESC LIMIT 1")
    Optional<CustomerSatisfaction> findLatestByCustomerId(@Param("customerId") String customerId);

    /**
     * Find satisfaction by survey type
     *
     * @param surveyType the survey type
     * @param pageable pagination information
     * @return page of satisfaction records
     */
    Page<CustomerSatisfaction> findBySurveyType(SurveyType surveyType, Pageable pageable);

    /**
     * Find satisfaction by survey channel
     *
     * @param surveyChannel the survey channel
     * @param pageable pagination information
     * @return page of satisfaction records
     */
    Page<CustomerSatisfaction> findBySurveyChannel(SurveyChannel surveyChannel, Pageable pageable);

    /**
     * Find satisfaction within date range
     *
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of satisfaction records
     */
    @Query("SELECT s FROM CustomerSatisfaction s WHERE s.surveyDate BETWEEN :startDate AND :endDate " +
           "ORDER BY s.surveyDate DESC")
    Page<CustomerSatisfaction> findByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    /**
     * Find satisfied customers (CSAT >= 4)
     *
     * @param pageable pagination information
     * @return page of satisfied customers
     */
    @Query("SELECT s FROM CustomerSatisfaction s WHERE s.csatScore >= 4 ORDER BY s.surveyDate DESC")
    Page<CustomerSatisfaction> findSatisfiedCustomers(Pageable pageable);

    /**
     * Find dissatisfied customers (CSAT <= 2)
     *
     * @param pageable pagination information
     * @return page of dissatisfied customers
     */
    @Query("SELECT s FROM CustomerSatisfaction s WHERE s.csatScore <= 2 ORDER BY s.surveyDate DESC")
    Page<CustomerSatisfaction> findDissatisfiedCustomers(Pageable pageable);

    /**
     * Find promoters (NPS 9-10)
     *
     * @param pageable pagination information
     * @return page of promoters
     */
    @Query("SELECT s FROM CustomerSatisfaction s WHERE s.npsScore >= 9 ORDER BY s.surveyDate DESC")
    Page<CustomerSatisfaction> findPromoters(Pageable pageable);

    /**
     * Find detractors (NPS 0-6)
     *
     * @param pageable pagination information
     * @return page of detractors
     */
    @Query("SELECT s FROM CustomerSatisfaction s WHERE s.npsScore <= 6 ORDER BY s.surveyDate DESC")
    Page<CustomerSatisfaction> findDetractors(Pageable pageable);

    /**
     * Find passives (NPS 7-8)
     *
     * @param pageable pagination information
     * @return page of passives
     */
    @Query("SELECT s FROM CustomerSatisfaction s WHERE s.npsScore BETWEEN 7 AND 8 ORDER BY s.surveyDate DESC")
    Page<CustomerSatisfaction> findPassives(Pageable pageable);

    /**
     * Find low effort experiences (CES 1-3)
     *
     * @param pageable pagination information
     * @return page of low effort experiences
     */
    @Query("SELECT s FROM CustomerSatisfaction s WHERE s.cesScore <= 3 ORDER BY s.surveyDate DESC")
    Page<CustomerSatisfaction> findLowEffortExperiences(Pageable pageable);

    /**
     * Find high effort experiences (CES 5-7)
     *
     * @param pageable pagination information
     * @return page of high effort experiences
     */
    @Query("SELECT s FROM CustomerSatisfaction s WHERE s.cesScore >= 5 ORDER BY s.surveyDate DESC")
    Page<CustomerSatisfaction> findHighEffortExperiences(Pageable pageable);

    /**
     * Find highly satisfied customers (overall >= 70)
     *
     * @param threshold the satisfaction threshold
     * @param pageable pagination information
     * @return page of highly satisfied customers
     */
    @Query("SELECT s FROM CustomerSatisfaction s WHERE s.overallSatisfaction >= :threshold " +
           "ORDER BY s.overallSatisfaction DESC")
    Page<CustomerSatisfaction> findHighlySatisfied(@Param("threshold") BigDecimal threshold, Pageable pageable);

    /**
     * Find low satisfaction customers (overall < 50)
     *
     * @param threshold the satisfaction threshold
     * @param pageable pagination information
     * @return page of low satisfaction customers
     */
    @Query("SELECT s FROM CustomerSatisfaction s WHERE s.overallSatisfaction < :threshold " +
           "ORDER BY s.overallSatisfaction ASC")
    Page<CustomerSatisfaction> findLowSatisfaction(@Param("threshold") BigDecimal threshold, Pageable pageable);

    /**
     * Find satisfaction by CSAT score
     *
     * @param csatScore the CSAT score
     * @param pageable pagination information
     * @return page of satisfaction records
     */
    Page<CustomerSatisfaction> findByCsatScore(Integer csatScore, Pageable pageable);

    /**
     * Find satisfaction by NPS score
     *
     * @param npsScore the NPS score
     * @param pageable pagination information
     * @return page of satisfaction records
     */
    Page<CustomerSatisfaction> findByNpsScore(Integer npsScore, Pageable pageable);

    /**
     * Find satisfaction by CES score
     *
     * @param cesScore the CES score
     * @param pageable pagination information
     * @return page of satisfaction records
     */
    Page<CustomerSatisfaction> findByCesScore(Integer cesScore, Pageable pageable);

    /**
     * Count satisfaction records by customer ID
     *
     * @param customerId the customer ID
     * @return count of satisfaction records
     */
    long countByCustomerId(String customerId);

    /**
     * Count by survey type
     *
     * @param surveyType the survey type
     * @return count of satisfaction records
     */
    long countBySurveyType(SurveyType surveyType);

    /**
     * Count satisfied customers
     *
     * @return count of satisfied customers
     */
    @Query("SELECT COUNT(s) FROM CustomerSatisfaction s WHERE s.csatScore >= 4")
    long countSatisfied();

    /**
     * Count dissatisfied customers
     *
     * @return count of dissatisfied customers
     */
    @Query("SELECT COUNT(s) FROM CustomerSatisfaction s WHERE s.csatScore <= 2")
    long countDissatisfied();

    /**
     * Count promoters
     *
     * @return count of promoters
     */
    @Query("SELECT COUNT(s) FROM CustomerSatisfaction s WHERE s.npsScore >= 9")
    long countPromoters();

    /**
     * Count detractors
     *
     * @return count of detractors
     */
    @Query("SELECT COUNT(s) FROM CustomerSatisfaction s WHERE s.npsScore <= 6")
    long countDetractors();

    /**
     * Count passives
     *
     * @return count of passives
     */
    @Query("SELECT COUNT(s) FROM CustomerSatisfaction s WHERE s.npsScore BETWEEN 7 AND 8")
    long countPassives();

    /**
     * Calculate average CSAT score
     *
     * @return average CSAT score
     */
    @Query("SELECT AVG(s.csatScore) FROM CustomerSatisfaction s WHERE s.csatScore IS NOT NULL")
    Double calculateAverageCsat();

    /**
     * Calculate average NPS score
     *
     * @return average NPS score
     */
    @Query("SELECT AVG(s.npsScore) FROM CustomerSatisfaction s WHERE s.npsScore IS NOT NULL")
    Double calculateAverageNps();

    /**
     * Calculate average CES score
     *
     * @return average CES score
     */
    @Query("SELECT AVG(s.cesScore) FROM CustomerSatisfaction s WHERE s.cesScore IS NOT NULL")
    Double calculateAverageCes();

    /**
     * Calculate average overall satisfaction
     *
     * @return average overall satisfaction
     */
    @Query("SELECT AVG(s.overallSatisfaction) FROM CustomerSatisfaction s WHERE s.overallSatisfaction IS NOT NULL")
    BigDecimal calculateAverageOverallSatisfaction();

    /**
     * Calculate average service quality score
     *
     * @return average service quality score
     */
    @Query("SELECT AVG(s.serviceQualityScore) FROM CustomerSatisfaction s WHERE s.serviceQualityScore IS NOT NULL")
    BigDecimal calculateAverageServiceQuality();

    /**
     * Calculate average product quality score
     *
     * @return average product quality score
     */
    @Query("SELECT AVG(s.productQualityScore) FROM CustomerSatisfaction s WHERE s.productQualityScore IS NOT NULL")
    BigDecimal calculateAverageProductQuality();

    /**
     * Calculate average value for money score
     *
     * @return average value for money score
     */
    @Query("SELECT AVG(s.valueForMoneyScore) FROM CustomerSatisfaction s WHERE s.valueForMoneyScore IS NOT NULL")
    BigDecimal calculateAverageValueForMoney();

    /**
     * Calculate NPS (Net Promoter Score)
     *
     * @return NPS percentage (-100 to 100)
     */
    @Query("SELECT (CAST(SUM(CASE WHEN s.npsScore >= 9 THEN 1 ELSE 0 END) AS double) / COUNT(s) * 100) - " +
           "(CAST(SUM(CASE WHEN s.npsScore <= 6 THEN 1 ELSE 0 END) AS double) / COUNT(s) * 100) " +
           "FROM CustomerSatisfaction s WHERE s.npsScore IS NOT NULL")
    Double calculateNPS();

    /**
     * Calculate CSAT percentage (% satisfied = CSAT 4-5)
     *
     * @return CSAT percentage
     */
    @Query("SELECT CAST(SUM(CASE WHEN s.csatScore >= 4 THEN 1 ELSE 0 END) AS double) / COUNT(s) * 100 " +
           "FROM CustomerSatisfaction s WHERE s.csatScore IS NOT NULL")
    Double calculateCsatPercentage();

    /**
     * Get average response time in seconds
     *
     * @return average response time
     */
    @Query("SELECT AVG(s.responseTimeSeconds) FROM CustomerSatisfaction s WHERE s.responseTimeSeconds IS NOT NULL")
    Double getAverageResponseTime();
}
