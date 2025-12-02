package com.waqiti.billpayment.repository;

import com.waqiti.billpayment.entity.BillReminder;
import com.waqiti.billpayment.entity.ReminderStatus;
import com.waqiti.billpayment.entity.ReminderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for BillReminder entity operations
 */
@Repository
public interface BillReminderRepository extends JpaRepository<BillReminder, UUID> {

    List<BillReminder> findByUserId(String userId);

    List<BillReminder> findByBillId(UUID billId);

    List<BillReminder> findByUserIdAndStatus(String userId, ReminderStatus status);

    @Query("SELECT br FROM BillReminder br WHERE br.status = 'PENDING' " +
           "AND br.scheduledSendTime <= :now")
    List<BillReminder> findRemindersDueToSend(@Param("now") LocalDateTime now);

    @Query("SELECT br FROM BillReminder br WHERE br.status = 'FAILED' " +
           "AND br.retryCount < 3")
    List<BillReminder> findFailedRemindersForRetry();

    boolean existsByBillIdAndReminderTypeAndStatus(UUID billId, ReminderType reminderType, ReminderStatus status);

    long countByUserIdAndStatus(String userId, ReminderStatus status);
}
