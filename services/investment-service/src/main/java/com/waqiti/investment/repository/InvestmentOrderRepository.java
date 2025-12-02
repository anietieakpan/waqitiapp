package com.waqiti.investment.repository;

import com.waqiti.investment.domain.InvestmentOrder;
import com.waqiti.investment.domain.enums.OrderSide;
import com.waqiti.investment.domain.enums.OrderStatus;
import com.waqiti.investment.domain.enums.OrderType;
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

@Repository
public interface InvestmentOrderRepository extends JpaRepository<InvestmentOrder, String> {

    Optional<InvestmentOrder> findByOrderNumber(String orderNumber);

    Optional<InvestmentOrder> findByBrokerageOrderId(String brokerageOrderId);

    List<InvestmentOrder> findByInvestmentAccountId(String investmentAccountId);

    Page<InvestmentOrder> findByInvestmentAccountId(String investmentAccountId, Pageable pageable);

    List<InvestmentOrder> findByInvestmentAccountIdAndStatus(String investmentAccountId, OrderStatus status);

    List<InvestmentOrder> findByStatus(OrderStatus status);

    @Query("SELECT o FROM InvestmentOrder o WHERE o.investmentAccount.id = :accountId " +
           "AND o.status IN (:statuses) ORDER BY o.createdAt DESC")
    List<InvestmentOrder> findActiveOrders(@Param("accountId") String accountId, 
                                          @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT o FROM InvestmentOrder o WHERE o.investmentAccount.id = :accountId " +
           "AND o.symbol = :symbol AND o.status = :status")
    List<InvestmentOrder> findByAccountSymbolAndStatus(@Param("accountId") String accountId,
                                                      @Param("symbol") String symbol,
                                                      @Param("status") OrderStatus status);

    @Query("SELECT o FROM InvestmentOrder o WHERE o.investmentAccount.id = :accountId " +
           "AND o.createdAt >= :startDate AND o.createdAt <= :endDate")
    List<InvestmentOrder> findOrdersInDateRange(@Param("accountId") String accountId,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);

    @Query("SELECT o FROM InvestmentOrder o WHERE o.investmentAccount.id = :accountId " +
           "AND o.isDayTrade = true AND DATE(o.createdAt) = CURRENT_DATE")
    List<InvestmentOrder> findTodaysDayTrades(@Param("accountId") String accountId);

    @Query("SELECT COUNT(o) FROM InvestmentOrder o WHERE o.investmentAccount.id = :accountId " +
           "AND o.isDayTrade = true AND DATE(o.createdAt) = CURRENT_DATE")
    long countTodaysDayTrades(@Param("accountId") String accountId);

    @Query("SELECT o FROM InvestmentOrder o WHERE o.status = :status AND o.timeInForce = 'DAY' " +
           "AND DATE(o.createdAt) < CURRENT_DATE")
    List<InvestmentOrder> findExpiredDayOrders(@Param("status") OrderStatus status);

    @Query("SELECT o.symbol, COUNT(o), SUM(o.executedQuantity) FROM InvestmentOrder o " +
           "WHERE o.investmentAccount.id = :accountId AND o.status = 'FILLED' " +
           "GROUP BY o.symbol ORDER BY COUNT(o) DESC")
    List<Object[]> getMostTradedSymbols(@Param("accountId") String accountId);

    @Query("SELECT SUM(o.totalCost) FROM InvestmentOrder o WHERE o.investmentAccount.id = :accountId " +
           "AND o.side = :side AND o.status = 'FILLED'")
    BigDecimal getTotalVolumeByAccountAndSide(@Param("accountId") String accountId, @Param("side") OrderSide side);

    @Query("SELECT AVG(o.commission) FROM InvestmentOrder o WHERE o.status = 'FILLED'")
    BigDecimal getAverageCommission();

    @Query("SELECT o FROM InvestmentOrder o WHERE o.orderAmount > :minAmount ORDER BY o.orderAmount DESC")
    List<InvestmentOrder> findLargeOrders(@Param("minAmount") BigDecimal minAmount);

    @Query("SELECT o.orderType, COUNT(o) FROM InvestmentOrder o GROUP BY o.orderType")
    List<Object[]> getOrderTypeDistribution();

    @Query("SELECT HOUR(o.createdAt), COUNT(o) FROM InvestmentOrder o " +
           "WHERE DATE(o.createdAt) = CURRENT_DATE GROUP BY HOUR(o.createdAt)")
    List<Object[]> getTodaysOrdersByHour();

    @Query("SELECT o FROM InvestmentOrder o WHERE o.status = 'REJECTED' AND o.createdAt >= :since")
    List<InvestmentOrder> findRecentRejectedOrders(@Param("since") LocalDateTime since);

    @Query("SELECT o FROM InvestmentOrder o WHERE o.parentOrderId = :parentOrderId")
    List<InvestmentOrder> findChildOrders(@Param("parentOrderId") String parentOrderId);

    @Query("SELECT DATE(o.filledAt), COUNT(o), SUM(o.totalCost) FROM InvestmentOrder o " +
           "WHERE o.status = 'FILLED' AND o.filledAt >= :startDate " +
           "GROUP BY DATE(o.filledAt) ORDER BY DATE(o.filledAt)")
    List<Object[]> getDailyTradingVolume(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT o FROM InvestmentOrder o WHERE o.averagePrice > o.limitPrice * :threshold " +
           "AND o.orderType = 'LIMIT' AND o.side = 'BUY'")
    List<InvestmentOrder> findPoorlyExecutedBuyOrders(@Param("threshold") BigDecimal threshold);
}