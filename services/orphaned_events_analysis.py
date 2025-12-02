#!/usr/bin/env python3
"""
Orphaned Events Analysis and Prioritization Script
Analyzes the 1,491 orphaned events and creates prioritized implementation lists
"""

import json
from collections import defaultdict

def categorize_orphaned_events():
    """Load and categorize orphaned events by priority and type"""

    with open('kafka_audit_report.json', 'r') as f:
        data = json.load(f)

    orphaned = data['analysis']['orphaned_events']

    # Priority categories
    categories = {
        'critical_dlq': [],
        'compliance_regulatory': [],
        'system_monitoring': [],
        'notification_alerts': [],
        'financial_transactions': [],
        'security_fraud': [],
        'error_handling': [],
        'analytics_reporting': [],
        'other': []
    }

    # Categorization keywords
    dlq_keywords = ['dlq', '.DLQ', 'dead-letter', 'fallback', 'retry']
    compliance_keywords = ['compliance', 'aml', 'kyc', 'sar', 'sanctions', 'regulatory', 'legal', 'w9', 'form-8300', 'dora', 'escheatment']
    monitoring_keywords = ['monitoring', 'metrics', 'dashboard', 'system-monitoring', 'health', 'performance']
    notification_keywords = ['notification', 'alert', 'email', 'sms', 'push', 'escalation']
    financial_keywords = ['payment', 'transaction', 'transfer', 'balance', 'settlement', 'disbursement', 'refund']
    security_keywords = ['fraud', 'security', 'breach', 'compromise', 'freeze', 'block', 'suspend']
    error_keywords = ['error', 'failure', 'failed', 'exception', 'validation-errors']
    analytics_keywords = ['analytics', 'reporting', 'metrics-update', 'chargeback-analytics']

    for event in orphaned:
        topic = event['topic'].lower()

        if any(kw in topic for kw in dlq_keywords):
            categories['critical_dlq'].append(event)
        elif any(kw in topic for kw in compliance_keywords):
            categories['compliance_regulatory'].append(event)
        elif any(kw in topic for kw in monitoring_keywords):
            categories['system_monitoring'].append(event)
        elif any(kw in topic for kw in notification_keywords):
            categories['notification_alerts'].append(event)
        elif any(kw in topic for kw in financial_keywords):
            categories['financial_transactions'].append(event)
        elif any(kw in topic for kw in security_keywords):
            categories['security_fraud'].append(event)
        elif any(kw in topic for kw in error_keywords):
            categories['error_handling'].append(event)
        elif any(kw in topic for kw in analytics_keywords):
            categories['analytics_reporting'].append(event)
        else:
            categories['other'].append(event)

    return categories, orphaned

def analyze_producer_patterns(orphaned_events):
    """Analyze which services are producing the most orphaned events"""

    service_counts = defaultdict(int)
    service_topics = defaultdict(list)

    for event in orphaned_events:
        for producer in event.get('producers', []):
            file_path = producer.get('file', '')
            if './':
                service = file_path.split('/')[1] if len(file_path.split('/')) > 1 else 'unknown'
                service_counts[service] += 1
                service_topics[service].append(event['topic'])

    return dict(service_counts), dict(service_topics)

def generate_priority_report(categories, service_analysis):
    """Generate a prioritized implementation report"""

    report = []
    report.append("=" * 80)
    report.append("ORPHANED KAFKA EVENTS - PRIORITY ANALYSIS REPORT")
    report.append("=" * 80)
    report.append("")

    # Summary
    total_orphaned = sum(len(events) for events in categories.values())
    report.append(f"SUMMARY:")
    report.append(f"├── Total Orphaned Events: {total_orphaned}")
    report.append(f"├── Previously Implemented: 434 consumers")
    report.append(f"└── Remaining Work: {total_orphaned} consumers to implement")
    report.append("")

    # Priority breakdown
    priority_order = [
        ('critical_dlq', 'CRITICAL - Dead Letter Queue Topics', 'P0'),
        ('compliance_regulatory', 'HIGH - Compliance & Regulatory Topics', 'P1'),
        ('security_fraud', 'HIGH - Security & Fraud Detection', 'P1'),
        ('system_monitoring', 'MEDIUM - System Monitoring & Health', 'P2'),
        ('error_handling', 'MEDIUM - Error Handling & Validation', 'P2'),
        ('financial_transactions', 'MEDIUM - Financial Transactions', 'P2'),
        ('notification_alerts', 'LOW - Notifications & Alerts', 'P3'),
        ('analytics_reporting', 'LOW - Analytics & Reporting', 'P3'),
        ('other', 'LOW - Other Topics', 'P4')
    ]

    report.append("PRIORITY BREAKDOWN:")
    report.append("-------------------")
    for category, title, priority in priority_order:
        count = len(categories[category])
        report.append(f"{priority} {title}: {count} topics")
    report.append("")

    # Detailed breakdown by category
    for category, title, priority in priority_order:
        events = categories[category]
        if events:
            report.append(f"{priority} {title}")
            report.append("-" * (len(title) + 4))

            # Group by service
            service_events = defaultdict(list)
            for event in events:
                for producer in event.get('producers', []):
                    file_path = producer.get('file', '')
                    service = file_path.split('/')[1] if len(file_path.split('/')) > 1 else 'unknown'
                    service_events[service].append(event['topic'])

            for service, topics in sorted(service_events.items()):
                report.append(f"  {service}: {len(topics)} topics")
                for topic in sorted(set(topics))[:5]:  # Show first 5 unique topics
                    report.append(f"    - {topic}")
                if len(set(topics)) > 5:
                    report.append(f"    ... and {len(set(topics)) - 5} more")
                report.append("")
            report.append("")

    # Service analysis
    service_counts, service_topics = service_analysis
    report.append("TOP SERVICES PRODUCING ORPHANED EVENTS:")
    report.append("---------------------------------------")
    for service, count in sorted(service_counts.items(), key=lambda x: x[1], reverse=True)[:10]:
        report.append(f"{service}: {count} orphaned topics")
    report.append("")

    # Implementation recommendations
    report.append("IMPLEMENTATION RECOMMENDATIONS:")
    report.append("------------------------------")
    report.append("1. START WITH P0 (Critical DLQ Topics)")
    report.append("   - Essential for error handling and message recovery")
    report.append("   - Implement generic DLQ handlers first")
    report.append("")
    report.append("2. COMPLIANCE & REGULATORY (P1)")
    report.append("   - Required for regulatory compliance")
    report.append("   - Focus on AML, KYC, SAR filing topics")
    report.append("")
    report.append("3. SECURITY & FRAUD (P1)")
    report.append("   - Critical for platform security")
    report.append("   - Implement fraud detection and security alert consumers")
    report.append("")
    report.append("4. SYSTEM MONITORING (P2)")
    report.append("   - Important for operational visibility")
    report.append("   - Implement for key metrics and health monitoring")
    report.append("")
    report.append("5. BATCH PROCESSING FOR P3/P4")
    report.append("   - Use templated consumer generation")
    report.append("   - Focus on common patterns")
    report.append("")

    return "\n".join(report)

def main():
    print("🔍 Starting Orphaned Events Analysis...")

    # Load and categorize events
    categories, all_orphaned = categorize_orphaned_events()

    # Analyze producer patterns
    service_analysis = analyze_producer_patterns(all_orphaned)

    # Generate report
    report = generate_priority_report(categories, service_analysis)

    # Save report
    with open('orphaned_events_priority_report.txt', 'w') as f:
        f.write(report)

    # Save priority lists for implementation
    priority_lists = {
        'P0_critical_dlq': [e['topic'] for e in categories['critical_dlq']],
        'P1_compliance': [e['topic'] for e in categories['compliance_regulatory']],
        'P1_security': [e['topic'] for e in categories['security_fraud']],
        'P2_monitoring': [e['topic'] for e in categories['system_monitoring']],
        'P2_errors': [e['topic'] for e in categories['error_handling']],
        'P2_financial': [e['topic'] for e in categories['financial_transactions']],
        'P3_notifications': [e['topic'] for e in categories['notification_alerts']],
        'P3_analytics': [e['topic'] for e in categories['analytics_reporting']],
        'P4_other': [e['topic'] for e in categories['other']]
    }

    with open('priority_topics_for_implementation.json', 'w') as f:
        json.dump(priority_lists, f, indent=2)

    print("✅ Analysis complete!")
    print(f"📊 Total orphaned events analyzed: {len(all_orphaned)}")
    print("📋 Reports generated:")
    print("  - orphaned_events_priority_report.txt")
    print("  - priority_topics_for_implementation.json")

    # Print summary
    print("\n📈 PRIORITY SUMMARY:")
    priority_order = [
        ('critical_dlq', 'P0 - Critical DLQ'),
        ('compliance_regulatory', 'P1 - Compliance'),
        ('security_fraud', 'P1 - Security'),
        ('system_monitoring', 'P2 - Monitoring'),
        ('error_handling', 'P2 - Errors'),
        ('financial_transactions', 'P2 - Financial'),
        ('notification_alerts', 'P3 - Notifications'),
        ('analytics_reporting', 'P3 - Analytics'),
        ('other', 'P4 - Other')
    ]

    for category, title in priority_order:
        count = len(categories[category])
        print(f"  {title}: {count} topics")

if __name__ == "__main__":
    main()