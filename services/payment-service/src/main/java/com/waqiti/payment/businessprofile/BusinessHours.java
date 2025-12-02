package com.waqiti.payment.businessprofile;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessHours {
    
    @Column(name = "open_time")
    private LocalTime openTime;
    
    @Column(name = "close_time")
    private LocalTime closeTime;
    
    @Column(name = "is_open")
    private boolean isOpen;
}