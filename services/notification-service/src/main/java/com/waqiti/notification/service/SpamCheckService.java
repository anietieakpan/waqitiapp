package com.waqiti.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for checking email content for spam indicators
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpamCheckService {
    
    private static final Set<String> SPAM_KEYWORDS = new HashSet<>(Arrays.asList(
        "free", "guaranteed", "no obligation", "risk free", "satisfaction guaranteed",
        "100% satisfied", "additional income", "be your own boss", "work from home",
        "make money fast", "get rich quick", "financial freedom", "extra cash",
        "urgent", "immediate", "act now", "limited time", "expires today",
        "click here", "buy now", "order now", "call now", "don't delay",
        "winner", "congratulations", "you have won", "claim your prize",
        "viagra", "cialis", "pharmacy", "prescription", "medication"
    ));
    
    private static final Set<String> SUSPICIOUS_PHRASES = new HashSet<>(Arrays.asList(
        "as seen on", "get started now", "if you are not satisfied",
        "increase sales", "increase traffic", "miracle", "once in lifetime",
        "one time", "pennies a day", "potential earnings", "pure profit",
        "risk free", "satisfaction guaranteed", "save big money", "save up to",
        "special promotion", "this is not spam", "unsolicited", "while you sleep",
        "will not believe your eyes", "you are receiving this", "dear friend"
    ));
    
    private static final Pattern EXCESSIVE_CAPS = Pattern.compile("[A-Z]{5,}");
    private static final Pattern EXCESSIVE_EXCLAMATION = Pattern.compile("!{3,}");
    private static final Pattern SUSPICIOUS_URLS = Pattern.compile("(bit\\.ly|tinyurl|goo\\.gl|t\\.co)/\\w+");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    
    public double calculateSpamScore(String subject, String body) {
        double score = 0.0;
        
        if (subject == null && body == null) {
            return 0.0;
        }
        
        String content = (subject != null ? subject : "") + " " + (body != null ? body : "");
        content = content.toLowerCase();
        
        // Check for spam keywords
        score += checkSpamKeywords(content);
        
        // Check for suspicious phrases
        score += checkSuspiciousPhrases(content);
        
        // Check formatting indicators
        score += checkFormatting(subject, body);
        
        // Check for suspicious URLs
        score += checkSuspiciousUrls(content);
        
        // Check for excessive email addresses
        score += checkExcessiveEmails(content);
        
        // Normalize score to 0-1 range
        return Math.min(1.0, score);
    }
    
    public boolean isSpam(String subject, String body, double threshold) {
        return calculateSpamScore(subject, body) > threshold;
    }
    
    public SpamAnalysisResult analyzeContent(String subject, String body) {
        SpamAnalysisResult result = new SpamAnalysisResult();
        result.setSubject(subject);
        result.setSpamScore(calculateSpamScore(subject, body));
        result.setSpam(result.getSpamScore() > 0.5);
        
        String content = (subject != null ? subject : "") + " " + (body != null ? body : "");
        content = content.toLowerCase();
        
        // Identify specific issues
        if (checkSpamKeywords(content) > 0) {
            result.addFlag("Contains spam keywords", "warning");
        }
        
        if (checkSuspiciousPhrases(content) > 0) {
            result.addFlag("Contains suspicious phrases", "warning");
        }
        
        if (EXCESSIVE_CAPS.matcher(content).find()) {
            result.addFlag("Excessive capital letters", "minor");
        }
        
        if (EXCESSIVE_EXCLAMATION.matcher(content).find()) {
            result.addFlag("Excessive exclamation marks", "minor");
        }
        
        if (SUSPICIOUS_URLS.matcher(content).find()) {
            result.addFlag("Contains suspicious URLs", "warning");
        }
        
        return result;
    }
    
    private double checkSpamKeywords(String content) {
        double score = 0.0;
        
        for (String keyword : SPAM_KEYWORDS) {
            if (content.contains(keyword)) {
                score += 0.1;
                log.debug("Spam keyword found: {}", keyword);
            }
        }
        
        return Math.min(0.5, score); // Cap at 0.5
    }
    
    private double checkSuspiciousPhrases(String content) {
        double score = 0.0;
        
        for (String phrase : SUSPICIOUS_PHRASES) {
            if (content.contains(phrase)) {
                score += 0.05;
                log.debug("Suspicious phrase found: {}", phrase);
            }
        }
        
        return Math.min(0.3, score); // Cap at 0.3
    }
    
    private double checkFormatting(String subject, String body) {
        double score = 0.0;
        
        String content = (subject != null ? subject : "") + " " + (body != null ? body : "");
        
        // Check for excessive capital letters
        if (EXCESSIVE_CAPS.matcher(content).find()) {
            score += 0.1;
            log.debug("Excessive caps detected");
        }
        
        // Check for excessive exclamation marks
        if (EXCESSIVE_EXCLAMATION.matcher(content).find()) {
            score += 0.05;
            log.debug("Excessive exclamation marks detected");
        }
        
        // Check subject line length and characteristics
        if (subject != null) {
            if (subject.length() > 100) {
                score += 0.05;
            }
            
            long capsCount = subject.chars().filter(Character::isUpperCase).count();
            if (capsCount > subject.length() * 0.7) {
                score += 0.1;
            }
        }
        
        return score;
    }
    
    private double checkSuspiciousUrls(String content) {
        if (SUSPICIOUS_URLS.matcher(content).find()) {
            log.debug("Suspicious URL detected");
            return 0.2;
        }
        return 0.0;
    }
    
    private double checkExcessiveEmails(String content) {
        java.util.regex.Matcher matcher = EMAIL_PATTERN.matcher(content);
        int emailCount = 0;
        while (matcher.find()) {
            emailCount++;
        }
        
        if (emailCount > 3) {
            log.debug("Excessive email addresses detected: {}", emailCount);
            return Math.min(0.2, emailCount * 0.05);
        }
        
        return 0.0;
    }
    
    public String sanitizeContent(String content, double spamScore) {
        if (spamScore < 0.3) {
            return content; // Low spam score, no changes needed
        }
        
        if (content == null) return null;
        
        String sanitized = content;
        
        // Remove excessive caps (convert to sentence case)
        sanitized = EXCESSIVE_CAPS.matcher(sanitized).replaceAll(match -> {
            String word = match.group();
            return word.charAt(0) + word.substring(1).toLowerCase();
        });
        
        // Reduce excessive exclamation marks
        sanitized = EXCESSIVE_EXCLAMATION.matcher(sanitized).replaceAll("!");
        
        // Add disclaimer if high spam score
        if (spamScore > 0.7) {
            sanitized = "[This message has been modified for compliance]\n\n" + sanitized;
        }
        
        return sanitized;
    }
    
    // Inner class for detailed spam analysis results
    public static class SpamAnalysisResult {
        private String subject;
        private double spamScore;
        private boolean isSpam;
        private java.util.List<SpamFlag> flags = new java.util.ArrayList<>();
        
        public void addFlag(String message, String severity) {
            flags.add(new SpamFlag(message, severity));
        }
        
        // Getters and setters
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        
        public double getSpamScore() { return spamScore; }
        public void setSpamScore(double spamScore) { this.spamScore = spamScore; }
        
        public boolean isSpam() { return isSpam; }
        public void setSpam(boolean spam) { isSpam = spam; }
        
        public java.util.List<SpamFlag> getFlags() { return flags; }
        public void setFlags(java.util.List<SpamFlag> flags) { this.flags = flags; }
        
        public boolean hasFlags() { return !flags.isEmpty(); }
        
        public String getScoreDescription() {
            if (spamScore < 0.2) return "Low risk";
            if (spamScore < 0.5) return "Medium risk";
            if (spamScore < 0.8) return "High risk";
            return "Very high risk";
        }
    }
    
    public static class SpamFlag {
        private String message;
        private String severity;
        
        public SpamFlag(String message, String severity) {
            this.message = message;
            this.severity = severity;
        }
        
        public String getMessage() { return message; }
        public String getSeverity() { return severity; }
    }
}