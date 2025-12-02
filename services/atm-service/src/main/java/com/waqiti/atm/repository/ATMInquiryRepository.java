package com.waqiti.atm.repository;

import com.waqiti.atm.domain.ATMInquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface ATMInquiryRepository extends JpaRepository<ATMInquiry, UUID> {

    @Query("SELECT COUNT(i) FROM ATMInquiry i " +
           "JOIN ATMCard c ON i.cardId = c.id " +
           "WHERE c.cardNumber = :cardNumber " +
           "AND DATE(i.inquiryDate) = :date")
    long countInquiriesByCardNumberAndDate(
            @Param("cardNumber") String cardNumber,
            @Param("date") LocalDate date);
}
