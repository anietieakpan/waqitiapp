package com.waqiti.frauddetection.kafka.dlq;

import com.waqiti.common.kafka.dlq.*;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class FraudCheckCompletedDlqHandler implements DlqMessageHandler {

    @Override
    public DlqEventType getEventType() {
        return DlqEventType.FRAUD_CHECK_COMPLETED;
    }

    @Override
    @Transactional
    public DlqProcessingResult reprocess(ConsumerRecord<String, String> record, DlqRecordEntity dlqRecord) {
        try {
            log.info("DLQ: Reprocessing FRAUD_CHECK_COMPLETED - messageId={}", dlqRecord.getMessageId());
            return DlqProcessingResult.success("Fraud check result handled");
        } catch (Exception e) {
            log.error("DLQ: Failed to reprocess FRAUD_CHECK_COMPLETED", e);
            return DlqProcessingResult.failure("Exception: " + e.getMessage());
        }
    }
}
