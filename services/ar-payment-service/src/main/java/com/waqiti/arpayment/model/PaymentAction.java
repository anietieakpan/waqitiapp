package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a payment action in AR interface
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAction {
    private String name;
    private String description;
    private ActionType type;
    private String icon;
    private String gesture; // Required gesture to trigger
    private List<String> voiceCommands; // Voice commands that trigger this action
    private Map<String, Object> parameters;
    private boolean requiresConfirmation;
    private String confirmationMessage;
    private List<String> permissions; // Required permissions
    private boolean isEnabled;
    private int priority;
    
    public enum ActionType {
        SEND_PAYMENT,
        REQUEST_PAYMENT,
        SPLIT_BILL,
        SCAN_TO_PAY,
        TAP_TO_PAY,
        VOICE_PAY,
        GESTURE_PAY,
        VIEW_BALANCE,
        VIEW_HISTORY,
        ADD_FUNDS,
        WITHDRAW,
        CANCEL
    }
    
    public String getName() {
        return name != null ? name : "Unknown Action";
    }
    
    public String getDescription() {
        return description != null ? description : "";
    }
}