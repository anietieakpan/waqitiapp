package com.waqiti.common.security.awareness.repository;

import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;

import com.waqiti.common.security.awareness.domain.PhishingTestResult;

import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PhishingTestResult entities
 *
 * @author Waqiti Platform Team
 */
@Repository
public interface PhishingTestResultRepository extends JpaRepository<PhishingTestResult, UUID> {

    /**
     * Find results by campaign
     */
    List<PhishingTestResult> findByCampaignId(UUID campaignId);

    /**
     * Find results by employee
     */
    List<PhishingTestResult> findByEmployeeId(UUID employeeId);

    /**
     * Find result by campaign and employee
     */
    Optional<PhishingTestResult> findByCampaignIdAndEmployeeId(UUID campaignId, UUID employeeId);

    /**
     * Count failed tests for employee (clicked or submitted data)
     */
    @Query("SELECT COUNT(r) FROM PhishingTestResult r WHERE r.employee.id = :employeeId " +
            "AND (r.linkClicked = true OR r.dataSubmitted = true)")
    Long countFailedTestsByEmployeeId(@Param("employeeId") UUID employeeId);

    /**
     * Count passed tests for employee (reported or no action)
     */
    @Query("SELECT COUNT(r) FROM PhishingTestResult r WHERE r.employee.id = :employeeId " +
            "AND (r.reportedAsPhishing = true OR (r.linkClicked = false AND r.dataSubmitted = false))")
    Long countPassedTestsByEmployeeId(@Param("employeeId") UUID employeeId);

    /**
     * Count all phishing tests for employee
     */
    long countByEmployeeId(UUID employeeId);

    /**
     * Count phishing tests by employee and result
     */
    long countByEmployeeIdAndResult(UUID employeeId, PhishingResult result);

    /**
     * Find phishing test result by tracking token
     */
    Optional<PhishingTestResult> findByTrackingToken(String trackingToken);

    /**
     * Count results by campaign where email was opened
     */
    long countByCampaign_IdAndEmailOpenedAtIsNotNull(UUID campaignId);

    /**
     * Count results by campaign where link was clicked
     */
    long countByCampaign_IdAndLinkClickedAtIsNotNull(UUID campaignId);

    /**
     * Count results by campaign where data was submitted
     */
    long countByCampaign_IdAndDataSubmittedAtIsNotNull(UUID campaignId);

    /**
     * Count results by campaign where phishing was reported
     */
    long countByCampaign_IdAndReportedAtIsNotNull(UUID campaignId);
}