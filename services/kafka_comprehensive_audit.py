#!/usr/bin/env python3
"""
Comprehensive Kafka Event-Driven Messaging Architecture Audit
Exhaustive analysis of producers, consumers, orphaned messages, and data integrity gaps
"""

import os
import re
import json
from collections import defaultdict
from pathlib import Path

class KafkaArchitectureAuditor:
    def __init__(self, base_path):
        self.base_path = Path(base_path)
        self.topics = set()
        self.producers = defaultdict(list)  # topic -> [(service, file, line, method)]
        self.consumers = defaultdict(list)  # topic -> [(service, file, line, method)]
        self.dlq_consumers = defaultdict(list)
        self.transactional_consumers = set()
        self.idempotent_consumers = set()

    def find_all_topics(self):
        """Extract all topic definitions from KafkaTopics.java and code"""
        print("[1/7] Extracting ALL topic definitions...")

        # Read KafkaTopics.java
        kafka_topics_file = self.base_path / "common/src/main/java/com/waqiti/common/kafka/KafkaTopics.java"
        if kafka_topics_file.exists():
            with open(kafka_topics_file, 'r') as f:
                content = f.read()
                # Extract topic constants
                pattern = r'public static final String\s+\w+\s*=\s*"([^"]+)"'
                for match in re.finditer(pattern, content):
                    self.topics.add(match.group(1))

        # Search for hardcoded topic strings in all Java files
        for java_file in self.base_path.rglob("*.java"):
            if 'test' in str(java_file).lower():
                continue
            try:
                with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
                    # Find topic strings in kafkaTemplate.send
                    send_pattern = r'kafkaTemplate\.send\s*\(\s*"([^"]+)"'
                    for match in re.finditer(send_pattern, content):
                        self.topics.add(match.group(1))

                    # Find topics in @KafkaListener
                    listener_pattern = r'@KafkaListener.*?topics\s*=\s*[{"]([^}"]+)[}"]'
                    for match in re.finditer(listener_pattern, content, re.DOTALL):
                        topic = match.group(1).strip().replace('"', '')
                        if not topic.startswith('${'):
                            self.topics.add(topic)
            except Exception as e:
                continue

        print(f"    Found {len(self.topics)} unique topics")
        return self.topics

    def find_all_producers(self):
        """Find all Kafka producers with location details"""
        print("[2/7] Mapping ALL Kafka producers...")

        producer_count = 0
        for java_file in self.base_path.rglob("*.java"):
            if 'test' in str(java_file).lower():
                continue

            try:
                with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
                    lines = f.readlines()
                    service_name = self._extract_service_name(java_file)

                    for i, line in enumerate(lines, 1):
                        # Match kafkaTemplate.send patterns
                        if 'kafkaTemplate.send(' in line or 'kafkaTemplate.send (' in line:
                            topic = self._extract_topic_from_send(line, lines, i)
                            if topic:
                                method = self._extract_method_context(lines, i)
                                self.producers[topic].append({
                                    'service': service_name,
                                    'file': str(java_file.relative_to(self.base_path)),
                                    'line': i,
                                    'method': method
                                })
                                producer_count += 1
            except Exception as e:
                continue

        print(f"    Found {producer_count} producer calls across {len(self.producers)} topics")
        return self.producers

    def find_all_consumers(self):
        """Find all Kafka consumers with @KafkaListener annotation"""
        print("[3/7] Mapping ALL Kafka consumers...")

        consumer_count = 0
        for java_file in self.base_path.rglob("*.java"):
            if 'test' in str(java_file).lower():
                continue

            try:
                with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
                    lines = content.split('\n')
                    service_name = self._extract_service_name(java_file)

                    # Find @KafkaListener annotations
                    for match in re.finditer(r'@KafkaListener\s*\([^)]+\)', content, re.DOTALL):
                        annotation = match.group(0)
                        topics = self._extract_topics_from_listener(annotation)
                        line_num = content[:match.start()].count('\n') + 1
                        method = self._extract_method_context(lines, line_num)

                        # Check for @Transactional
                        is_transactional = self._is_transactional(lines, line_num)
                        has_idempotency = self._has_idempotency_check(lines, line_num, method)

                        for topic in topics:
                            consumer_info = {
                                'service': service_name,
                                'file': str(java_file.relative_to(self.base_path)),
                                'line': line_num,
                                'method': method,
                                'transactional': is_transactional,
                                'idempotent': has_idempotency
                            }

                            if '.dlq' in topic.lower() or 'dead-letter' in topic.lower():
                                self.dlq_consumers[topic].append(consumer_info)
                            else:
                                self.consumers[topic].append(consumer_info)

                            if is_transactional:
                                self.transactional_consumers.add(f"{service_name}:{method}")
                            if has_idempotency:
                                self.idempotent_consumers.add(f"{service_name}:{method}")

                            consumer_count += 1
            except Exception as e:
                continue

        print(f"    Found {consumer_count} consumer listeners across {len(self.consumers)} topics")
        print(f"    Found {len(self.dlq_consumers)} DLQ consumers")
        return self.consumers

    def analyze_orphaned_events(self):
        """Identify topics with producers but no consumers"""
        print("[4/7] Analyzing orphaned events...")

        orphaned = []
        for topic in self.producers.keys():
            if topic not in self.consumers:
                # Check if it might be consumed via pattern or property
                is_possibly_consumed = any(
                    '${' in c_topic or '*' in c_topic
                    for c_topic in self.consumers.keys()
                )

                business_impact = self._assess_business_impact(topic)
                data_loss_risk = self._assess_data_loss_risk(topic, self.producers[topic])

                orphaned.append({
                    'topic': topic,
                    'producers': self.producers[topic],
                    'producer_count': len(self.producers[topic]),
                    'business_impact': business_impact,
                    'data_loss_risk': data_loss_risk,
                    'possibly_consumed_via_pattern': is_possibly_consumed
                })

        # Sort by business impact
        orphaned.sort(key=lambda x: self._impact_priority(x['business_impact']), reverse=True)

        print(f"    Found {len(orphaned)} orphaned topics (producers without consumers)")
        return orphaned

    def analyze_dlq_coverage(self):
        """Analyze DLQ setup and identify topics missing DLQ handling"""
        print("[5/7] Analyzing DLQ coverage...")

        topics_needing_dlq = []
        topics_with_dlq = set()
        topics_missing_dlq_consumer = []

        # Find topics that have DLQ
        for topic in self.topics:
            if '.dlq' in topic.lower() or 'dead-letter' in topic.lower():
                base_topic = topic.replace('.dlq', '').replace('-dlq', '').replace('.dlt', '').replace('-dlt', '')
                topics_with_dlq.add(base_topic)

        # Check critical topics for DLQ coverage
        critical_keywords = ['payment', 'wallet', 'transaction', 'fraud', 'compliance', 'transfer']
        for topic in self.consumers.keys():
            is_critical = any(keyword in topic.lower() for keyword in critical_keywords)
            has_dlq = topic in topics_with_dlq

            if is_critical and not has_dlq:
                topics_needing_dlq.append({
                    'topic': topic,
                    'consumers': len(self.consumers[topic]),
                    'reason': 'Critical topic missing DLQ'
                })

        # Check if DLQ topics have consumers
        for dlq_topic in self.dlq_consumers.keys():
            if len(self.dlq_consumers[dlq_topic]) == 0:
                topics_missing_dlq_consumer.append(dlq_topic)

        print(f"    Topics with DLQ: {len(topics_with_dlq)}")
        print(f"    Critical topics needing DLQ: {len(topics_needing_dlq)}")
        print(f"    DLQ topics without consumers: {len(topics_missing_dlq_consumer)}")

        return {
            'topics_with_dlq': list(topics_with_dlq),
            'topics_needing_dlq': topics_needing_dlq,
            'dlq_without_consumers': topics_missing_dlq_consumer
        }

    def analyze_transaction_idempotency(self):
        """Analyze transaction boundaries and idempotency"""
        print("[6/7] Analyzing transaction boundaries and idempotency...")

        consumers_without_transaction = []
        consumers_without_idempotency = []

        critical_keywords = ['payment', 'wallet', 'transaction', 'transfer', 'withdrawal', 'deposit']

        for topic, consumer_list in self.consumers.items():
            is_critical = any(keyword in topic.lower() for keyword in critical_keywords)

            for consumer in consumer_list:
                if is_critical:
                    if not consumer.get('transactional'):
                        consumers_without_transaction.append({
                            'topic': topic,
                            'consumer': f"{consumer['service']}:{consumer['method']}",
                            'file': consumer['file'],
                            'line': consumer['line']
                        })

                    if not consumer.get('idempotent'):
                        consumers_without_idempotency.append({
                            'topic': topic,
                            'consumer': f"{consumer['service']}:{consumer['method']}",
                            'file': consumer['file'],
                            'line': consumer['line']
                        })

        print(f"    Transactional consumers: {len(self.transactional_consumers)}")
        print(f"    Idempotent consumers: {len(self.idempotent_consumers)}")
        print(f"    Critical consumers without @Transactional: {len(consumers_without_transaction)}")
        print(f"    Critical consumers without idempotency: {len(consumers_without_idempotency)}")

        return {
            'transactional_count': len(self.transactional_consumers),
            'idempotent_count': len(self.idempotent_consumers),
            'missing_transaction': consumers_without_transaction,
            'missing_idempotency': consumers_without_idempotency
        }

    def generate_report(self):
        """Generate comprehensive audit report"""
        print("[7/7] Generating comprehensive report...")

        orphaned = self.analyze_orphaned_events()
        dlq_analysis = self.analyze_dlq_coverage()
        transaction_analysis = self.analyze_transaction_idempotency()

        # Calculate statistics
        total_topics = len(self.topics)
        topics_with_producers = len(self.producers)
        topics_with_consumers = len(self.consumers)
        topics_with_both = len(set(self.producers.keys()) & set(self.consumers.keys()))

        report = {
            'summary': {
                'total_topics_discovered': total_topics,
                'topics_with_producers': topics_with_producers,
                'topics_with_consumers': topics_with_consumers,
                'topics_with_both_producer_and_consumer': topics_with_both,
                'orphaned_topics': len(orphaned),
                'dlq_topics': len(self.dlq_consumers),
                'transactional_consumers': len(self.transactional_consumers),
                'idempotent_consumers': len(self.idempotent_consumers)
            },
            'orphaned_events': orphaned[:50],  # Top 50 orphaned
            'dlq_analysis': dlq_analysis,
            'transaction_idempotency_analysis': transaction_analysis,
            'production_blocking_issues': self._identify_production_blockers(orphaned, dlq_analysis, transaction_analysis)
        }

        return report

    # Helper methods

    def _extract_service_name(self, file_path):
        """Extract service name from file path"""
        parts = str(file_path).split('services/')
        if len(parts) > 1:
            service = parts[1].split('/')[0]
            return service
        return 'unknown'

    def _extract_topic_from_send(self, line, all_lines, line_num):
        """Extract topic from kafkaTemplate.send line"""
        # Pattern 1: Direct string
        match = re.search(r'send\s*\(\s*"([^"]+)"', line)
        if match:
            return match.group(1)

        # Pattern 2: Constant
        match = re.search(r'send\s*\(\s*([A-Z_]+)', line)
        if match:
            const_name = match.group(1)
            # Try to find constant definition
            for i in range(max(0, line_num - 100), min(len(all_lines), line_num + 10)):
                const_match = re.search(rf'{const_name}\s*=\s*"([^"]+)"', all_lines[i])
                if const_match:
                    return const_match.group(1)

        return None

    def _extract_topics_from_listener(self, annotation):
        """Extract topics from @KafkaListener annotation"""
        topics = []

        # Pattern 1: topics = "topic-name"
        match = re.search(r'topics\s*=\s*"([^"]+)"', annotation)
        if match:
            topics.append(match.group(1))

        # Pattern 2: topics = {"topic1", "topic2"}
        match = re.search(r'topics\s*=\s*\{([^}]+)\}', annotation)
        if match:
            topic_str = match.group(1)
            for topic in re.findall(r'"([^"]+)"', topic_str):
                topics.append(topic)

        # Pattern 3: topics = ${property}
        match = re.search(r'topics\s*=\s*"\$\{([^}]+)\}"', annotation)
        if match:
            prop = match.group(1)
            # Extract default value if present
            if ':' in prop:
                default = prop.split(':')[1]
                topics.append(default)

        return topics

    def _extract_method_context(self, lines, line_num):
        """Extract method name from context"""
        for i in range(max(0, line_num - 10), line_num):
            if i < len(lines):
                match = re.search(r'(public|private|protected)\s+\w+\s+(\w+)\s*\(', lines[i])
                if match:
                    return match.group(2)
        return 'unknown'

    def _is_transactional(self, lines, line_num):
        """Check if method has @Transactional annotation"""
        for i in range(max(0, line_num - 5), line_num):
            if i < len(lines) and '@Transactional' in lines[i]:
                return True
        return False

    def _has_idempotency_check(self, lines, line_num, method_name):
        """Check if method has idempotency checking logic"""
        # Look for idempotency keywords in next 20 lines
        for i in range(line_num, min(len(lines), line_num + 20)):
            if i < len(lines):
                line_lower = lines[i].lower()
                if any(keyword in line_lower for keyword in ['idempotency', 'duplicate', 'already processed', 'eventid']):
                    return True
        return False

    def _assess_business_impact(self, topic):
        """Assess business impact of topic"""
        critical_keywords = {
            'payment': 'CRITICAL',
            'transaction': 'CRITICAL',
            'wallet': 'CRITICAL',
            'fraud': 'CRITICAL',
            'compliance': 'CRITICAL',
            'kyc': 'HIGH',
            'aml': 'HIGH',
            'transfer': 'HIGH',
            'withdrawal': 'HIGH',
            'deposit': 'HIGH',
            'refund': 'HIGH'
        }

        topic_lower = topic.lower()
        for keyword, impact in critical_keywords.items():
            if keyword in topic_lower:
                return impact

        return 'MEDIUM'

    def _assess_data_loss_risk(self, topic, producers):
        """Assess data loss risk"""
        if any(keyword in topic.lower() for keyword in ['payment', 'transaction', 'wallet', 'transfer']):
            return 'YES - Financial data may be lost'
        return 'MEDIUM - Non-financial data may be lost'

    def _impact_priority(self, impact):
        """Convert impact to numeric priority"""
        return {'CRITICAL': 3, 'HIGH': 2, 'MEDIUM': 1, 'LOW': 0}.get(impact, 0)

    def _identify_production_blockers(self, orphaned, dlq_analysis, transaction_analysis):
        """Identify top production-blocking issues"""
        blockers = []

        # Top orphaned events
        for event in orphaned[:10]:
            if event['business_impact'] in ['CRITICAL', 'HIGH']:
                blockers.append({
                    'severity': event['business_impact'],
                    'category': 'ORPHANED_EVENT',
                    'topic': event['topic'],
                    'issue': f"Topic has {event['producer_count']} producer(s) but NO consumer",
                    'data_loss_risk': event['data_loss_risk'],
                    'producers': [f"{p['service']}:{p['file']}:{p['line']}" for p in event['producers'][:3]]
                })

        # Critical topics without DLQ
        for topic_info in dlq_analysis['topics_needing_dlq'][:10]:
            blockers.append({
                'severity': 'HIGH',
                'category': 'MISSING_DLQ',
                'topic': topic_info['topic'],
                'issue': 'Critical topic missing Dead Letter Queue',
                'data_loss_risk': 'YES - Failed messages will be lost',
                'consumers': topic_info['consumers']
            })

        # Critical consumers without transaction
        for consumer in transaction_analysis['missing_transaction'][:5]:
            blockers.append({
                'severity': 'CRITICAL',
                'category': 'MISSING_TRANSACTION',
                'topic': consumer['topic'],
                'consumer': consumer['consumer'],
                'issue': 'Financial operation without @Transactional boundary',
                'data_loss_risk': 'YES - Data inconsistency possible',
                'location': f"{consumer['file']}:{consumer['line']}"
            })

        return sorted(blockers, key=lambda x: self._impact_priority(x['severity']), reverse=True)[:15]

def main():
    base_path = "/Users/anietieakpan/git/waqiti-app/services"

    print("=" * 80)
    print("KAFKA EVENT-DRIVEN MESSAGING ARCHITECTURE COMPREHENSIVE AUDIT")
    print("=" * 80)
    print()

    auditor = KafkaArchitectureAuditor(base_path)

    # Run comprehensive analysis
    auditor.find_all_topics()
    auditor.find_all_producers()
    auditor.find_all_consumers()

    # Generate report
    report = auditor.generate_report()

    # Save to file
    output_file = f"{base_path}/KAFKA_COMPREHENSIVE_AUDIT_REPORT.json"
    with open(output_file, 'w') as f:
        json.dump(report, f, indent=2)

    print()
    print("=" * 80)
    print("AUDIT COMPLETE")
    print("=" * 80)
    print(f"Report saved to: {output_file}")
    print()
    print(f"Total Topics: {report['summary']['total_topics_discovered']}")
    print(f"Orphaned Topics: {report['summary']['orphaned_topics']}")
    print(f"Production Blockers: {len(report['production_blocking_issues'])}")
    print()

    return report

if __name__ == "__main__":
    main()
