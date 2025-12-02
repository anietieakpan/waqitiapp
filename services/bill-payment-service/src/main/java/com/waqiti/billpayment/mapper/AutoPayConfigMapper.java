package com.waqiti.billpayment.mapper;

import com.waqiti.billpayment.dto.AutoPayConfigDto;
import com.waqiti.billpayment.dto.AutoPayResponse;
import com.waqiti.billpayment.dto.SetupAutoPayRequest;
import com.waqiti.billpayment.entity.AutoPayConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct mapper for AutoPayConfig entity to DTO conversions
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface AutoPayConfigMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "bill", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "successfulPayments", constant = "0")
    @Mapping(target = "failedPayments", constant = "0")
    AutoPayConfig toEntity(SetupAutoPayRequest request);

    @Mapping(source = "id", target = "autoPayId")
    @Mapping(source = "bill.id", target = "billId")
    @Mapping(source = "bill.biller.name", target = "billerName")
    @Mapping(source = "bill.accountNumber", target = "accountNumber")
    @Mapping(source = "bill.currency", target = "currency")
    AutoPayResponse toResponse(AutoPayConfig autoPayConfig);

    List<AutoPayResponse> toResponseList(List<AutoPayConfig> autoPayConfigs);

    @Mapping(source = "bill.id", target = "billId")
    @Mapping(source = "bill.biller.name", target = "billerName")
    @Mapping(source = "bill.accountNumber", target = "accountNumber")
    AutoPayConfigDto toConfigDto(AutoPayConfig autoPayConfig);

    List<AutoPayConfigDto> toConfigDtoList(List<AutoPayConfig> autoPayConfigs);
}
