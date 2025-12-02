package com.waqiti.billpayment.mapper;

import com.waqiti.billpayment.dto.BillerResponse;
import com.waqiti.billpayment.entity.Biller;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct mapper for Biller entity to DTO conversions
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface BillerMapper {

    BillerResponse toResponse(Biller biller);

    List<BillerResponse> toResponseList(List<Biller> billers);
}
