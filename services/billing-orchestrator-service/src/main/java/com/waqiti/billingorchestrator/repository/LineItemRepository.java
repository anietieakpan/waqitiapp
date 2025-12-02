package com.waqiti.billingorchestrator.repository;

import com.waqiti.billingorchestrator.entity.BillingOrchestratorCycle;
import com.waqiti.billingorchestrator.entity.LineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for LineItem entities
 */
@Repository
public interface LineItemRepository extends JpaRepository<LineItem, UUID> {

    /**
     * Find line items by billing cycle
     */
    List<LineItem> findByBillingCycle(BillingCycle billingCycle);

    /**
     * Find line items by billing cycle and status
     */
    List<LineItem> findByBillingCycleAndStatus(BillingCycle billingCycle, LineItem.ItemStatus status);

    /**
     * Find line items by billing cycle and item type
     */
    List<LineItem> findByBillingCycleAndItemType(BillingCycle billingCycle, LineItem.ItemType itemType);

    /**
     * Find line items by customer ID
     */
    @Query("SELECT li FROM LineItem li WHERE li.billingCycle.customerId = :customerId")
    List<LineItem> findByCustomerId(@Param("customerId") UUID customerId);

    /**
     * Find line items by subscription ID
     */
    List<LineItem> findBySubscriptionId(UUID subscriptionId);

    /**
     * Find line items by reference ID and type
     */
    List<LineItem> findByReferenceIdAndReferenceType(String referenceId, String referenceType);

    /**
     * Find line items by item type
     */
    List<LineItem> findByItemType(LineItem.ItemType itemType);

    /**
     * Find line items by date range
     */
    @Query("SELECT li FROM LineItem li WHERE li.periodStart >= :startDate AND li.periodEnd <= :endDate")
    List<LineItem> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Sum net amount by billing cycle
     */
    @Query("SELECT COALESCE(SUM(li.netAmount), 0) FROM LineItem li WHERE li.billingCycle = :billingCycle AND li.status != 'CANCELLED'")
    BigDecimal sumNetAmountByBillingCycle(@Param("billingCycle") BillingCycle billingCycle);

    /**
     * Sum net amount by billing cycle and item type
     */
    @Query("SELECT COALESCE(SUM(li.netAmount), 0) FROM LineItem li WHERE li.billingCycle = :billingCycle AND li.itemType = :itemType AND li.status != 'CANCELLED'")
    BigDecimal sumNetAmountByBillingCycleAndItemType(@Param("billingCycle") BillingCycle billingCycle, @Param("itemType") LineItem.ItemType itemType);

    /**
     * Count line items by billing cycle
     */
    long countByBillingCycle(BillingCycle billingCycle);

    /**
     * Count line items by billing cycle and status
     */
    long countByBillingCycleAndStatus(BillingCycle billingCycle, LineItem.ItemStatus status);

    /**
     * Find disputed line items
     */
    List<LineItem> findByStatus(LineItem.ItemStatus status);

    /**
     * Find line items by product code
     */
    List<LineItem> findByProductCode(String productCode);

    /**
     * Find line items by category
     */
    List<LineItem> findByCategory(String category);

    /**
     * Find prorated line items
     */
    List<LineItem> findByIsProrated(Boolean isProrated);

    /**
     * Find line items with usage details
     */
    @Query("SELECT li FROM LineItem li WHERE li.usageType IS NOT NULL AND li.usageCount > 0")
    List<LineItem> findUsageBasedItems();

    /**
     * Find line items by external reference
     */
    List<LineItem> findByExternalReference(String externalReference);

    /**
     * Find line items with discounts
     */
    @Query("SELECT li FROM LineItem li WHERE li.discountAmount > 0 OR li.discountPercentage > 0")
    List<LineItem> findItemsWithDiscounts();

    /**
     * Find line items above a certain amount
     */
    @Query("SELECT li FROM LineItem li WHERE li.netAmount >= :minAmount")
    List<LineItem> findItemsAboveAmount(@Param("minAmount") BigDecimal minAmount);
}