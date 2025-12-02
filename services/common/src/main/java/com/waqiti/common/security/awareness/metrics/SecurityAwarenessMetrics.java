package com.waqiti.common.security.awareness.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityAwarenessMetrics {

    private final MeterRegistry meterRegistry;

    public void recordTrainingCompletion(String moduleCode, boolean passed) {
        meterRegistry.counter("security.training.completions",
                "module", moduleCode,
                "passed", String.valueOf(passed)
        ).increment();
    }

    public void recordPhishingTestResult(String result) {
        meterRegistry.counter("security.phishing.results",
                "result", result
        ).increment();
    }

    public void recordAssessmentCompletion(String assessmentType, boolean passed, int score) {
        meterRegistry.counter("security.assessment.completions",
                "type", assessmentType,
                "passed", String.valueOf(passed)
        ).increment();

        meterRegistry.gauge("security.assessment.score",
                score
        );
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordTimer(Timer.Sample sample, String operation) {
        sample.stop(meterRegistry.timer("security.awareness.operation.duration",
                "operation", operation
        ));
    }
}