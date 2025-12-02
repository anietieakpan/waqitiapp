package com.waqiti.billpayment;

import com.waqiti.billpayment.entity.*;
import com.waqiti.billpayment.dto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test data builder for creating test entities and DTOs
 */
public class TestDataBuilder {

    // Default test values
    public static final String TEST_USER_ID = "test-user-123";
    public static final String TEST_ACCOUNT_NUMBER = "ACC-123456";
    public static final BigDecimal TEST_AMOUNT = new BigDecimal("150.75");
    public static final String TEST_CURRENCY = "USD";

    /**
     * Create a test Biller
     */
    public static Biller createTestBiller() {
        return Biller.builder()
                .id(UUID.randomUUID())
                .billerCode("TEST-BILLER-001")
                .name("Test Electricity Company")
                .category(BillCategory.UTILITIES)
                .countryCode("US")
                .isActive(true)
                .supportsAutoPay(true)
                .supportsDirectPayment(true)
                .supportsBillImport(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create a test Bill
     */
    public static Bill createTestBill(Biller biller) {
        return Bill.builder()
                .id(UUID.randomUUID())
                .userId(TEST_USER_ID)
                .biller(biller)
                .billerId(biller.getId())
                .billerName(biller.getName())
                .accountNumber(TEST_ACCOUNT_NUMBER)
                .accountName("John Doe")
                .billNumber("BILL-" + System.currentTimeMillis())
                .category(biller.getCategory())
                .amount(TEST_AMOUNT)
                .minimumDue(new BigDecimal("50.00"))
                .currency(TEST_CURRENCY)
                .dueDate(LocalDate.now().plusDays(15))
                .issueDate(LocalDate.now())
                .status(BillStatus.UNPAID)
                .isRecurring(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create a test BillPayment
     */
    public static BillPayment createTestBillPayment(Bill bill) {
        return BillPayment.builder()
                .id(UUID.randomUUID())
                .userId(TEST_USER_ID)
                .bill(bill)
                .amount(bill.getAmount())
                .currency(bill.getCurrency())
                .paymentMethod("WALLET")
                .status(BillPaymentStatus.PENDING)
                .idempotencyKey(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create a test BillerConnection
     */
    public static BillerConnection createTestBillerConnection(Biller biller) {
        return BillerConnection.builder()
                .id(UUID.randomUUID())
                .userId(TEST_USER_ID)
                .biller(biller)
                .billerId(biller.getId())
                .accountNumber(TEST_ACCOUNT_NUMBER)
                .accountName("John Doe")
                .status(ConnectionStatus.ACTIVE)
                .isActive(true)
                .isDefault(false)
                .autoImportEnabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create a test AutoPayConfig
     */
    public static AutoPayConfig createTestAutoPayConfig(Bill bill) {
        return AutoPayConfig.builder()
                .id(UUID.randomUUID())
                .userId(TEST_USER_ID)
                .bill(bill)
                .paymentMethod("WALLET")
                .amountType("FULL_BALANCE")
                .paymentTiming("ON_DUE_DATE")
                .isEnabled(true)
                .successfulPayments(0)
                .failedPayments(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create a test PayBillRequest
     */
    public static PayBillRequest createPayBillRequest(UUID billId) {
        return PayBillRequest.builder()
                .billId(billId)
                .amount(TEST_AMOUNT)
                .paymentMethod("WALLET")
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Create a test BillInquiryRequest
     */
    public static BillInquiryRequest createBillInquiryRequest(UUID billerId) {
        return BillInquiryRequest.builder()
                .billerId(billerId)
                .accountNumber(TEST_ACCOUNT_NUMBER)
                .accountName("John Doe")
                .build();
    }

    /**
     * Create a test AddBillAccountRequest
     */
    public static AddBillAccountRequest createAddBillAccountRequest(UUID billerId) {
        return AddBillAccountRequest.builder()
                .billerId(billerId)
                .accountNumber(TEST_ACCOUNT_NUMBER)
                .accountName("John Doe")
                .setAsDefault(false)
                .build();
    }
}
