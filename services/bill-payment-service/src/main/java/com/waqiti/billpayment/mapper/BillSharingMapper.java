package com.waqiti.billpayment.mapper;

import com.waqiti.billpayment.dto.BillSharingParticipant;
import com.waqiti.billpayment.dto.BillSharingResponse;
import com.waqiti.billpayment.entity.BillShareParticipant;
import com.waqiti.billpayment.entity.BillShareRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct mapper for bill sharing entities to DTO conversions
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface BillSharingMapper {

    @Mapping(source = "id", target = "shareRequestId")
    @Mapping(source = "bill.id", target = "billId")
    @Mapping(source = "bill.biller.name", target = "billerName")
    @Mapping(source = "bill.accountNumber", target = "accountNumber")
    @Mapping(source = "bill.amount", target = "totalAmount")
    @Mapping(source = "bill.currency", target = "currency")
    BillSharingResponse toResponse(BillShareRequest billShareRequest);

    @Mapping(source = "id", target = "participantId")
    BillSharingParticipant toParticipantDto(BillShareParticipant billShareParticipant);

    List<BillSharingParticipant> toParticipantDtoList(List<BillShareParticipant> billShareParticipants);
}
