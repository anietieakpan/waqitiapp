package com.waqiti.chaos.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.chaos.core.ChaosResult;
import com.waqiti.chaos.orchestrator.ChaosSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChaosReportGenerator {
    
    private final ObjectMapper objectMapper;
    
    @Value("${chaos.reporting.output-dir:./chaos-reports}")
    private String outputDirectory;
    
    @Value("${chaos.reporting.format:html}")
    private String reportFormat;
    
    public void generateReport(ChaosSession session) {
        try {
            createOutputDirectory();
            
            String filename = generateFilename(session);
            
            switch (reportFormat.toLowerCase()) {
                case "html":
                    generateHtmlReport(session, filename);
                    break;
                case "json":
                    generateJsonReport(session, filename);
                    break;
                case "markdown":
                    generateMarkdownReport(session, filename);
                    break;
                default:
                    generateHtmlReport(session, filename);
            }
            
            log.info("Chaos report generated: {}", filename);
            
        } catch (Exception e) {
            log.error("Failed to generate chaos report", e);
        }
    }
    
    private void createOutputDirectory() throws IOException {
        Path dir = Paths.get(outputDirectory);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }
    
    private String generateFilename(ChaosSession session) {
        String timestamp = session.getStartTime()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        return String.format("chaos-report_%s_%s", timestamp, session.getSessionId().substring(0, 8));
    }
    
    private void generateHtmlReport(ChaosSession session, String filename) throws IOException {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n")
            .append("<html>\n")
            .append("<head>\n")
            .append("    <title>Chaos Engineering Report</title>\n")
            .append("    <style>\n")
            .append(getCssStyles())
            .append("    </style>\n")
            .append("</head>\n")
            .append("<body>\n");
        
        // Header
        html.append("<div class='header'>\n")
            .append("    <h1>Chaos Engineering Report</h1>\n")
            .append("    <div class='session-info'>\n")
            .append("        <p><strong>Session ID:</strong> ").append(session.getSessionId()).append("</p>\n")
            .append("        <p><strong>Start Time:</strong> ").append(session.getStartTime()).append("</p>\n")
            .append("        <p><strong>Duration:</strong> ").append(formatDuration(session.getDuration())).append("</p>\n")
            .append("        <p><strong>Status:</strong> ").append(getSessionStatus(session)).append("</p>\n")
            .append("    </div>\n")
            .append("</div>\n");
        
        // Summary
        html.append("<div class='summary'>\n")
            .append("    <h2>Summary</h2>\n")
            .append("    <div class='metrics'>\n")
            .append("        <div class='metric'>\n")
            .append("            <h3>").append(session.getTotalExperiments()).append("</h3>\n")
            .append("            <p>Total Experiments</p>\n")
            .append("        </div>\n")
            .append("        <div class='metric success'>\n")
            .append("            <h3>").append(session.getSuccessfulExperiments()).append("</h3>\n")
            .append("            <p>Successful</p>\n")
            .append("        </div>\n")
            .append("        <div class='metric failure'>\n")
            .append("            <h3>").append(session.getFailedExperiments()).append("</h3>\n")
            .append("            <p>Failed</p>\n")
            .append("        </div>\n")
            .append("        <div class='metric'>\n")
            .append("            <h3>").append(String.format("%.1f%%", session.getSuccessRate() * 100)).append("</h3>\n")
            .append("            <p>Success Rate</p>\n")
            .append("        </div>\n")
            .append("    </div>\n")
            .append("</div>\n");
        
        // Validation Results
        if (session.getPreValidation() != null) {
            html.append(generateValidationSection("Pre-Chaos Validation", session.getPreValidation()));
        }
        
        if (session.getPostValidation() != null) {
            html.append(generateValidationSection("Post-Chaos Validation", session.getPostValidation()));
        }
        
        // Experiment Results
        if (session.getResults() != null) {
            html.append("<div class='experiments'>\n")
                .append("    <h2>Experiment Results</h2>\n");
            
            for (ChaosResult result : session.getResults()) {
                html.append(generateExperimentSection(result));
            }
            
            html.append("</div>\n");
        }
        
        // Resilience Test Results
        if (session.getResilienceTestResult() != null) {
            html.append("<div class='resilience'>\n")
                .append("    <h2>Resilience Test Results</h2>\n")
                .append(generateExperimentSection(session.getResilienceTestResult()))
                .append("</div>\n");
        }
        
        html.append("</body>\n</html>");
        
        writeFile(filename + ".html", html.toString());
    }
    
    private void generateJsonReport(ChaosSession session, String filename) throws IOException {
        Map<String, Object> report = new HashMap<>();
        report.put("sessionId", session.getSessionId());
        report.put("startTime", session.getStartTime());
        report.put("endTime", session.getEndTime());
        report.put("duration", session.getDuration().toString());
        report.put("config", session.getConfig());
        report.put("totalExperiments", session.getTotalExperiments());
        report.put("successfulExperiments", session.getSuccessfulExperiments());
        report.put("failedExperiments", session.getFailedExperiments());
        report.put("successRate", session.getSuccessRate());
        report.put("results", session.getResults());
        report.put("resilienceTestResult", session.getResilienceTestResult());
        report.put("preValidation", session.getPreValidation());
        report.put("postValidation", session.getPostValidation());
        report.put("aborted", session.isAborted());
        report.put("degraded", session.isDegraded());
        
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        writeFile(filename + ".json", json);
    }
    
    private void generateMarkdownReport(ChaosSession session, String filename) throws IOException {
        StringBuilder md = new StringBuilder();
        
        md.append("# Chaos Engineering Report\n\n");
        
        // Session Info
        md.append("## Session Information\n\n");
        md.append("- **Session ID:** ").append(session.getSessionId()).append("\n");
        md.append("- **Start Time:** ").append(session.getStartTime()).append("\n");
        md.append("- **Duration:** ").append(formatDuration(session.getDuration())).append("\n");
        md.append("- **Status:** ").append(getSessionStatus(session)).append("\n\n");
        
        // Summary
        md.append("## Summary\n\n");
        md.append("| Metric | Value |\n");
        md.append("|--------|-------|\n");
        md.append("| Total Experiments | ").append(session.getTotalExperiments()).append(" |\n");
        md.append("| Successful | ").append(session.getSuccessfulExperiments()).append(" |\n");
        md.append("| Failed | ").append(session.getFailedExperiments()).append(" |\n");
        md.append("| Success Rate | ").append(String.format("%.1f%%", session.getSuccessRate() * 100)).append(" |\n\n");
        
        // Experiment Results
        if (session.getResults() != null) {
            md.append("## Experiment Results\n\n");
            
            for (ChaosResult result : session.getResults()) {
                md.append("### ").append(result.getExperimentName()).append("\n\n");
                md.append("- **Status:** ").append(result.isSuccess() ? "✅ Success" : "❌ Failed").append("\n");
                md.append("- **Duration:** ").append(formatDuration(result.getDuration())).append("\n");
                md.append("- **Message:** ").append(result.getMessage() != null ? result.getMessage() : "N/A").append("\n");
                
                if (result.getMetrics() != null && !result.getMetrics().isEmpty()) {
                    md.append("- **Metrics:**\n");
                    result.getMetrics().forEach((key, value) -> 
                        md.append("  - ").append(key).append(": ").append(value).append("\n"));
                }
                md.append("\n");
            }
        }
        
        writeFile(filename + ".md", md.toString());
    }
    
    private String getCssStyles() {
        return """
            body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
            .header { background-color: #2c3e50; color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; }
            .header h1 { margin: 0 0 15px 0; }
            .session-info p { margin: 5px 0; }
            .summary { background-color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            .metrics { display: flex; gap: 20px; }
            .metric { text-align: center; flex: 1; padding: 15px; border: 2px solid #ecf0f1; border-radius: 8px; }
            .metric.success { border-color: #27ae60; }
            .metric.failure { border-color: #e74c3c; }
            .metric h3 { margin: 0; font-size: 2em; }
            .metric p { margin: 10px 0 0 0; color: #7f8c8d; }
            .experiments, .resilience { background-color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            .experiment { border-left: 4px solid #3498db; padding: 15px; margin: 10px 0; background-color: #f8f9fa; }
            .experiment.success { border-left-color: #27ae60; }
            .experiment.failure { border-left-color: #e74c3c; }
            .experiment h3 { margin: 0 0 10px 0; }
            .experiment-metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 10px; margin-top: 10px; }
            .experiment-metric { background-color: white; padding: 10px; border-radius: 4px; }
            """;
    }
    
    private String generateValidationSection(String title, Object validation) {
        StringBuilder html = new StringBuilder();
        
        html.append("<div class='validation'>\n")
            .append("    <h2>").append(title).append("</h2>\n")
            .append("    <pre>").append(objectMapper.valueToTree(validation).toPrettyString()).append("</pre>\n")
            .append("</div>\n");
        
        return html.toString();
    }
    
    private String generateExperimentSection(ChaosResult result) {
        StringBuilder html = new StringBuilder();
        
        String statusClass = result.isSuccess() ? "success" : "failure";
        String statusIcon = result.isSuccess() ? "✅" : "❌";
        
        html.append("<div class='experiment ").append(statusClass).append("'>\n")
            .append("    <h3>").append(statusIcon).append(" ").append(result.getExperimentName()).append("</h3>\n")
            .append("    <p><strong>Status:</strong> ").append(result.isSuccess() ? "Success" : "Failed").append("</p>\n")
            .append("    <p><strong>Duration:</strong> ").append(formatDuration(result.getDuration())).append("</p>\n");
        
        if (result.getMessage() != null) {
            html.append("    <p><strong>Message:</strong> ").append(result.getMessage()).append("</p>\n");
        }
        
        if (result.getMetrics() != null && !result.getMetrics().isEmpty()) {
            html.append("    <div class='experiment-metrics'>\n");
            result.getMetrics().forEach((key, value) -> 
                html.append("        <div class='experiment-metric'>\n")
                    .append("            <strong>").append(key).append(":</strong> ").append(value).append("\n")
                    .append("        </div>\n"));
            html.append("    </div>\n");
        }
        
        html.append("</div>\n");
        
        return html.toString();
    }
    
    private String getSessionStatus(ChaosSession session) {
        if (session.isAborted()) return "❌ Aborted";
        if (session.isDegraded()) return "⚠️ Degraded";
        if (session.getError() != null) return "❌ Error";
        if (session.getSuccessRate() == 1.0) return "✅ All Passed";
        if (session.getSuccessRate() > 0.8) return "✅ Mostly Passed";
        return "❌ Failed";
    }
    
    private String formatDuration(java.time.Duration duration) {
        if (duration == null) return "N/A";
        
        long seconds = duration.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    private void writeFile(String filename, String content) throws IOException {
        Path filePath = Paths.get(outputDirectory, filename);
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write(content);
        }
    }
}