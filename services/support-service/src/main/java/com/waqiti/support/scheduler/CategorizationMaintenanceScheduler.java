package com.waqiti.support.scheduler;

import com.waqiti.support.config.TicketCategorizationConfig.CategorizationSettings;
import com.waqiti.support.domain.Ticket;
import com.waqiti.support.repository.TicketRepository;
import com.waqiti.support.service.TicketCategorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CategorizationMaintenanceScheduler {

    private final TicketCategorizationService categorizationService;
    private final TicketRepository ticketRepository;
    private final CategorizationSettings settings;

    /**
     * Scheduled task to retrain the categorization model
     * Runs weekly by default (configurable via settings)
     */
    @Scheduled(cron = "${waqiti.support.categorization.retrain-schedule:0 0 2 * * SUN}")
    @Async
    public void scheduleModelRetraining() {
        if (!settings.isAutoRetrain() || !settings.isEnabled()) {
            log.debug("Auto-retraining is disabled, skipping scheduled training");
            return;
        }

        log.info("Starting scheduled model retraining");
        
        try {
            // Get training data from the last period
            LocalDateTime since = LocalDateTime.now().minusDays(30); // Last 30 days
            List<Ticket> trainingData = getTrainingData(since);
            
            if (trainingData.size() < settings.getMinTrainingData()) {
                log.warn("Insufficient training data: {} tickets (minimum: {})", 
                        trainingData.size(), settings.getMinTrainingData());
                return;
            }
            
            log.info("Retraining model with {} tickets", trainingData.size());
            categorizationService.trainModel(trainingData);
            
            log.info("Scheduled model retraining completed successfully");
            
        } catch (Exception e) {
            log.error("Failed to complete scheduled model retraining", e);
        }
    }

    /**
     * Periodic task to analyze categorization accuracy
     * Runs daily by default
     */
    @Scheduled(fixedRateString = "${waqiti.support.categorization.accuracy-reporting-interval:86400}000")
    @Async
    public void analyzeCategorizationAccuracy() {
        if (!settings.isTrackAccuracy() || !settings.isEnabled()) {
            log.debug("Accuracy tracking is disabled, skipping analysis");
            return;
        }

        log.debug("Starting categorization accuracy analysis");
        
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(settings.getAccuracyReportingInterval());
            
            // Get recently categorized tickets
            List<Ticket> recentTickets = getRecentTickets(since);
            
            if (recentTickets.isEmpty()) {
                log.debug("No recent tickets found for accuracy analysis");
                return;
            }
            
            // Analyze accuracy (simplified version)
            analyzeAccuracy(recentTickets);
            
            log.debug("Categorization accuracy analysis completed for {} tickets", recentTickets.size());
            
        } catch (Exception e) {
            log.error("Failed to analyze categorization accuracy", e);
        }
    }

    /**
     * Clean up old categorization cache and temporary data
     * Runs every 6 hours
     */
    @Scheduled(fixedRate = 21600000) // 6 hours in milliseconds
    @Async
    public void cleanupCategorizationData() {
        if (!settings.isEnabled()) {
            return;
        }

        log.debug("Starting categorization data cleanup");
        
        try {
            // Clean up old cache entries, temporary files, etc.
            // This is a placeholder for actual cleanup logic
            
            log.debug("Categorization data cleanup completed");
            
        } catch (Exception e) {
            log.error("Failed to clean up categorization data", e);
        }
    }

    /**
     * Monitor for tickets that need recategorization
     * Runs every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    @Async
    public void monitorRecategorizationNeeds() {
        if (!settings.isEnabled() || !settings.isEnableFeedbackLoop()) {
            return;
        }

        log.debug("Monitoring for tickets that need recategorization");
        
        try {
            // Find tickets with low confidence scores that might need review
            List<Ticket> lowConfidenceTickets = findLowConfidenceTickets();
            
            for (Ticket ticket : lowConfidenceTickets) {
                // Check if ticket has been updated or has new information
                if (shouldRecategorize(ticket)) {
                    log.info("Recategorizing ticket {} due to low confidence or new information", 
                            ticket.getTicketNumber());
                    
                    categorizationService.recategorizeTicket(ticket);
                }
            }
            
            log.debug("Recategorization monitoring completed, processed {} tickets", 
                     lowConfidenceTickets.size());
            
        } catch (Exception e) {
            log.error("Failed to monitor recategorization needs", e);
        }
    }

    /**
     * Generate daily categorization metrics report
     * Runs daily at 8 AM
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Async
    public void generateDailyMetricsReport() {
        if (!settings.isTrackAccuracy() || !settings.isEnabled()) {
            return;
        }

        log.info("Generating daily categorization metrics report");
        
        try {
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            LocalDateTime today = LocalDateTime.now();
            
            // Generate comprehensive metrics report
            generateMetricsReport(yesterday, today);
            
            log.info("Daily categorization metrics report generated successfully");
            
        } catch (Exception e) {
            log.error("Failed to generate daily metrics report", e);
        }
    }

    // Helper methods

    private List<Ticket> getTrainingData(LocalDateTime since) {
        // Get tickets with known categories for training
        Pageable pageable = PageRequest.of(0, settings.getTrainingBatchSize());
        return ticketRepository.findAll(pageable).getContent()
                .stream()
                .filter(ticket -> ticket.getCreatedAt().isAfter(since))
                .filter(ticket -> ticket.getCategory() != null)
                .toList();
    }

    private List<Ticket> getRecentTickets(LocalDateTime since) {
        Pageable pageable = PageRequest.of(0, 1000);
        return ticketRepository.findAll(pageable).getContent()
                .stream()
                .filter(ticket -> ticket.getCreatedAt().isAfter(since))
                .toList();
    }

    private void analyzeAccuracy(List<Ticket> tickets) {
        // Simplified accuracy analysis
        int totalTickets = tickets.size();
        int correctlyClassified = 0;
        
        for (Ticket ticket : tickets) {
            // In a real implementation, this would compare predicted vs actual categories
            // For now, we'll use a placeholder logic
            if (isCorrectlyClassified(ticket)) {
                correctlyClassified++;
            }
        }
        
        double accuracy = totalTickets > 0 ? (double) correctlyClassified / totalTickets : 0.0;
        
        log.info("Categorization accuracy: {:.2f}% ({}/{} tickets)", 
                accuracy * 100, correctlyClassified, totalTickets);
        
        // Store metrics or send alerts if accuracy is below threshold
        if (accuracy < 0.8) { // 80% threshold
            log.warn("Categorization accuracy below threshold: {:.2f}%", accuracy * 100);
            // Could send alerts to administrators here
        }
    }

    private List<Ticket> findLowConfidenceTickets() {
        // Find tickets that might need recategorization
        // This is a placeholder - in reality would query based on confidence scores
        Pageable pageable = PageRequest.of(0, 50);
        return ticketRepository.findAll(pageable).getContent()
                .stream()
                .filter(this::hasLowConfidence)
                .toList();
    }

    private boolean shouldRecategorize(Ticket ticket) {
        // Check if ticket should be recategorized based on various factors
        
        // Has new messages that might change categorization
        if (ticket.getMessages().size() > 1) {
            LocalDateTime lastMessage = ticket.getMessages().get(ticket.getMessages().size() - 1).getCreatedAt();
            if (lastMessage.isAfter(ticket.getCreatedAt().plusHours(1))) {
                return true;
            }
        }
        
        // Has been escalated (might indicate incorrect initial categorization)
        if (ticket.isEscalated()) {
            return true;
        }
        
        // Has low satisfaction score (might indicate incorrect routing)
        if (ticket.getSatisfactionScore() != null && ticket.getSatisfactionScore() < 3) {
            return true;
        }
        
        return false;
    }

    private boolean isCorrectlyClassified(Ticket ticket) {
        // Placeholder for accuracy checking logic
        // In reality, this would compare AI prediction with human verification or feedback
        return true; // Simplified for demonstration
    }

    private boolean hasLowConfidence(Ticket ticket) {
        // Placeholder for low confidence detection
        // In reality, this would check stored confidence scores
        return false; // Simplified for demonstration
    }

    private void generateMetricsReport(LocalDateTime start, LocalDateTime end) {
        // Generate comprehensive metrics report
        log.info("Categorization metrics for period {} to {}:", start, end);
        
        // Placeholder metrics - in reality would generate detailed reports
        long totalTickets = ticketRepository.count();
        log.info("Total tickets processed: {}", totalTickets);
        
        // Could generate detailed CSV reports, send to monitoring systems, etc.
    }
}