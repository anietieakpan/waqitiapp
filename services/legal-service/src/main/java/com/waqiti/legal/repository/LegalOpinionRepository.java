package com.waqiti.legal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legal Opinion Repository
 *
 * Complete data access layer for LegalOpinion entities with custom query methods
 * Supports legal advice, memoranda, and opinion tracking
 *
 * Note: This repository is designed for a LegalOpinion entity that should be created
 * to track legal opinions and advice
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface LegalOpinionRepository extends JpaRepository<LegalOpinion, UUID> {

    /**
     * Find opinion by opinion ID
     */
    Optional<LegalOpinion> findByOpinionId(String opinionId);

    /**
     * Find opinions by opinion type
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.opinionType = :opinionType")
    List<LegalOpinion> findByOpinionType(@Param("opinionType") String opinionType);

    /**
     * Find opinions by subject matter
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.subjectMatter = :subjectMatter")
    List<LegalOpinion> findBySubjectMatter(@Param("subjectMatter") String subjectMatter);

    /**
     * Find opinions by status
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.status = :status")
    List<LegalOpinion> findByStatus(@Param("status") String status);

    /**
     * Find draft opinions
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.status = 'DRAFT'")
    List<LegalOpinion> findDraftOpinions();

    /**
     * Find finalized opinions
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.status = 'FINALIZED' " +
           "AND o.finalizedDate IS NOT NULL")
    List<LegalOpinion> findFinalizedOpinions();

    /**
     * Find opinions by author
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.authorId = :authorId")
    List<LegalOpinion> findByAuthorId(@Param("authorId") String authorId);

    /**
     * Find opinions by reviewing attorney
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.reviewingAttorneyId = :attorneyId")
    List<LegalOpinion> findByReviewingAttorneyId(@Param("attorneyId") String attorneyId);

    /**
     * Find opinions by requesting party
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.requestingPartyId = :partyId")
    List<LegalOpinion> findByRequestingPartyId(@Param("partyId") String partyId);

    /**
     * Find opinions by related case
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.relatedCaseId = :caseId")
    List<LegalOpinion> findByRelatedCaseId(@Param("caseId") String caseId);

    /**
     * Find opinions by related contract
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.relatedContractId = :contractId")
    List<LegalOpinion> findByRelatedContractId(@Param("contractId") String contractId);

    /**
     * Find opinions by jurisdiction
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.jurisdiction = :jurisdiction")
    List<LegalOpinion> findByJurisdiction(@Param("jurisdiction") String jurisdiction);

    /**
     * Find opinions by legal area
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.legalArea = :legalArea")
    List<LegalOpinion> findByLegalArea(@Param("legalArea") String legalArea);

    /**
     * Find opinions by risk level
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.riskLevel = :riskLevel " +
           "ORDER BY o.issuedDate DESC")
    List<LegalOpinion> findByRiskLevel(@Param("riskLevel") String riskLevel);

    /**
     * Find high-risk opinions
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.riskLevel IN ('HIGH', 'CRITICAL') " +
           "ORDER BY o.riskLevel DESC, o.issuedDate DESC")
    List<LegalOpinion> findHighRiskOpinions();

    /**
     * Find opinions pending review
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.status = 'PENDING_REVIEW' " +
           "AND o.reviewingAttorneyId IS NOT NULL")
    List<LegalOpinion> findPendingReview();

    /**
     * Find reviewed opinions
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.reviewed = true " +
           "AND o.reviewedDate IS NOT NULL")
    List<LegalOpinion> findReviewedOpinions();

    /**
     * Find confidential opinions
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.confidential = true")
    List<LegalOpinion> findConfidentialOpinions();

    /**
     * Find privileged opinions (attorney-client privilege)
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.privileged = true")
    List<LegalOpinion> findPrivilegedOpinions();

    /**
     * Find opinions issued within date range
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.issuedDate BETWEEN :startDate AND :endDate " +
           "ORDER BY o.issuedDate DESC")
    List<LegalOpinion> findByIssuedDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find opinions requested within date range
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.requestedDate BETWEEN :startDate AND :endDate " +
           "ORDER BY o.requestedDate DESC")
    List<LegalOpinion> findByRequestedDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find opinions with favorable recommendation
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.recommendation = 'FAVORABLE' OR o.recommendation = 'PROCEED'")
    List<LegalOpinion> findFavorableOpinions();

    /**
     * Find opinions with unfavorable recommendation
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.recommendation = 'UNFAVORABLE' OR o.recommendation = 'DO_NOT_PROCEED'")
    List<LegalOpinion> findUnfavorableOpinions();

    /**
     * Find opinions by recommendation
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.recommendation = :recommendation")
    List<LegalOpinion> findByRecommendation(@Param("recommendation") String recommendation);

    /**
     * Find opinions requiring follow-up
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.followUpRequired = true " +
           "AND o.followUpCompleted = false")
    List<LegalOpinion> findRequiringFollowUp();

    /**
     * Find opinions with follow-up completed
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.followUpRequired = true " +
           "AND o.followUpCompleted = true")
    List<LegalOpinion> findWithCompletedFollowUp();

    /**
     * Find opinions by precedent cited
     */
    @Query("SELECT o FROM LegalOpinion o WHERE :precedentId MEMBER OF o.precedentsCited")
    List<LegalOpinion> findByPrecedentCited(@Param("precedentId") String precedentId);

    /**
     * Find opinions by statute cited
     */
    @Query("SELECT o FROM LegalOpinion o WHERE :statuteId MEMBER OF o.statutesCited")
    List<LegalOpinion> findByStatuteCited(@Param("statuteId") String statuteId);

    /**
     * Find opinions with regulatory citations
     */
    @Query("SELECT o FROM LegalOpinion o WHERE SIZE(o.regulationsCited) > 0")
    List<LegalOpinion> findOpinionsWithRegulatoryCitations();

    /**
     * Find superseded opinions
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.supersededBy IS NOT NULL")
    List<LegalOpinion> findSupersededOpinions();

    /**
     * Find current (not superseded) opinions
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.supersededBy IS NULL " +
           "AND o.status = 'FINALIZED'")
    List<LegalOpinion> findCurrentOpinions();

    /**
     * Find opinions by law firm
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.lawFirm = :lawFirm")
    List<LegalOpinion> findByLawFirm(@Param("lawFirm") String lawFirm);

    /**
     * Find external opinions (from external counsel)
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.externalCounsel = true")
    List<LegalOpinion> findExternalOpinions();

    /**
     * Find internal opinions
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.externalCounsel = false OR o.externalCounsel IS NULL")
    List<LegalOpinion> findInternalOpinions();

    /**
     * Count opinions by status
     */
    @Query("SELECT COUNT(o) FROM LegalOpinion o WHERE o.status = :status")
    long countByStatus(@Param("status") String status);

    /**
     * Count opinions by type
     */
    @Query("SELECT COUNT(o) FROM LegalOpinion o WHERE o.opinionType = :opinionType")
    long countByOpinionType(@Param("opinionType") String opinionType);

    /**
     * Count opinions by author
     */
    @Query("SELECT COUNT(o) FROM LegalOpinion o WHERE o.authorId = :authorId")
    long countByAuthorId(@Param("authorId") String authorId);

    /**
     * Check if opinion ID exists
     */
    boolean existsByOpinionId(String opinionId);

    /**
     * Find opinions requiring immediate attention
     */
    @Query("SELECT o FROM LegalOpinion o WHERE " +
           "(o.status = 'PENDING_REVIEW' AND o.reviewDeadline < :currentDate) " +
           "OR (o.riskLevel IN ('HIGH', 'CRITICAL')) " +
           "OR (o.followUpRequired = true AND o.followUpCompleted = false AND o.followUpDueDate < :currentDate) " +
           "ORDER BY o.riskLevel DESC, o.reviewDeadline ASC")
    List<LegalOpinion> findRequiringImmediateAttention(@Param("currentDate") LocalDate currentDate);

    /**
     * Search opinions by title or summary
     */
    @Query("SELECT o FROM LegalOpinion o WHERE LOWER(o.opinionTitle) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(o.summary) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<LegalOpinion> searchOpinions(@Param("searchTerm") String searchTerm);

    /**
     * Find opinions by created by user
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.createdBy = :createdBy")
    List<LegalOpinion> findByCreatedBy(@Param("createdBy") String createdBy);

    /**
     * Get opinion statistics by legal area
     */
    @Query("SELECT o.legalArea, COUNT(*), " +
           "COUNT(CASE WHEN o.recommendation IN ('FAVORABLE', 'PROCEED') THEN 1 END), " +
           "COUNT(CASE WHEN o.riskLevel IN ('HIGH', 'CRITICAL') THEN 1 END) " +
           "FROM LegalOpinion o WHERE o.status = 'FINALIZED' " +
           "GROUP BY o.legalArea")
    List<Object[]> getOpinionStatisticsByLegalArea();

    /**
     * Find opinions cited in other opinions
     */
    @Query("SELECT o FROM LegalOpinion o WHERE :opinionId MEMBER OF o.relatedOpinionIds")
    List<LegalOpinion> findOpinionsCiting(@Param("opinionId") String opinionId);

    /**
     * Find opinions updated after specific date
     */
    @Query("SELECT o FROM LegalOpinion o WHERE o.updatedAt > :afterDate " +
           "ORDER BY o.updatedAt DESC")
    List<LegalOpinion> findUpdatedAfter(@Param("afterDate") LocalDateTime afterDate);
}

/**
 * Placeholder class for LegalOpinion entity
 * This should be created as a proper domain entity in com.waqiti.legal.domain package
 */
class LegalOpinion {
    private UUID id;
    private String opinionId;
    private String opinionType;
    private String opinionTitle;
    private String subjectMatter;
    private String status;
    private String authorId;
    private String reviewingAttorneyId;
    private String requestingPartyId;
    private String relatedCaseId;
    private String relatedContractId;
    private String jurisdiction;
    private String legalArea;
    private String riskLevel;
    private Boolean reviewed;
    private LocalDate reviewedDate;
    private Boolean confidential;
    private Boolean privileged;
    private LocalDate issuedDate;
    private LocalDate requestedDate;
    private LocalDate finalizedDate;
    private String recommendation;
    private Boolean followUpRequired;
    private Boolean followUpCompleted;
    private LocalDate followUpDueDate;
    private List<String> precedentsCited;
    private List<String> statutesCited;
    private List<String> regulationsCited;
    private String supersededBy;
    private String lawFirm;
    private Boolean externalCounsel;
    private String createdBy;
    private LocalDate reviewDeadline;
    private String summary;
    private List<String> relatedOpinionIds;
    private LocalDateTime updatedAt;
}
