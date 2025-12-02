package com.waqiti.dispute.repository;

import com.waqiti.dispute.entity.DisputeEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for DisputeEvidence entities
 * Manages evidence attached to disputes
 */
@Repository
public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, String> {
    
    List<DisputeEvidence> findByDisputeId(String disputeId);
    
    long countByDisputeId(String disputeId);
}
