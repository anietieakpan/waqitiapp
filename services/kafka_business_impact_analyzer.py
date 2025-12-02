#!/usr/bin/env python3
"""
Kafka Business Impact Analyzer
Analyzes orphaned events and provides business impact assessment
"""

import json
import re
from collections import defaultdict

# Business criticality mapping
BUSINESS_CRITICALITY = {
    'payment': 'CRITICAL',
    'transaction': 'CRITICAL', 
    'fraud': 'CRITICAL',
    'compliance': 'CRITICAL',
    'kyc': 'CRITICAL',
    'aml': 'CRITICAL',
    'wallet': 'HIGH',
    'account': 'HIGH',
    'notification': 'HIGH',
    'rewards': 'MEDIUM',
    'analytics': 'LOW',
    'monitoring': 'MEDIUM',
    'audit': 'HIGH',
    'security': 'CRITICAL',
    'risk': 'CRITICAL'
}

def analyze_orphaned_event(topic, producers):
    """Analyze a single orphaned event for business impact"""
    
    # Determine criticality based on topic name
    criticality = 'LOW'
    for keyword, level in BUSINESS_CRITICALITY.items():
        if keyword in topic.lower():
            if level == 'CRITICAL' or (level == 'HIGH' and criticality != 'CRITICAL'):
                criticality = level
            elif level == 'MEDIUM' and criticality not in ['CRITICAL', 'HIGH']:
                criticality = level
    
    # Determine potential business impact
    impact_analysis = analyze_business_impact(topic)
    
    # Determine data loss risk
    data_loss_risk = assess_data_loss_risk(topic)
    
    # Estimate fix complexity
    fix_complexity = estimate_fix_complexity(topic, producers)
    
    # Determine expected consumer service
    expected_consumer = determine_expected_consumer(topic)
    
    return {
        'topic': topic,
        'criticality': criticality,
        'business_impact': impact_analysis,
        'data_loss_risk': data_loss_risk,
        'fix_complexity': fix_complexity,
        'expected_consumer_service': expected_consumer,
        'producers': producers,
        'broken_features': identify_broken_features(topic)
    }

def analyze_business_impact(topic):
    """Analyze what business functionality is broken"""
    topic_lower = topic.lower()
    
    if 'payment' in topic_lower:
        if 'completed' in topic_lower:
            return "Payment completion workflow broken - downstream services not notified of successful payments"
        elif 'failed' in topic_lower:
            return "Payment failure handling broken - failed payments not processed for retries/notifications"
        elif 'refund' in topic_lower:
            return "Refund processing workflow broken - refund events not triggering dependent actions"
        else:
            return "Payment event processing broken - payment lifecycle events lost"
    
    elif 'fraud' in topic_lower:
        return "Fraud detection alerts not being processed - potential financial losses and compliance violations"
    
    elif 'compliance' in topic_lower or 'aml' in topic_lower or 'kyc' in topic_lower:
        return "Compliance workflow broken - regulatory reporting and monitoring compromised"
    
    elif 'notification' in topic_lower:
        return "User notifications not sent - poor customer experience and missing critical alerts"
    
    elif 'wallet' in topic_lower:
        return "Wallet state inconsistencies - balance updates and transaction history may be incomplete"
    
    elif 'account' in topic_lower:
        return "Account lifecycle management broken - status changes and account operations not propagated"
    
    elif 'rewards' in topic_lower or 'loyalty' in topic_lower:
        return "Rewards/loyalty program broken - points/cashback not calculated or awarded"
    
    elif 'risk' in topic_lower:
        return "Risk assessment pipeline broken - risk scores and alerts not updated"
    
    elif 'audit' in topic_lower:
        return "Audit trail incomplete - compliance and forensic capabilities compromised"
    
    elif 'security' in topic_lower:
        return "Security incident response broken - security events not processed for threat detection"
    
    elif 'monitoring' in topic_lower or 'alert' in topic_lower:
        return "System monitoring broken - alerts and metrics not processed for operational visibility"
    
    else:
        return "Unknown business impact - event processing workflow interrupted"

def assess_data_loss_risk(topic):
    """Assess the risk of data loss"""
    topic_lower = topic.lower()
    
    critical_patterns = ['payment', 'transaction', 'fraud', 'compliance', 'audit', 'kyc', 'aml']
    high_patterns = ['wallet', 'account', 'security', 'risk']
    medium_patterns = ['notification', 'rewards', 'analytics']
    
    for pattern in critical_patterns:
        if pattern in topic_lower:
            return "HIGH - Financial/compliance data loss possible"
    
    for pattern in high_patterns:
        if pattern in topic_lower:
            return "MEDIUM - Important business data may be lost"
    
    for pattern in medium_patterns:
        if pattern in topic_lower:
            return "LOW - Non-critical data loss"
    
    return "LOW - Impact unclear"

def estimate_fix_complexity(topic, producers):
    """Estimate the complexity of fixing the orphaned event (1-5 scale)"""
    
    # Base complexity on number of producers
    num_producers = len(producers)
    if num_producers == 1:
        base_complexity = 2
    elif num_producers <= 3:
        base_complexity = 3
    elif num_producers <= 5:
        base_complexity = 4
    else:
        base_complexity = 5
    
    # Adjust based on topic complexity
    topic_lower = topic.lower()
    if any(word in topic_lower for word in ['dlq', 'retry', 'error', 'validation']):
        base_complexity += 1
    
    if any(word in topic_lower for word in ['compliance', 'aml', 'fraud', 'kyc']):
        base_complexity += 1
    
    return min(5, max(1, base_complexity))

def determine_expected_consumer(topic):
    """Determine which service should likely consume this event"""
    topic_lower = topic.lower()
    
    if 'payment' in topic_lower:
        return "payment-service, ledger-service, notification-service"
    elif 'fraud' in topic_lower:
        return "fraud-service, security-service, notification-service"
    elif 'compliance' in topic_lower or 'aml' in topic_lower:
        return "compliance-service, audit-service"
    elif 'notification' in topic_lower:
        return "notification-service"
    elif 'wallet' in topic_lower:
        return "wallet-service, ledger-service"
    elif 'account' in topic_lower:
        return "account-service, user-service"
    elif 'rewards' in topic_lower or 'loyalty' in topic_lower:
        return "rewards-service"
    elif 'kyc' in topic_lower:
        return "kyc-service, compliance-service, user-service"
    elif 'risk' in topic_lower:
        return "risk-service, fraud-service"
    elif 'audit' in topic_lower:
        return "audit-service"
    elif 'security' in topic_lower:
        return "security-service"
    elif 'monitoring' in topic_lower:
        return "monitoring-service"
    else:
        return "Unknown - needs investigation"

def identify_broken_features(topic):
    """Identify specific features that are broken"""
    topic_lower = topic.lower()
    broken_features = []
    
    if 'payment-completed' in topic_lower:
        broken_features.extend([
            "Rewards/cashback calculation",
            "Transaction confirmations", 
            "Receipt generation",
            "Merchant settlement",
            "Ledger updates"
        ])
    elif 'fraud' in topic_lower:
        broken_features.extend([
            "Real-time fraud detection",
            "Risk scoring updates",
            "Account blocking/suspension",
            "Fraud alerts to users"
        ])
    elif 'notification' in topic_lower:
        broken_features.extend([
            "SMS/Email notifications",
            "Push notifications",
            "In-app notifications",
            "Alert delivery"
        ])
    elif 'wallet' in topic_lower:
        broken_features.extend([
            "Balance updates",
            "Transaction history",
            "Wallet state synchronization"
        ])
    elif 'compliance' in topic_lower:
        broken_features.extend([
            "Regulatory reporting",
            "AML monitoring",
            "Sanctions screening",
            "Audit trail maintenance"
        ])
    
    return broken_features

def generate_prioritized_fix_list(orphaned_analysis):
    """Generate a prioritized list of fixes based on business impact"""
    
    # Priority scoring
    priority_scores = []
    for analysis in orphaned_analysis:
        score = 0
        
        # Criticality weight (40%)
        if analysis['criticality'] == 'CRITICAL':
            score += 40
        elif analysis['criticality'] == 'HIGH':
            score += 30
        elif analysis['criticality'] == 'MEDIUM':
            score += 20
        else:
            score += 10
        
        # Data loss risk weight (30%)
        if 'HIGH' in analysis['data_loss_risk']:
            score += 30
        elif 'MEDIUM' in analysis['data_loss_risk']:
            score += 20
        else:
            score += 10
        
        # Fix complexity weight (20% - inverse)
        score += (6 - analysis['fix_complexity']) * 4
        
        # Number of producers weight (10%)
        score += min(10, len(analysis['producers']) * 2)
        
        priority_scores.append((score, analysis))
    
    # Sort by priority score (highest first)
    priority_scores.sort(key=lambda x: x[0], reverse=True)
    
    return [analysis for score, analysis in priority_scores]

def main():
    """Main analysis function"""
    
    # Load the audit report
    with open('/Users/anietieakpan/git/waqiti-app/services/kafka_audit_report.json', 'r') as f:
        audit_data = json.load(f)
    
    print("🔍 KAFKA BUSINESS IMPACT ANALYSIS")
    print("=" * 60)
    
    # Analyze orphaned events
    orphaned_events = audit_data['analysis']['orphaned_events']
    orphaned_analysis = []
    
    for orphan in orphaned_events:
        analysis = analyze_orphaned_event(orphan['topic'], orphan['producers'])
        orphaned_analysis.append(analysis)
    
    # Generate prioritized fix list
    prioritized_fixes = generate_prioritized_fix_list(orphaned_analysis)
    
    # Summary statistics
    critical_count = sum(1 for a in orphaned_analysis if a['criticality'] == 'CRITICAL')
    high_count = sum(1 for a in orphaned_analysis if a['criticality'] == 'HIGH')
    
    print(f"📊 SUMMARY STATISTICS:")
    print(f"Total Orphaned Events: {len(orphaned_analysis)}")
    print(f"Critical Business Impact: {critical_count}")
    print(f"High Business Impact: {high_count}")
    print(f"Data Loss Risk (HIGH): {sum(1 for a in orphaned_analysis if 'HIGH' in a['data_loss_risk'])}")
    
    print(f"\n🚨 TOP 20 CRITICAL ORPHANED EVENTS TO FIX:")
    print("=" * 60)
    
    for i, analysis in enumerate(prioritized_fixes[:20], 1):
        print(f"\n{i}. TOPIC: {analysis['topic']}")
        print(f"   Criticality: {analysis['criticality']}")
        print(f"   Business Impact: {analysis['business_impact']}")
        print(f"   Data Loss Risk: {analysis['data_loss_risk']}")
        print(f"   Fix Complexity: {analysis['fix_complexity']}/5")
        print(f"   Expected Consumer: {analysis['expected_consumer_service']}")
        print(f"   Broken Features: {', '.join(analysis['broken_features']) if analysis['broken_features'] else 'Unknown'}")
        
        print(f"   Producers ({len(analysis['producers'])}):")
        for producer in analysis['producers'][:3]:  # Show first 3
            service_name = producer['file'].split('/')[-4] if len(producer['file'].split('/')) > 4 else 'unknown'
            print(f"     - {service_name}: {producer['file']}:{producer['line']}")
        if len(analysis['producers']) > 3:
            print(f"     ... and {len(analysis['producers']) - 3} more")
    
    # Save detailed analysis
    detailed_report = {
        'analysis_timestamp': '2025-09-27',
        'summary': {
            'total_orphaned': len(orphaned_analysis),
            'critical_impact': critical_count,
            'high_impact': high_count,
            'high_data_loss_risk': sum(1 for a in orphaned_analysis if 'HIGH' in a['data_loss_risk'])
        },
        'prioritized_fixes': prioritized_fixes,
        'detailed_analysis': orphaned_analysis
    }
    
    with open('/Users/anietieakpan/git/waqiti-app/services/kafka_business_impact_report.json', 'w') as f:
        json.dump(detailed_report, f, indent=2, default=str)
    
    print(f"\n✅ Detailed business impact analysis saved to: kafka_business_impact_report.json")
    
    # Generate fix recommendations
    print(f"\n💡 IMMEDIATE FIX RECOMMENDATIONS:")
    print("=" * 40)
    print("1. Fix all CRITICAL payment-related events first")
    print("2. Address fraud detection and compliance events")
    print("3. Implement missing notification consumers")
    print("4. Review and fix wallet/account event flows")
    print("5. Set up monitoring for event processing gaps")

if __name__ == "__main__":
    main()