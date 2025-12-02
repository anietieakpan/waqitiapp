package com.waqiti.payment.service;

import com.waqiti.payment.dto.MobileMoneyTransferRequest;
import com.waqiti.payment.dto.MobileMoneyTransferResult;
import org.springframework.stereotype.Service;

/**
 * Agent Network Service Interface
 * 
 * Manages agent-assisted mobile money transactions and agent validation.
 */
@Service
public interface AgentNetworkService {

    /**
     * Validates agent credentials and status
     */
    void validateAgent(String agentCode, String provider);

    /**
     * Processes agent-assisted mobile money transfer
     */
    MobileMoneyTransferResult processAgentAssistedTransfer(MobileMoneyTransferRequest request);
}