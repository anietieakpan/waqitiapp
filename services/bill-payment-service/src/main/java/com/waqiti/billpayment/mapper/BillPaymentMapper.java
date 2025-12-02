package com.waqiti.billpayment.mapper;

import com.waqiti.billpayment.dto.BillPaymentResponse;
import com.waqiti.billpayment.dto.PaymentStatusResponse;
import com.waqiti.billpayment.dto.ScheduledPaymentResponse;
import com.waqiti.billpayment.entity.BillPayment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct mapper for BillPayment entity to DTO conversions
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface BillPaymentMapper {

    @Mapping(source = "id", target = "paymentId")
    @Mapping(source = "bill.biller.name", target = "billerName")
    @Mapping(source = "bill.accountNumber", target = "accountNumber")
    @Mapping(source = "createdAt", target = "initiatedAt")
    BillPaymentResponse toResponse(BillPayment billPayment);

    List<BillPaymentResponse> toResponseList(List<BillPayment> billPayments);

    @Mapping(source = "id", target = "paymentId")
    @Mapping(source = "bill.biller.name", target = "billerName")
    @Mapping(source = "bill.accountNumber", target = "accountNumber")
    @Mapping(source = "createdAt", target = "initiatedAt")
    PaymentStatusResponse toStatusResponse(BillPayment billPayment);

    @Mapping(source = "id", target = "paymentId")
    @Mapping(source = "bill.biller.name", target = "billerName")
    @Mapping(source = "bill.accountNumber", target = "accountNumber")
    ScheduledPaymentResponse toScheduledResponse(BillPayment billPayment);

    List<ScheduledPaymentResponse> toScheduledResponseList(List<BillPayment> billPayments);
}
