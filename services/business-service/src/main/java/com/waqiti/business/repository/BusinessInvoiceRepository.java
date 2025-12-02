package com.waqiti.business.repository;

import com.waqiti.business.domain.BusinessInvoice;
import com.waqiti.business.domain.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessInvoiceRepository extends JpaRepository<BusinessInvoice, UUID> {
    
    Page<BusinessInvoice> findByAccountId(UUID accountId, Pageable pageable);
    
    Page<BusinessInvoice> findByAccountIdAndStatus(UUID accountId, InvoiceStatus status, Pageable pageable);
    
    Optional<BusinessInvoice> findByIdAndAccountId(UUID id, UUID accountId);
    
    Optional<BusinessInvoice> findByInvoiceNumber(String invoiceNumber);
    
    boolean existsByInvoiceNumber(String invoiceNumber);
    
    long countByAccountId(UUID accountId);
    
    long countByAccountIdAndStatus(UUID accountId, InvoiceStatus status);
    
    @Query("SELECT i FROM BusinessInvoice i WHERE i.accountId = :accountId AND " +
           "(:status IS NULL OR i.status = :status) AND " +
           "(:customerName IS NULL OR LOWER(i.customerName) LIKE LOWER(CONCAT('%', :customerName, '%')))")
    Page<BusinessInvoice> findByFilters(@Param("accountId") UUID accountId,
                                       @Param("status") String status,
                                       @Param("customerName") String customerName,
                                       Pageable pageable);
    
    @Query("SELECT i FROM BusinessInvoice i WHERE i.accountId = :accountId AND " +
           "i.createdAt BETWEEN :startDate AND :endDate")
    List<BusinessInvoice> findByAccountIdAndCreatedAtBetween(@Param("accountId") UUID accountId,
                                                            @Param("startDate") LocalDateTime startDate,
                                                            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(i.totalAmount) FROM BusinessInvoice i WHERE i.accountId = :accountId AND " +
           "i.status = 'PAID' AND i.paidAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalPaidInvoicesByDateRange(@Param("accountId") UUID accountId,
                                              @Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(i.totalAmount) FROM BusinessInvoice i WHERE i.accountId = :accountId AND " +
           "i.status IN ('SENT', 'VIEWED', 'OVERDUE')")
    BigDecimal getTotalOutstandingAmount(@Param("accountId") UUID accountId);
    
    @Query("SELECT i FROM BusinessInvoice i WHERE i.status = 'SENT' AND i.dueDate < :currentDate")
    List<BusinessInvoice> findOverdueInvoices(@Param("currentDate") LocalDateTime currentDate);
    
    @Query("SELECT i.customerName, COUNT(i) FROM BusinessInvoice i WHERE i.accountId = :accountId " +
           "GROUP BY i.customerName ORDER BY COUNT(i) DESC")
    List<Object[]> getInvoiceCountByCustomer(@Param("accountId") UUID accountId);
    
    @Query("SELECT i.status, COUNT(i) FROM BusinessInvoice i WHERE i.accountId = :accountId " +
           "GROUP BY i.status")
    List<Object[]> getInvoiceCountByStatus(@Param("accountId") UUID accountId);
    
    @Query("SELECT AVG(i.totalAmount) FROM BusinessInvoice i WHERE i.accountId = :accountId")
    BigDecimal getAverageInvoiceAmount(@Param("accountId") UUID accountId);
    
    @Query("SELECT i FROM BusinessInvoice i WHERE i.accountId = :accountId AND " +
           "i.status = 'SENT' AND i.sentAt < :reminderDate")
    List<BusinessInvoice> findInvoicesNeedingReminder(@Param("accountId") UUID accountId,
                                                     @Param("reminderDate") LocalDateTime reminderDate);
    
    @Query("SELECT EXTRACT(MONTH FROM i.createdAt) as month, SUM(i.totalAmount) FROM BusinessInvoice i " +
           "WHERE i.accountId = :accountId AND i.status = 'PAID' AND EXTRACT(YEAR FROM i.createdAt) = :year " +
           "GROUP BY EXTRACT(MONTH FROM i.createdAt) ORDER BY month")
    List<Object[]> getMonthlyRevenue(@Param("accountId") UUID accountId, @Param("year") int year);
}