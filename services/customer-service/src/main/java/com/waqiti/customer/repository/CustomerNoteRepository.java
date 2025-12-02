package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerNote;
import com.waqiti.customer.entity.CustomerNote.NoteType;
import com.waqiti.customer.entity.CustomerNote.Priority;
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
 * Repository interface for CustomerNote entity
 *
 * Provides data access methods for customer note management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerNoteRepository extends JpaRepository<CustomerNote, UUID> {

    /**
     * Find note by note ID
     *
     * @param noteId the unique note identifier
     * @return Optional containing the note if found
     */
    Optional<CustomerNote> findByNoteId(String noteId);

    /**
     * Find all notes for a customer
     *
     * @param customerId the customer ID
     * @return list of notes
     */
    List<CustomerNote> findByCustomerId(String customerId);

    /**
     * Find notes by customer ID with pagination
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of notes
     */
    Page<CustomerNote> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Find notes by type for a customer
     *
     * @param customerId the customer ID
     * @param noteType the note type
     * @param pageable pagination information
     * @return page of notes
     */
    Page<CustomerNote> findByCustomerIdAndNoteType(String customerId, NoteType noteType, Pageable pageable);

    /**
     * Find notes by priority for a customer
     *
     * @param customerId the customer ID
     * @param priority the priority level
     * @param pageable pagination information
     * @return page of notes
     */
    Page<CustomerNote> findByCustomerIdAndPriority(String customerId, Priority priority, Pageable pageable);

    /**
     * Find internal notes for a customer
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of internal notes
     */
    @Query("SELECT n FROM CustomerNote n WHERE n.customerId = :customerId AND n.isInternal = true")
    Page<CustomerNote> findInternalNotes(@Param("customerId") String customerId, Pageable pageable);

    /**
     * Find external notes for a customer
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of external notes
     */
    @Query("SELECT n FROM CustomerNote n WHERE n.customerId = :customerId AND n.isInternal = false")
    Page<CustomerNote> findExternalNotes(@Param("customerId") String customerId, Pageable pageable);

    /**
     * Find alert notes for a customer
     *
     * @param customerId the customer ID
     * @return list of alert notes
     */
    @Query("SELECT n FROM CustomerNote n WHERE n.customerId = :customerId AND n.isAlert = true ORDER BY n.createdAt DESC")
    List<CustomerNote> findAlertNotes(@Param("customerId") String customerId);

    /**
     * Find all alert notes
     *
     * @param pageable pagination information
     * @return page of alert notes
     */
    @Query("SELECT n FROM CustomerNote n WHERE n.isAlert = true ORDER BY n.createdAt DESC")
    Page<CustomerNote> findAllAlertNotes(Pageable pageable);

    /**
     * Find high priority notes for a customer
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of high priority notes
     */
    @Query("SELECT n FROM CustomerNote n WHERE n.customerId = :customerId " +
           "AND n.priority IN ('HIGH', 'URGENT', 'CRITICAL') ORDER BY n.priority DESC, n.createdAt DESC")
    Page<CustomerNote> findHighPriorityNotes(@Param("customerId") String customerId, Pageable pageable);

    /**
     * Find critical notes
     *
     * @param pageable pagination information
     * @return page of critical notes
     */
    @Query("SELECT n FROM CustomerNote n WHERE n.priority = 'CRITICAL' ORDER BY n.createdAt DESC")
    Page<CustomerNote> findCriticalNotes(Pageable pageable);

    /**
     * Find notes created by a specific user
     *
     * @param createdBy the user who created the notes
     * @param pageable pagination information
     * @return page of notes
     */
    Page<CustomerNote> findByCreatedBy(String createdBy, Pageable pageable);

    /**
     * Find notes created by a user for a specific customer
     *
     * @param customerId the customer ID
     * @param createdBy the user who created the notes
     * @param pageable pagination information
     * @return page of notes
     */
    Page<CustomerNote> findByCustomerIdAndCreatedBy(String customerId, String createdBy, Pageable pageable);

    /**
     * Find notes created within date range
     *
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of notes
     */
    @Query("SELECT n FROM CustomerNote n WHERE n.createdAt BETWEEN :startDate AND :endDate ORDER BY n.createdAt DESC")
    Page<CustomerNote> findByCreatedAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Find notes created within date range for a customer
     *
     * @param customerId the customer ID
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of notes
     */
    @Query("SELECT n FROM CustomerNote n WHERE n.customerId = :customerId " +
           "AND n.createdAt BETWEEN :startDate AND :endDate ORDER BY n.createdAt DESC")
    Page<CustomerNote> findByCustomerIdAndCreatedAtBetween(
        @Param("customerId") String customerId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Search notes by subject or content
     *
     * @param searchTerm the search term
     * @param pageable pagination information
     * @return page of matching notes
     */
    @Query("SELECT n FROM CustomerNote n WHERE " +
           "LOWER(n.subject) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(n.note) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<CustomerNote> searchNotes(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Search notes for a customer
     *
     * @param customerId the customer ID
     * @param searchTerm the search term
     * @param pageable pagination information
     * @return page of matching notes
     */
    @Query("SELECT n FROM CustomerNote n WHERE n.customerId = :customerId AND " +
           "(LOWER(n.subject) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(n.note) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<CustomerNote> searchNotesForCustomer(
        @Param("customerId") String customerId,
        @Param("searchTerm") String searchTerm,
        Pageable pageable
    );

    /**
     * Find all notes by type
     *
     * @param noteType the note type
     * @param pageable pagination information
     * @return page of notes
     */
    Page<CustomerNote> findByNoteType(NoteType noteType, Pageable pageable);

    /**
     * Find all notes by priority
     *
     * @param priority the priority level
     * @param pageable pagination information
     * @return page of notes
     */
    Page<CustomerNote> findByPriority(Priority priority, Pageable pageable);

    /**
     * Count notes by customer ID
     *
     * @param customerId the customer ID
     * @return count of notes
     */
    long countByCustomerId(String customerId);

    /**
     * Count alert notes by customer ID
     *
     * @param customerId the customer ID
     * @return count of alert notes
     */
    @Query("SELECT COUNT(n) FROM CustomerNote n WHERE n.customerId = :customerId AND n.isAlert = true")
    long countAlertsByCustomerId(@Param("customerId") String customerId);

    /**
     * Count high priority notes by customer ID
     *
     * @param customerId the customer ID
     * @return count of high priority notes
     */
    @Query("SELECT COUNT(n) FROM CustomerNote n WHERE n.customerId = :customerId " +
           "AND n.priority IN ('HIGH', 'URGENT', 'CRITICAL')")
    long countHighPriorityByCustomerId(@Param("customerId") String customerId);

    /**
     * Count notes by type
     *
     * @param noteType the note type
     * @return count of notes
     */
    long countByNoteType(NoteType noteType);

    /**
     * Count notes by priority
     *
     * @param priority the priority level
     * @return count of notes
     */
    long countByPriority(Priority priority);

    /**
     * Count total alerts
     *
     * @return count of alert notes
     */
    @Query("SELECT COUNT(n) FROM CustomerNote n WHERE n.isAlert = true")
    long countTotalAlerts();
}
