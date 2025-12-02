#!/usr/bin/env python3
"""
Kafka Event-Driven Architecture Audit Script
Extracts all Kafka topics, producers, and consumers across all services
"""

import os
import re
import json
from pathlib import Path
from collections import defaultdict

def extract_kafka_listeners(file_path):
    """Extract @KafkaListener annotations with topic, group, and method info"""
    listeners = []
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # Multi-line @KafkaListener pattern
        pattern = r'@KafkaListener\s*\(\s*(?:[^)]*topics\s*=\s*(?:\{([^}]+)\}|"([^"]+)")[^)]*groupId\s*=\s*"([^"]+)"[^)]*)?\)\s*[^{]*?\s*(?:public|private|protected)?\s*(?:\w+\s+)*(\w+)\s*\('
        matches = re.finditer(pattern, content, re.MULTILINE | re.DOTALL)
        
        for match in matches:
            topics_group = match.group(1) or match.group(2)
            if topics_group:
                if '{' in topics_group or topics_group.startswith('"'):
                    # Handle array format: {"topic1", "topic2"}
                    topics = re.findall(r'"([^"]+)"', topics_group)
                else:
                    # Single topic
                    topics = [topics_group]
                
                group_id = match.group(3) or "unknown"
                method_name = match.group(4) or "unknown"
                
                for topic in topics:
                    listeners.append({
                        'topic': topic,
                        'groupId': group_id,
                        'method': method_name,
                        'file': file_path
                    })
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
    
    return listeners

def extract_kafka_producers(file_path):
    """Extract KafkaTemplate.send() calls"""
    producers = []
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # Pattern for kafkaTemplate.send(topic, ...) calls
        pattern = r'(?:kafkaTemplate|kafkaTemplateProducer|template|eventPublisher)\.send\s*\(\s*"([^"]+)"'
        matches = re.finditer(pattern, content, re.MULTILINE)
        
        for match in matches:
            topic = match.group(1)
            producers.append({
                'topic': topic,
                'file': file_path,
                'line': content[:match.start()].count('\n') + 1
            })
            
        # Also look for method calls that publish to topics
        pattern2 = r'\.publishEvent\([^,]+,\s*"([^"]+)"'
        matches2 = re.finditer(pattern2, content, re.MULTILINE)
        
        for match in matches2:
            topic = match.group(1)
            producers.append({
                'topic': topic,
                'file': file_path,
                'line': content[:match.start()].count('\n') + 1
            })
            
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
    
    return producers

def scan_services_directory(services_dir):
    """Scan all services for Kafka listeners and producers"""
    consumers = []
    producers = []
    
    for root, dirs, files in os.walk(services_dir):
        for file in files:
            if file.endswith('.java'):
                file_path = os.path.join(root, file)
                
                # Extract consumers
                file_consumers = extract_kafka_listeners(file_path)
                consumers.extend(file_consumers)
                
                # Extract producers
                file_producers = extract_kafka_producers(file_path)
                producers.extend(file_producers)
    
    return consumers, producers

def analyze_topics(consumers, producers):
    """Analyze topic mappings and identify orphans"""
    # Group by topic
    topics_consumed = defaultdict(list)
    topics_produced = defaultdict(list)
    
    for consumer in consumers:
        topics_consumed[consumer['topic']].append(consumer)
    
    for producer in producers:
        topics_produced[producer['topic']].append(producer)
    
    all_topics = set(topics_consumed.keys()) | set(topics_produced.keys())
    
    # Find orphaned events (produced but not consumed)
    orphaned_events = []
    for topic in topics_produced.keys():
        if topic not in topics_consumed:
            orphaned_events.append({
                'topic': topic,
                'producers': topics_produced[topic],
                'reason': 'No consumers found'
            })
    
    # Find missing producers (consumed but not produced)
    missing_producers = []
    for topic in topics_consumed.keys():
        if topic not in topics_produced:
            missing_producers.append({
                'topic': topic,
                'consumers': topics_consumed[topic],
                'reason': 'No producers found'
            })
    
    return {
        'all_topics': sorted(list(all_topics)),
        'topics_consumed': dict(topics_consumed),
        'topics_produced': dict(topics_produced),
        'orphaned_events': orphaned_events,
        'missing_producers': missing_producers,
        'stats': {
            'total_topics': len(all_topics),
            'consumed_topics': len(topics_consumed),
            'produced_topics': len(topics_produced),
            'orphaned_count': len(orphaned_events),
            'missing_producer_count': len(missing_producers)
        }
    }

def main():
    services_dir = "."
    
    print("🔍 Starting Kafka Event-Driven Architecture Audit...")
    print(f"Scanning directory: {services_dir}")
    
    # Scan all services
    consumers, producers = scan_services_directory(services_dir)
    
    print(f"📊 Found {len(consumers)} Kafka consumers")
    print(f"📊 Found {len(producers)} Kafka producers")
    
    # Analyze topics
    analysis = analyze_topics(consumers, producers)
    
    # Generate report
    report = {
        'audit_summary': {
            'timestamp': '2025-09-27',
            'total_consumers': len(consumers),
            'total_producers': len(producers),
            'total_topics': analysis['stats']['total_topics'],
            'orphaned_events': analysis['stats']['orphaned_count'],
            'missing_producers': analysis['stats']['missing_producer_count']
        },
        'all_consumers': consumers,
        'all_producers': producers,
        'analysis': analysis
    }
    
    # Save detailed report
    with open('kafka_audit_report.json', 'w') as f:
        json.dump(report, f, indent=2, default=str)
    
    print("\n📋 AUDIT SUMMARY")
    print("=" * 50)
    print(f"Total Topics: {analysis['stats']['total_topics']}")
    print(f"Topics with Consumers: {analysis['stats']['consumed_topics']}")
    print(f"Topics with Producers: {analysis['stats']['produced_topics']}")
    print(f"🚨 Orphaned Events: {analysis['stats']['orphaned_count']}")
    print(f"🚨 Missing Producers: {analysis['stats']['missing_producer_count']}")
    
    if analysis['orphaned_events']:
        print("\n🚨 ORPHANED EVENTS (Produced but No Consumers):")
        for i, orphan in enumerate(analysis['orphaned_events'], 1):
            print(f"{i}. Topic: {orphan['topic']}")
            for producer in orphan['producers']:
                service = producer['file'].split('/')[-4] if len(producer['file'].split('/')) > 4 else "unknown"
                print(f"   Producer: {service} ({producer['file']}:{producer['line']})")
    
    if analysis['missing_producers']:
        print("\n🚨 MISSING PRODUCERS (Consumed but No Producers Found):")
        for i, missing in enumerate(analysis['missing_producers'], 1):
            print(f"{i}. Topic: {missing['topic']}")
            for consumer in missing['consumers']:
                service = consumer['file'].split('/')[-4] if len(consumer['file'].split('/')) > 4 else "unknown"
                print(f"   Consumer: {service} - {consumer['method']} (group: {consumer['groupId']})")
    
    print(f"\n✅ Detailed report saved to: kafka_audit_report.json")

if __name__ == "__main__":
    main()