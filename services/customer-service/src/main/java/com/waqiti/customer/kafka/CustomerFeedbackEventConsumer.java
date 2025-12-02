package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.customer.service.CustomerFeedbackService;
import com.waqiti.customer.service.SentimentAnalysisService;
import com.waqiti.customer.service.ProductImprovementService;
import com.waqiti.customer.service.ComplaintResolutionService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.CustomerFeedback;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #225: Customer Feedback Event Consumer
 * Processes NPS surveys, complaint handling, and sentiment analysis
 * Implements 12-step zero-tolerance processing for customer feedback lifecycle
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerFeedbackEventConsumer extends BaseKafkaConsumer {

    private final CustomerFeedbackService feedbackService;
    private final SentimentAnalysisService sentimentService;
    private final ProductImprovementService improvementService;
    private final ComplaintResolutionService complaintService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "customer-feedback-events", groupId = "customer-feedback-group")
    @CircuitBreaker(name = "customer-feedback-consumer")
    @Retry(name = "customer-feedback-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCustomerFeedbackEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "customer-feedback-event");
        
        try {
            log.info("Step 1: Processing customer feedback event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String customerId = eventData.path("customerId").asText();
            String feedbackType = eventData.path("feedbackType").asText();
            String rating = eventData.path("rating").asText();
            String comments = eventData.path("comments").asText();
            String productArea = eventData.path("productArea").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            String channel = eventData.path("channel").asText();
            
            log.info("Step 2: Extracted feedback details: customerId={}, type={}, rating={}, area={}", 
                    customerId, feedbackType, rating, productArea);
            
            // Step 3: Customer validation and context gathering
            log.info("Step 3: Validating customer and gathering interaction context");
            Customer customer = feedbackService.validateCustomerAccess(customerId);
            if (customer == null) {
                throw new IllegalStateException("Customer not found: " + customerId);
            }
            
            CustomerFeedback feedback = feedbackService.createFeedbackRecord(eventData, customer);
            feedbackService.enrichWithCustomerContext(feedback, customer);
            
            // Step 4: Sentiment analysis and categorization
            log.info("Step 4: Performing sentiment analysis and feedback categorization");
            String sentiment = sentimentService.analyzeSentiment(comments);
            feedbackService.categorizeFeedback(feedback, sentiment);
            
            if ("NEGATIVE".equals(sentiment) && Integer.parseInt(rating) <= 3) {
                feedbackService.flagForImmediateAttention(feedback);
            }
            
            // Step 5: NPS calculation and trend analysis
            log.info("Step 5: Calculating NPS score and analyzing customer satisfaction trends");
            if ("NPS_SURVEY".equals(feedbackType)) {
                int npsScore = Integer.parseInt(rating);
                feedbackService.updateNPSMetrics(customer, npsScore, timestamp);
                feedbackService.categorizeNPSResponse(feedback, npsScore);
                
                if (npsScore <= 6) { // Detractor
                    complaintService.initiateDetractorFollowUp(customer, feedback);
                }
            }
            
            // Step 6: Complaint identification and escalation
            log.info("Step 6: Identifying potential complaints and initiating escalation procedures");
            if (feedbackService.isComplaintIndicator(feedback, sentiment)) {
                complaintService.convertToFormalComplaint(feedback);
                complaintService.assignComplaintHandler(feedback);
                complaintService.startComplaintResolutionProcess(feedback);
                
                if (feedbackService.isRegulatoryComplaint(feedback)) {
                    complaintService.flagForRegulatoryReporting(feedback);
                }
            }
            
            // Step 7: Product improvement opportunity identification
            log.info("Step 7: Identifying product improvement opportunities and feature requests");
            if (feedbackService.containsProductSuggestion(comments)) {
                improvementService.extractProductSuggestions(feedback, comments);
                improvementService.routeToProductTeam(feedback, productArea);
            }
            
            improvementService.updateFeatureRequestDatabase(feedback);
            improvementService.analyzeTrendingIssues(productArea, feedback);
            
            // Step 8: Customer journey and experience mapping
            log.info("Step 8: Mapping feedback to customer journey and experience touchpoints");
            feedbackService.mapToCustomerJourney(feedback, customer);
            feedbackService.identifyPainPoints(feedback, customer.getCustomerSegment());
            feedbackService.updateExperienceMetrics(customer, feedback);
            
            // Step 9: Response and follow-up orchestration
            log.info("Step 9: Orchestrating customer response and follow-up communications");
            feedbackService.sendFeedbackAcknowledgment(customer, feedback);
            
            if (Integer.parseInt(rating) <= 3) {
                feedbackService.scheduleFollowUpCall(customer, feedback);
                feedbackService.offerServiceRecovery(customer, feedback);
            }
            
            feedbackService.updateCustomerEngagementScore(customer, feedback);
            
            // Step 10: Analytics and reporting integration
            log.info("Step 10: Integrating with analytics platform and generating insights");
            feedbackService.updateCustomerSatisfactionDashboard(feedback);
            feedbackService.contributeToBenchmarkingMetrics(feedback, customer.getIndustrySegment());
            improvementService.updateProductRoadmapInsights(feedback, productArea);
            
            // Step 11: Quality assurance and audit documentation
            log.info("Step 11: Creating quality assurance records and audit documentation");
            feedbackService.createQualityAssuranceRecord(feedback);
            feedbackService.archiveFeedbackData(feedback);
            complaintService.documentRegulatoryCompliance(feedback);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed customer feedback: feedbackId={}, eventId={}", 
                    feedback.getFeedbackId(), eventId);
            
        } catch (Exception e) {
            log.error("Error processing customer feedback event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("customerId") || 
            !eventData.has("feedbackType") || !eventData.has("rating") ||
            !eventData.has("productArea") || !eventData.has("timestamp") ||
            !eventData.has("channel")) {
            throw new IllegalArgumentException("Invalid customer feedback event structure");
        }
    }
}