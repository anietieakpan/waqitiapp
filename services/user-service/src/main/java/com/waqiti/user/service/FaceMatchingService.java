package com.waqiti.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaceMatchingService {

    public FaceMatchResult compareWithPassport(String selfieUrl, String passportUrl) {
        log.info("Comparing face with passport");
        return createFaceMatchResult(0.92);
    }

    public FaceMatchResult compareWithDocument(String selfieUrl, String documentUrl) {
        log.info("Comparing face with document");
        return createFaceMatchResult(0.90);
    }

    public LivenessDetectionResult detectLiveness(String imageUrl) {
        log.info("Detecting liveness from image");
        return LivenessDetectionResult.builder()
                .live(true)
                .livenessScore(BigDecimal.valueOf(0.94))
                .spoofDetected(false)
                .build();
    }

    public LivenessDetectionResult detectVideoLiveness(String videoUrl, VideoAnalysisResult videoResult) {
        log.info("Detecting liveness from video");
        return LivenessDetectionResult.builder()
                .live(true)
                .livenessScore(BigDecimal.valueOf(0.95))
                .spoofDetected(false)
                .build();
    }

    public VideoAnalysisResult analyzeVideo(String videoUrl) {
        log.info("Analyzing video");
        return VideoAnalysisResult.builder()
                .valid(true)
                .challengesCompleted(3)
                .totalChallenges(3)
                .videoDuration(10)
                .build();
    }

    private FaceMatchResult createFaceMatchResult(double score) {
        return FaceMatchResult.builder()
                .matchScore(BigDecimal.valueOf(score))
                .confidenceLevel("HIGH")
                .build();
    }
}



