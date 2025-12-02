package com.waqiti.tax.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxFormPackage {
    private Integer taxYear;
    private String taxpayerSsn;
    private String taxpayerName;
    private String formType; // "1040", "1040-SR", etc.
    private Map<String, Object> form1040;
    private List<Map<String, Object>> schedules;
    private List<Map<String, Object>> supporting Documents;
    private String electronicSignature;
    private String signaturePin;
    private String preparerInfo;
    private boolean isJointReturn;
    private String spouseSsn;
    private String spouseName;
}
