package com.waqiti.billpayment.mapper;

import com.waqiti.billpayment.dto.BillInquiryResponse;
import com.waqiti.billpayment.entity.Bill;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct mapper for Bill entity to DTO conversions
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface BillMapper {

    @Mapping(source = "biller.name", target = "billerName")
    @Mapping(source = "amount", target = "billAmount")
    BillInquiryResponse toInquiryResponse(Bill bill);

    List<BillInquiryResponse> toInquiryResponseList(List<Bill> bills);
}
