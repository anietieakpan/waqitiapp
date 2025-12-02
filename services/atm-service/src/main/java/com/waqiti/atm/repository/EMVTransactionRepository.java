package com.waqiti.atm.repository;

import com.waqiti.atm.domain.EMVTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EMVTransactionRepository extends JpaRepository<EMVTransaction, UUID> {

    @Query("SELECT MAX(e.atc) FROM EMVTransaction e WHERE e.cardId = :cardId")
    Optional<Integer> findLastATCByCardId(@Param("cardId") UUID cardId);
}
