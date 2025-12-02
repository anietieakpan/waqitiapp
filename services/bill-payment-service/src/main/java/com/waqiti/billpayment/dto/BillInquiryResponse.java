package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for bill inquiry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillInquiryResponse {

    private UUID billId;

    private String billerName;

    private String accountNumber;

    private String accountName;

    private BigDecimal billAmount;

    private BigDecimal minimumDue;

    private String currency;

    private LocalDate dueDate;

    private LocalDate issueDate;

    private String billPeriod;

    private String billStatus;

    private String billerReferenceNumber;

    private String additionalInfo;

    private Boolean canPayPartial;

    private Boolean canSchedule;
}
