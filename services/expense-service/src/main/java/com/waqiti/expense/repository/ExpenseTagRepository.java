package com.waqiti.expense.repository;

import com.waqiti.expense.domain.ExpenseTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Expense Tag Repository
 */
@Repository
public interface ExpenseTagRepository extends JpaRepository<ExpenseTag, UUID> {

    List<ExpenseTag> findByUserIdOrderByNameAsc(UUID userId);

    Optional<ExpenseTag> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    @Query("SELECT et FROM ExpenseTag et WHERE et.userId = :userId " +
           "AND LOWER(et.name) IN :tagNames")
    List<ExpenseTag> findByUserIdAndNames(UUID userId, List<String> tagNames);
}
