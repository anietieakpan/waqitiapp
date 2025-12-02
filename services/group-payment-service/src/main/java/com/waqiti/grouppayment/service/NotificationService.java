package com.waqiti.grouppayment.service;

import com.waqiti.grouppayment.entity.GroupPayment;
import com.waqiti.grouppayment.entity.GroupPaymentParticipant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void notifyParticipantsOfNewGroupPayment(GroupPayment groupPayment) {
        log.info("Sending notifications for new group payment: {}", groupPayment.getGroupPaymentId());

        groupPayment.getParticipants().forEach(participant -> {
            Map<String, Object> notification = Map.of(
                "eventType", "GROUP_PAYMENT_INVITATION",
                "recipientId", participant.getUserId(),
                "groupPaymentId", groupPayment.getGroupPaymentId(),
                "title", groupPayment.getTitle(),
                "createdBy", groupPayment.getCreatedBy(),
                "owedAmount", participant.getOwedAmount(),
                "currency", groupPayment.getCurrency(),
                "dueDate", groupPayment.getDueDate(),
                "timestamp", Instant.now()
            );

            kafkaTemplate.send("notification-events", participant.getUserId(), notification);
        });
    }

    public void notifyParticipantsOfCancellation(GroupPayment groupPayment) {
        log.info("Sending cancellation notifications for group payment: {}", groupPayment.getGroupPaymentId());

        groupPayment.getParticipants().forEach(participant -> {
            Map<String, Object> notification = Map.of(
                "eventType", "GROUP_PAYMENT_CANCELLED",
                "recipientId", participant.getUserId(),
                "groupPaymentId", groupPayment.getGroupPaymentId(),
                "title", groupPayment.getTitle(),
                "createdBy", groupPayment.getCreatedBy(),
                "timestamp", Instant.now()
            );

            kafkaTemplate.send("notification-events", participant.getUserId(), notification);
        });
    }

    public void notifyOfPaymentReceived(GroupPayment groupPayment, GroupPaymentParticipant participant, BigDecimal amount) {
        log.info("Sending payment received notification for group payment: {}", groupPayment.getGroupPaymentId());

        // Notify the creator
        Map<String, Object> creatorNotification = Map.of(
            "eventType", "GROUP_PAYMENT_RECEIVED",
            "recipientId", groupPayment.getCreatedBy(),
            "groupPaymentId", groupPayment.getGroupPaymentId(),
            "title", groupPayment.getTitle(),
            "paidBy", participant.getUserId(),
            "amount", amount,
            "currency", groupPayment.getCurrency(),
            "remainingAmount", participant.getRemainingAmount(),
            "timestamp", Instant.now()
        );

        kafkaTemplate.send("notification-events", groupPayment.getCreatedBy(), creatorNotification);

        // Notify the participant (confirmation)
        Map<String, Object> participantNotification = Map.of(
            "eventType", "PAYMENT_CONFIRMATION",
            "recipientId", participant.getUserId(),
            "groupPaymentId", groupPayment.getGroupPaymentId(),
            "title", groupPayment.getTitle(),
            "amount", amount,
            "currency", groupPayment.getCurrency(),
            "remainingAmount", participant.getRemainingAmount(),
            "timestamp", Instant.now()
        );

        kafkaTemplate.send("notification-events", participant.getUserId(), participantNotification);
    }

    public void notifyOfGroupPaymentCompletion(GroupPayment groupPayment) {
        log.info("Sending completion notifications for group payment: {}", groupPayment.getGroupPaymentId());

        // Notify all participants
        groupPayment.getParticipants().forEach(participant -> {
            Map<String, Object> notification = Map.of(
                "eventType", "GROUP_PAYMENT_COMPLETED",
                "recipientId", participant.getUserId(),
                "groupPaymentId", groupPayment.getGroupPaymentId(),
                "title", groupPayment.getTitle(),
                "totalAmount", groupPayment.getTotalAmount(),
                "currency", groupPayment.getCurrency(),
                "timestamp", Instant.now()
            );

            kafkaTemplate.send("notification-events", participant.getUserId(), notification);
        });

        // Notify the creator
        Map<String, Object> creatorNotification = Map.of(
            "eventType", "GROUP_PAYMENT_COMPLETED",
            "recipientId", groupPayment.getCreatedBy(),
            "groupPaymentId", groupPayment.getGroupPaymentId(),
            "title", groupPayment.getTitle(),
            "totalAmount", groupPayment.getTotalAmount(),
            "currency", groupPayment.getCurrency(),
            "completedAt", Instant.now(),
            "timestamp", Instant.now()
        );

        kafkaTemplate.send("notification-events", groupPayment.getCreatedBy(), creatorNotification);
    }

    public void sendPaymentReminders(GroupPayment groupPayment, List<GroupPaymentParticipant> unpaidParticipants) {
        log.info("Sending payment reminders for group payment: {} to {} participants", 
            groupPayment.getGroupPaymentId(), unpaidParticipants.size());

        unpaidParticipants.forEach(participant -> {
            Map<String, Object> notification = Map.of(
                "eventType", "PAYMENT_REMINDER",
                "recipientId", participant.getUserId(),
                "groupPaymentId", groupPayment.getGroupPaymentId(),
                "title", groupPayment.getTitle(),
                "owedAmount", participant.getOwedAmount(),
                "paidAmount", participant.getPaidAmount(),
                "remainingAmount", participant.getRemainingAmount(),
                "currency", groupPayment.getCurrency(),
                "dueDate", groupPayment.getDueDate(),
                "reminderCount", participant.getRemindersSent() != null ? participant.getRemindersSent() + 1 : 1,
                "timestamp", Instant.now()
            );

            kafkaTemplate.send("notification-events", participant.getUserId(), notification);
        });
    }
}