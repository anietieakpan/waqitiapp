package com.waqiti.common.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@Slf4j
public class MonitoringService {
    public void recordMetric(String name, double value, Map<String, String> tags) {
        log.debug("Metric: {} = {} (tags: {})", name, value, tags);
    }

    public void recordEvent(String eventName, Map<String, Object> properties) {
        log.debug("Event: {} (properties: {})", eventName, properties);
    }
}
