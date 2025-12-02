#!/usr/bin/env python3

import re
import os
import subprocess

def extract_topics_from_file(file_path):
    """Extract Kafka topics from a Java file."""
    topics = []
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
            
            # Look for @KafkaListener annotations with topics
            kafka_listener_pattern = r'@KafkaListener\s*\(\s*topics\s*=\s*\{([^}]+)\}'
            matches = re.findall(kafka_listener_pattern, content, re.DOTALL)
            
            for match in matches:
                # Extract individual topics from the array
                topic_pattern = r'"([^"]+)"'
                file_topics = re.findall(topic_pattern, match)
                topics.extend(file_topics)
                
    except Exception as e:
        print(f"Error processing {file_path}: {e}")
    
    return topics

def find_all_kafka_listeners():
    """Find all @KafkaListener files and extract topics."""
    # Get all files with @KafkaListener
    result = subprocess.run([
        'grep', '-r', '-l', '@KafkaListener', 
        '/Users/anietieakpan/git/waqiti-app/services',
        '--include=*.java'
    ], capture_output=True, text=True)
    
    if result.returncode != 0:
        print("No @KafkaListener files found")
        return {}
    
    files = result.stdout.strip().split('\n')
    
    topic_mapping = {}
    all_topics = set()
    
    for file_path in files:
        if not file_path.strip():
            continue
            
        topics = extract_topics_from_file(file_path)
        if topics:
            service_name = extract_service_name(file_path)
            consumer_class = extract_class_name(file_path)
            
            for topic in topics:
                if topic not in topic_mapping:
                    topic_mapping[topic] = []
                topic_mapping[topic].append({
                    'service': service_name,
                    'consumer_class': consumer_class,
                    'file_path': file_path
                })
                all_topics.add(topic)
    
    return topic_mapping, all_topics

def extract_service_name(file_path):
    """Extract service name from file path."""
    parts = file_path.split('/')
    for i, part in enumerate(parts):
        if part == 'services' and i + 1 < len(parts):
            return parts[i + 1]
    return 'unknown'

def extract_class_name(file_path):
    """Extract class name from file path."""
    return os.path.basename(file_path).replace('.java', '')

if __name__ == "__main__":
    print("Extracting all Kafka topics from consumer files...")
    topic_mapping, all_topics = find_all_kafka_listeners()
    
    print(f"\nFound {len(all_topics)} unique topics across {len(topic_mapping)} topic entries")
    print(f"Total consumer files processed: {len(set(consumer['file_path'] for consumers in topic_mapping.values() for consumer in consumers))}")
    
    # Generate comprehensive report
    with open('/Users/anietieakpan/git/waqiti-app/services/kafka_topics_report.txt', 'w') as f:
        f.write("KAFKA TOPICS TO CONSUMER MAPPING REPORT\n")
        f.write("=" * 50 + "\n\n")
        
        f.write(f"Total Unique Topics: {len(all_topics)}\n")
        f.write(f"Total Topic-Consumer Mappings: {len(topic_mapping)}\n\n")
        
        f.write("TOPICS BY CONSUMER:\n")
        f.write("-" * 30 + "\n")
        
        for topic, consumers in sorted(topic_mapping.items()):
            f.write(f"\nTopic: {topic}\n")
            for consumer in consumers:
                f.write(f"  - Service: {consumer['service']}\n")
                f.write(f"    Consumer: {consumer['consumer_class']}\n")
                f.write(f"    File: {consumer['file_path']}\n")
        
        f.write("\n\nALL TOPICS LIST:\n")
        f.write("-" * 20 + "\n")
        for topic in sorted(all_topics):
            f.write(f"{topic}\n")
    
    print("Report generated: kafka_topics_report.txt")