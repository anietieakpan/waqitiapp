package com.waqiti.gamification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationSchedulerService {
    
    private final UserPointsService userPointsService;
    
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void resetPeriodicalPoints() {
        log.info("Starting periodical points reset");
        try {
            userPointsService.resetPeriodicalPoints();
            log.info("Completed periodical points reset");
        } catch (Exception e) {
            log.error("Error during periodical points reset", e);
        }
    }
    
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void deactivateExpiredMultipliers() {
        try {
            userPointsService.deactivateExpiredMultipliers();
        } catch (Exception e) {
            log.error("Error deactivating expired multipliers", e);
        }
    }
}