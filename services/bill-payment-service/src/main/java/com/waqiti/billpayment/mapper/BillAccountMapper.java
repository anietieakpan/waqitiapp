package com.waqiti.billpayment.mapper;

import com.waqiti.billpayment.dto.AddBillAccountRequest;
import com.waqiti.billpayment.dto.BillAccountResponse;
import com.waqiti.billpayment.entity.BillerConnection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct mapper for BillerConnection (bill account) entity to DTO conversions
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface BillAccountMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "biller", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    @Mapping(source = "setAsDefault", target = "isDefault")
    BillerConnection toEntity(AddBillAccountRequest request);

    @Mapping(source = "biller.id", target = "billerId")
    @Mapping(source = "biller.name", target = "billerName")
    @Mapping(source = "biller.category", target = "billerCategory")
    BillAccountResponse toResponse(BillerConnection billerConnection);

    List<BillAccountResponse> toResponseList(List<BillerConnection> billerConnections);
}
