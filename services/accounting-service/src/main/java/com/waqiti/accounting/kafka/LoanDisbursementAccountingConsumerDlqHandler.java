package com.waqiti.accounting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.accounting.service.DlqRecoveryService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * DLQ Handler for LoanDisbursementAccountingConsumer
 * Enhanced with automated DLQ recovery system
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Service
@Slf4j
public class LoanDisbursementAccountingConsumerDlqHandler extends AccountingDlqHandler {

    public LoanDisbursementAccountingConsumerDlqHandler(
            MeterRegistry meterRegistry,
            DlqRecoveryService dlqRecoveryService,
            ObjectMapper objectMapper) {
        super(meterRegistry, dlqRecoveryService, objectMapper);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("LoanDisbursementAccountingConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.LoanDisbursementAccountingConsumer.dlq:LoanDisbursementAccountingConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected String getServiceName() {
        return "LoanDisbursementAccountingConsumer";
    }

    @Override
    protected String getMessageIdPrefix() {
        return "loan-disbursement";
    }

    @Override
    protected String getDefaultTopic() {
        return "loan-disbursement-events";
    }

    @Override
    protected String[] getIdFields() {
        return new String[]{"loanId", "disbursementId", "transactionId", "id"};
    }
}
