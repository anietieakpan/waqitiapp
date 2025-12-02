-- ============================================================================
-- V2025.10.26.008__insert_seed_data.sql
-- Seed Data for Security Awareness Program
-- ============================================================================

-- Insert default training modules
INSERT INTO security_training_modules
(id, module_code, title, description, pci_requirement, is_mandatory, target_roles,
 estimated_duration_minutes, passing_score_percentage, is_active)
VALUES
    (gen_random_uuid(), 'PCI-12.6.1-001', 'PCI DSS Fundamentals',
     'Introduction to PCI DSS requirements and compliance obligations',
     '12.6.1', true, '["ALL"]', 45, 80, true),

    (gen_random_uuid(), 'PCI-12.6.1-002', 'Data Protection & Privacy',
     'Understanding cardholder data protection requirements',
     '12.6.1', true, '["DEVELOPER", "DBA", "DEVOPS"]', 60, 85, true),

    (gen_random_uuid(), 'PCI-12.6.1-003', 'Secure Coding Practices',
     'OWASP Top 10 and secure development lifecycle',
     '12.6.1', true, '["DEVELOPER"]', 90, 85, true),

    (gen_random_uuid(), 'PCI-12.6.1-004', 'Incident Response',
     'Security incident identification and response procedures',
     '12.6.1', true, '["SECURITY_ADMIN", "DEVOPS", "SOC_ANALYST"]', 30, 80, true),

    (gen_random_uuid(), 'PCI-12.6.1-005', 'Social Engineering Awareness',
     'Recognizing and reporting phishing, vishing, and social engineering attacks',
     '12.6.3.1', true, '["ALL"]', 30, 80, true),

    (gen_random_uuid(), 'PCI-12.6.1-006', 'Access Control & Authentication',
     'Strong authentication, password policies, and access management',
     '12.6.1', true, '["ALL"]', 45, 80, true),

    (gen_random_uuid(), 'PCI-12.6.1-007', 'Database Security',
     'Database hardening, encryption, and audit logging',
     '12.6.1', true, '["DBA", "DEVOPS"]', 60, 85, true),

    (gen_random_uuid(), 'PCI-12.6.1-008', 'Network Security',
     'Firewall configuration, segmentation, and monitoring',
     '12.6.1', true, '["NETWORK_ADMIN", "SECURITY_ADMIN"]', 60, 85, true);