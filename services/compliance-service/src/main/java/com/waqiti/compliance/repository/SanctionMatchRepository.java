package com.waqiti.compliance.repository;

import com.waqiti.compliance.domain.SanctionMatch;
import com.waqiti.compliance.domain.SanctionMatch.MatchStatus;
import com.waqiti.compliance.domain.SanctionMatch.SanctionListType;
import com.waqiti.compliance.domain.AMLRiskLevel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for sanction match records
 */
@Repository
public interface SanctionMatchRepository extends MongoRepository<SanctionMatch, String> {

    /**
     * Find all matches for a screening
     */
    List<SanctionMatch> findByScreeningId(String screeningId);

    /**
     * Find all matches for an entity
     */
    List<SanctionMatch> findByEntityIdOrderByCreatedAtDesc(String entityId);

    /**
     * Find matches by status
     */
    List<SanctionMatch> findByMatchStatus(MatchStatus status);

    /**
     * Find matches by list type
     */
    List<SanctionMatch> findByListType(SanctionListType listType);

    /**
     * Find matches by risk level
     */
    List<SanctionMatch> findByRiskLevel(AMLRiskLevel riskLevel);

    /**
     * Find high-risk matches pending review
     */
    @Query("{ 'riskLevel': { $in: ['HIGH', 'CRITICAL', 'PROHIBITED'] }, 'matchStatus': 'PENDING_REVIEW' }")
    List<SanctionMatch> findHighRiskMatchesPendingReview();

    /**
     * Find false positives
     */
    @Query("{ 'falsePositive': true }")
    List<SanctionMatch> findFalsePositives();

    /**
     * Find matches that resulted in blocking
     */
    @Query("{ 'transactionBlocked': true }")
    List<SanctionMatch> findBlockedMatches();

    /**
     * Count matches for entity
     */
    long countByEntityId(String entityId);
}
