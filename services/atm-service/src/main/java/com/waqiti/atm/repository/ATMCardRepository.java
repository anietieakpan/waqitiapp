package com.waqiti.atm.repository;

import com.waqiti.atm.domain.ATMCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ATMCardRepository extends JpaRepository<ATMCard, UUID> {
    
    Optional<ATMCard> findByCardNumber(String cardNumber);
    
    Optional<ATMCard> findByCardNumberAndAccountId(String cardNumber, UUID accountId);
    
    List<ATMCard> findByAccountId(UUID accountId);
    
    List<ATMCard> findByStatus(ATMCard.CardStatus status);
    
    @Query("SELECT c FROM ATMCard c WHERE c.expiryDate < :date AND c.status = 'ACTIVE'")
    List<ATMCard> findExpiredCards(@Param("date") LocalDateTime date);
    
    @Query("SELECT c FROM ATMCard c WHERE c.status = 'BLOCKED' AND c.blockedAt < :date")
    List<ATMCard> findBlockedCardsSince(@Param("date") LocalDateTime date);
    
    @Query("SELECT COUNT(c) FROM ATMCard c WHERE c.accountId = :accountId AND c.status = 'ACTIVE'")
    Long countActiveCardsByAccount(@Param("accountId") UUID accountId);
    
    boolean existsByCardNumber(String cardNumber);
}