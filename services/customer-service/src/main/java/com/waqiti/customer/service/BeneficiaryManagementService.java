package com.waqiti.customer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class BeneficiaryManagementService {
    public void addBeneficiary(String customerId, String beneficiaryId) {
        log.info("Adding beneficiary: customerId={}, beneficiaryId={}", customerId, beneficiaryId);
    }
    public List<String> getBeneficiaries(String customerId) {
        log.info("Getting beneficiaries: customerId={}", customerId);
        return Collections.emptyList();
    }
    public void removeBeneficiary(String customerId, String beneficiaryId) {
        log.info("Removing beneficiary: customerId={}, beneficiaryId={}", customerId, beneficiaryId);
    }
}
