package com.waqiti.merchant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSettings {
    
    @Column(name = "accept_cash_payments")
    @Builder.Default
    private Boolean acceptCashPayments = true;
    
    @Column(name = "accept_card_payments")
    @Builder.Default
    private Boolean acceptCardPayments = true;
    
    @Column(name = "accept_online_payments")
    @Builder.Default
    private Boolean acceptOnlinePayments = true;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_schedule")
    @Builder.Default
    private SettlementSchedule settlementSchedule = SettlementSchedule.DAILY;
    
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;
    
    @Column(name = "notifications_enabled")
    @Builder.Default
    private Boolean notificationsEnabled = true;
    
    @Column(name = "auto_settle")
    @Builder.Default
    private Boolean autoSettle = true;
    
    @Column(name = "require_receipt")
    @Builder.Default
    private Boolean requireReceipt = false;
    
    public enum SettlementSchedule {
        INSTANT,
        DAILY,
        WEEKLY,
        MONTHLY,
        MANUAL
    }
}