#!/usr/bin/env python3
import os
import json
import subprocess
from pathlib import Path

def count_files(service_path, pattern):
    """Count files matching pattern in service directory"""
    try:
        result = subprocess.run(
            ['find', service_path, '-name', pattern, '-type', 'f'],
            capture_output=True, text=True, timeout=5
        )
        return len([line for line in result.stdout.strip().split('\n') if line])
    except:
        return 0

def count_grep(service_path, pattern):
    """Count grep matches in service directory"""
    try:
        result = subprocess.run(
            ['grep', '-r', '-l', pattern, '--include=*.java', service_path],
            capture_output=True, text=True, timeout=5
        )
        return len([line for line in result.stdout.strip().split('\n') if line])
    except:
        return 0

def count_todos(service_path):
    """Count TODO and FIXME comments"""
    try:
        result = subprocess.run(
            ['grep', '-r', '-E', 'TODO|FIXME', '--include=*.java', service_path],
            capture_output=True, text=True, timeout=5
        )
        return len([line for line in result.stdout.strip().split('\n') if line])
    except:
        return 0

def analyze_service(service_path):
    """Analyze a single service"""
    service_name = os.path.basename(service_path)

    # Check if it has Java source
    src_path = os.path.join(service_path, 'src/main/java')
    if not os.path.exists(src_path):
        return None

    # Count components
    controllers = count_files(service_path, '*Controller.java')
    services = count_files(service_path, '*Service*.java') - count_files(service_path, '*ServiceApplication.java')
    repositories = count_files(service_path, '*Repository.java')
    entities = count_files(service_path, '*.java')

    # Count entities more specifically
    entity_count = 0
    for root, dirs, files in os.walk(service_path):
        if 'entity' in root or 'domain' in root or 'model' in root:
            entity_count += len([f for f in files if f.endswith('.java') and not f.endswith('Test.java')])

    kafka_listeners = count_grep(service_path, '@KafkaListener')
    feign_clients = count_grep(service_path, '@FeignClient')
    todos = count_todos(service_path)

    # Determine status
    has_app = count_files(service_path, '*Application.java') > 0

    if controllers == 0 and services <= 1 and repositories == 0:
        status = "STUB"
        completion = 10
    elif controllers > 0 and services > 5 and repositories > 3:
        status = "COMPLETE"
        completion = 90
    elif controllers > 0 and services > 0:
        status = "PARTIAL"
        completion = 60
    else:
        status = "EMPTY"
        completion = 0

    return {
        "name": service_name,
        "path": f"services/{service_name}",
        "status": status,
        "completion_percentage": completion,
        "components": {
            "controllers": controllers,
            "services": services,
            "repositories": repositories,
            "entities": entity_count,
            "kafka_listeners": kafka_listeners,
            "feign_clients": feign_clients
        },
        "todos_count": todos,
        "has_application_class": has_app
    }

def main():
    services_dir = '/Users/anietieakpan/git/waqiti-app/services'

    # Get all service directories
    service_dirs = []
    for item in os.listdir(services_dir):
        item_path = os.path.join(services_dir, item)
        if os.path.isdir(item_path) and item not in ['common', 'payment-commons', 'scripts', 'keycloak-config', 'integration-tests', 'demo', 'wallet', 'services', 'core-banking']:
            service_dirs.append(item_path)

    service_dirs.sort()

    results = []
    for service_path in service_dirs:
        print(f"Analyzing {os.path.basename(service_path)}...")
        result = analyze_service(service_path)
        if result:
            results.append(result)

    # Calculate summary
    complete = sum(1 for s in results if s['status'] == 'COMPLETE')
    partial = sum(1 for s in results if s['status'] == 'PARTIAL')
    stub = sum(1 for s in results if s['status'] == 'STUB')
    empty = sum(1 for s in results if s['status'] == 'EMPTY')

    output = {
        "services_analyzed": len(results),
        "services": results,
        "summary": {
            "complete_services": complete,
            "partial_services": partial,
            "stub_services": stub,
            "empty_services": empty
        }
    }

    # Write output
    output_file = os.path.join(services_dir, 'service_analysis.json')
    with open(output_file, 'w') as f:
        json.dump(output, f, indent=2)

    print(f"\nAnalysis complete! Results written to {output_file}")
    print(f"Total services analyzed: {len(results)}")
    print(f"Complete: {complete}, Partial: {partial}, Stub: {stub}, Empty: {empty}")

if __name__ == '__main__':
    main()
