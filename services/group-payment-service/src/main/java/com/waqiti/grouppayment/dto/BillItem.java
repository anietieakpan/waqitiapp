package com.waqiti.grouppayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bill Item DTO
 * Represents an individual item in a bill that can be assigned or shared
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillItem {

    private String itemId;

    private String name;

    private String description;

    private BigDecimal amount;

    private BigDecimal quantity;

    private String category; // FOOD, DRINK, SERVICE, OTHER

    private String assignedTo; // Single participant ID if not shared

    private List<String> sharedBy; // List of participant IDs if shared

    private Boolean taxable;

    private Boolean includeTip;
}
