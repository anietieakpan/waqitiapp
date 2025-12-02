-- ============================================================================
-- PCI DSS REQ 12.6 - Security Training Modules Seed Data
-- ============================================================================

-- Insert mandatory training modules for all employees (PCI DSS REQ 12.6.1)

-- 1. Security Awareness Fundamentals (MANDATORY - ALL EMPLOYEES)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'SEC-101',
    'Security Awareness Fundamentals',
    'Essential security awareness training covering basic security principles, data protection, password management, and incident reporting.',
    'VIDEO',
    '/training/videos/security-awareness-fundamentals.mp4',
    30,
    'BASIC',
    true,
    80,
    '12.6.1',
    ARRAY['ALL'],
    '00000000-0000-0000-0000-000000000000'
);

-- 2. PCI DSS Overview (MANDATORY - ALL EMPLOYEES)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'PCI-101',
    'PCI DSS Compliance Overview',
    'Understanding PCI DSS requirements, cardholder data protection, and your role in maintaining compliance.',
    'INTERACTIVE',
    '/training/interactive/pci-dss-overview',
    45,
    'BASIC',
    true,
    85,
    '12.6.1',
    ARRAY['ALL'],
    '00000000-0000-0000-0000-000000000000'
);

-- 3. Phishing Recognition (MANDATORY - ALL EMPLOYEES)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'PHISH-101',
    'Phishing Attack Recognition & Reporting',
    'Learn to identify phishing attempts, social engineering tactics, and how to report suspicious emails.',
    'INTERACTIVE',
    '/training/interactive/phishing-recognition',
    25,
    'BASIC',
    true,
    80,
    '12.6.3.1',
    ARRAY['ALL'],
    '00000000-0000-0000-0000-000000000000'
);

-- 4. Data Privacy & GDPR (MANDATORY - ALL EMPLOYEES)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'PRIVACY-101',
    'Data Privacy & GDPR Compliance',
    'Understanding customer data privacy rights, GDPR requirements, and proper data handling procedures.',
    'VIDEO',
    '/training/videos/data-privacy-gdpr.mp4',
    35,
    'BASIC',
    true,
    85,
    '12.6.1',
    ARRAY['ALL'],
    '00000000-0000-0000-0000-000000000000'
);

-- 5. Incident Response & Reporting (MANDATORY - ALL EMPLOYEES)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'INCIDENT-101',
    'Security Incident Recognition & Reporting',
    'Recognizing security incidents, data breaches, and following proper reporting procedures.',
    'INTERACTIVE',
    '/training/interactive/incident-reporting',
    20,
    'BASIC',
    true,
    80,
    '12.6.1',
    ARRAY['ALL'],
    '00000000-0000-0000-0000-000000000000'
);

-- ============================================================================
-- Advanced Modules for Technical Roles (PCI DSS REQ 12.6.3)
-- ============================================================================

-- 6. Secure Coding Practices (MANDATORY - DEVELOPERS)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'SECURE-CODE-301',
    'Secure Coding Practices for Financial Applications',
    'OWASP Top 10 vulnerabilities, input validation, SQL injection prevention, XSS protection, and secure authentication.',
    'VIDEO',
    '/training/videos/secure-coding-practices.mp4',
    90,
    'ADVANCED',
    true,
    85,
    '12.6.3',
    ARRAY['DEVELOPER', 'TECH_LEAD'],
    '00000000-0000-0000-0000-000000000000'
);

-- 7. Database Security (MANDATORY - DBAs, BACKEND DEVELOPERS)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'DB-SEC-301',
    'Database Security & Encryption Best Practices',
    'Database encryption, access control, audit logging, SQL injection prevention, and secure backup procedures.',
    'INTERACTIVE',
    '/training/interactive/database-security',
    75,
    'ADVANCED',
    true,
    85,
    '12.6.3',
    ARRAY['DBA', 'DEVELOPER'],
    '00000000-0000-0000-0000-000000000000'
);

-- 8. API Security (MANDATORY - BACKEND DEVELOPERS)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'API-SEC-301',
    'API Security & OAuth 2.0 Implementation',
    'RESTful API security, OAuth 2.0, JWT tokens, rate limiting, input validation, and API authentication best practices.',
    'VIDEO',
    '/training/videos/api-security.mp4',
    60,
    'ADVANCED',
    true,
    85,
    '12.6.3',
    ARRAY['DEVELOPER', 'BACKEND_DEVELOPER'],
    '00000000-0000-0000-0000-000000000000'
);

-- 9. Cloud Security (MANDATORY - DEVOPS, CLOUD ENGINEERS)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'CLOUD-SEC-301',
    'Cloud Security & Kubernetes Hardening',
    'AWS/GCP security best practices, Kubernetes security, container security, secrets management, and infrastructure as code security.',
    'INTERACTIVE',
    '/training/interactive/cloud-security',
    90,
    'ADVANCED',
    true,
    85,
    '12.6.3',
    ARRAY['DEVOPS', 'CLOUD_ENGINEER', 'SECURITY_ADMIN'],
    '00000000-0000-0000-0000-000000000000'
);

-- 10. Cryptography & Key Management (MANDATORY - SECURITY ADMINS)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'CRYPTO-401',
    'Cryptography & HSM Key Management',
    'Encryption algorithms, HSM usage, key rotation, TLS/SSL certificates, and cryptographic best practices for financial systems.',
    'VIDEO',
    '/training/videos/cryptography-key-management.mp4',
    120,
    'ADVANCED',
    true,
    90,
    '12.6.3',
    ARRAY['SECURITY_ADMIN', 'SECURITY_ARCHITECT'],
    '00000000-0000-0000-0000-000000000000'
);

-- 11. Penetration Testing & Vulnerability Assessment (MANDATORY - SECURITY TEAM)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'PENTEST-401',
    'Penetration Testing & Vulnerability Assessment',
    'PCI DSS REQ 11.3 penetration testing, vulnerability scanning, threat modeling, and security testing methodologies.',
    'INTERACTIVE',
    '/training/interactive/penetration-testing',
    150,
    'ADVANCED',
    true,
    90,
    '12.6.3',
    ARRAY['SECURITY_ADMIN', 'SECURITY_ANALYST', 'PENTESTER'],
    '00000000-0000-0000-0000-000000000000'
);

-- 12. Threat Intelligence & Incident Response (MANDATORY - SOC TEAM)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'THREAT-401',
    'Threat Intelligence & Advanced Incident Response',
    'Threat landscape analysis, APT detection, SIEM monitoring, incident response playbooks, and forensic investigation.',
    'VIDEO',
    '/training/videos/threat-intelligence.mp4',
    180,
    'ADVANCED',
    true,
    90,
    '12.6.3.1',
    ARRAY['SECURITY_ANALYST', 'SOC_ANALYST', 'INCIDENT_RESPONDER'],
    '00000000-0000-0000-0000-000000000000'
);

-- ============================================================================
-- Remedial Training Modules (for employees who fail phishing tests)
-- ============================================================================

-- 13. Phishing Remedial Training
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'PHISH-REMEDIAL',
    'Phishing Awareness Remedial Training',
    'Intensive phishing recognition training for employees who failed phishing simulation tests.',
    'INTERACTIVE',
    '/training/interactive/phishing-remedial',
    40,
    'INTERMEDIATE',
    false, -- Assigned dynamically
    90, -- Higher passing score for remedial
    '12.6.3.1',
    ARRAY['ALL'],
    '00000000-0000-0000-0000-000000000000'
);

-- ============================================================================
-- Optional Advanced Modules (for professional development)
-- ============================================================================

-- 14. Blockchain Security (OPTIONAL - BLOCKCHAIN DEVELOPERS)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'BLOCKCHAIN-301',
    'Blockchain Security & Smart Contract Auditing',
    'Smart contract vulnerabilities, reentrancy attacks, private key management, and blockchain security best practices.',
    'VIDEO',
    '/training/videos/blockchain-security.mp4',
    90,
    'ADVANCED',
    false,
    85,
    NULL,
    ARRAY['BLOCKCHAIN_DEVELOPER', 'SECURITY_ADMIN'],
    '00000000-0000-0000-0000-000000000000'
);

-- 15. DevSecOps & CI/CD Security (OPTIONAL - DEVOPS)
INSERT INTO security_training_modules (
    module_code,
    title,
    description,
    content_type,
    content_url,
    duration_minutes,
    difficulty_level,
    is_mandatory,
    passing_score_percentage,
    pci_requirement,
    target_roles,
    created_by
) VALUES (
    'DEVSECOPS-301',
    'DevSecOps & Secure CI/CD Pipelines',
    'Security scanning in CI/CD, container security, infrastructure as code security, and shift-left security practices.',
    'INTERACTIVE',
    '/training/interactive/devsecops',
    75,
    'ADVANCED',
    false,
    85,
    NULL,
    ARRAY['DEVOPS', 'DEVELOPER'],
    '00000000-0000-0000-0000-000000000000'
);

-- ============================================================================
-- Update timestamps
-- ============================================================================
UPDATE security_training_modules
SET updated_at = NOW()
WHERE created_at = updated_at;

COMMENT ON TABLE security_training_modules IS 'PCI DSS REQ 12.6 - Security awareness training modules for all personnel';
