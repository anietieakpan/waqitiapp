package com.waqiti.customer.dto;

import com.waqiti.customer.entity.CustomerComplaint.ComplaintCategory;
import com.waqiti.customer.entity.CustomerComplaint.ComplaintType;
import com.waqiti.customer.entity.CustomerComplaint.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create Complaint Request DTO
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateComplaintRequest {

    private String customerId;
    private ComplaintType complaintType;
    private ComplaintCategory complaintCategory;
    private Severity severity;
    private String description;
}
