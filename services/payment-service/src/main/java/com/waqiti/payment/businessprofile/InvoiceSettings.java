package com.waqiti.payment.businessprofile;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceSettings {
    
    @Column(name = "invoice_prefix", length = 10)
    private String invoicePrefix;
    
    @Column(name = "starting_number")
    private Long startingNumber;
    
    @Column(name = "default_due_days")
    private Integer defaultDueDays;
    
    @Column(name = "default_payment_terms", length = 500)
    private String defaultPaymentTerms;
    
    @Column(name = "default_notes", columnDefinition = "TEXT")
    private String defaultNotes;
    
    @Column(name = "logo_url", length = 500)
    private String logoUrl;
    
    @Column(name = "template", length = 50)
    private String template;
    
    @Column(name = "tax_settings", columnDefinition = "JSONB")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> taxSettings;
    
    @Column(name = "auto_send_enabled")
    private boolean autoSendEnabled;
    
    @Column(name = "auto_reminders_enabled")
    private boolean autoRemindersEnabled;
    
    @Column(name = "reminder_days", columnDefinition = "TEXT")
    @Convert(converter = IntegerListConverter.class)
    private List<Integer> reminderDays;
    
    @Column(name = "late_fee_enabled")
    private boolean lateFeeEnabled;
}