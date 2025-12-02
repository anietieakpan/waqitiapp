package com.waqiti.merchant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactInfo {
    
    @Column(name = "contact_name", length = 100)
    private String contactName;
    
    @Column(name = "contact_email", length = 255)
    private String contactEmail;
    
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;
    
    @Column(name = "support_email", length = 255)
    private String supportEmail;
    
    @Column(name = "support_phone", length = 20)
    private String supportPhone;
}