package com.waqiti.gdpr.domain;

/**
 * Types of data breaches as categorized by GDPR and cybersecurity frameworks
 */
public enum BreachType {
    /**
     * Confidentiality breach - unauthorized access or disclosure
     */
    CONFIDENTIALITY_BREACH,

    /**
     * Availability breach - loss of access to or destruction of data
     */
    AVAILABILITY_BREACH,

    /**
     * Integrity breach - unauthorized or accidental alteration of data
     */
    INTEGRITY_BREACH,

    /**
     * Ransomware attack
     */
    RANSOMWARE,

    /**
     * Phishing or social engineering attack
     */
    PHISHING,

    /**
     * Malware infection
     */
    MALWARE,

    /**
     * Insider threat or employee misconduct
     */
    INSIDER_THREAT,

    /**
     * Physical theft of devices or documents
     */
    PHYSICAL_THEFT,

    /**
     * Loss of devices or documents
     */
    PHYSICAL_LOSS,

    /**
     * SQL injection or other code injection
     */
    CODE_INJECTION,

    /**
     * Distributed Denial of Service attack
     */
    DDOS_ATTACK,

    /**
     * Man-in-the-middle attack
     */
    MITM_ATTACK,

    /**
     * Brute force attack
     */
    BRUTE_FORCE,

    /**
     * Credential stuffing or password attack
     */
    CREDENTIAL_ATTACK,

    /**
     * Zero-day exploit
     */
    ZERO_DAY_EXPLOIT,

    /**
     * Misconfiguration or human error
     */
    MISCONFIGURATION,

    /**
     * Third-party or supply chain breach
     */
    SUPPLY_CHAIN_BREACH,

    /**
     * API vulnerability exploitation
     */
    API_VULNERABILITY,

    /**
     * Cloud storage misconfiguration
     */
    CLOUD_MISCONFIGURATION,

    /**
     * Other type not listed above
     */
    OTHER
}
