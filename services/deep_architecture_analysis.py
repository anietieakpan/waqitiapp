#!/usr/bin/env python3
"""
Deep Architectural Analysis Script for Waqiti Microservices
Analyzes structure, patterns, dependencies, and architecture for each service
"""

import os
import json
import subprocess
import re
from pathlib import Path
from collections import defaultdict

SERVICES_BASE = "/home/aniix/git/waqiti-app/services"

CRITICAL_SERVICES = [
    "payment-service",
    "wallet-service",
    "fraud-detection-service",
    "compliance-service",
    "core-banking-service",
    "user-service",
    "notification-service",
    "integration-service",
    "api-gateway",
    "saga-orchestration-service"
]

def run_command(cmd, cwd=None):
    """Execute shell command and return output"""
    try:
        result = subprocess.run(
            cmd,
            shell=True,
            capture_output=True,
            text=True,
            cwd=cwd,
            timeout=30
        )
        return result.stdout.strip()
    except Exception as e:
        return f"Error: {str(e)}"

def count_lines_of_code(service_path):
    """Count LOC in main source directory"""
    src_main = os.path.join(service_path, "src/main/java")
    if os.path.exists(src_main):
        cmd = f"find {src_main} -name '*.java' | xargs wc -l | tail -1"
        output = run_command(cmd)
        if output and "total" in output:
            return int(output.split()[0])
    return 0

def count_java_files(service_path):
    """Count Java files in service"""
    cmd = f"find {service_path} -name '*.java' | wc -l"
    output = run_command(cmd)
    return int(output) if output.isdigit() else 0

def find_controllers(service_path):
    """Find all REST controllers"""
    cmd = f"grep -r '@RestController\\|@Controller' {service_path}/src/main/java --include='*.java' -l 2>/dev/null | wc -l"
    output = run_command(cmd)
    return int(output) if output.isdigit() else 0

def find_services(service_path):
    """Find all service classes"""
    cmd = f"grep -r '@Service' {service_path}/src/main/java --include='*.java' -l 2>/dev/null | wc -l"
    output = run_command(cmd)
    return int(output) if output.isdigit() else 0

def find_repositories(service_path):
    """Find all repository classes"""
    cmd = f"grep -r '@Repository' {service_path}/src/main/java --include='*.java' -l 2>/dev/null | wc -l"
    output = run_command(cmd)
    return int(output) if output.isdigit() else 0

def find_entities(service_path):
    """Find all JPA entities"""
    cmd = f"grep -r '@Entity' {service_path}/src/main/java --include='*.java' -l 2>/dev/null | wc -l"
    output = run_command(cmd)
    return int(output) if output.isdigit() else 0

def find_kafka_consumers(service_path):
    """Find Kafka consumers"""
    cmd = f"grep -r '@KafkaListener' {service_path}/src/main/java --include='*.java' -l 2>/dev/null | wc -l"
    output = run_command(cmd)
    return int(output) if output.isdigit() else 0

def find_kafka_producers(service_path):
    """Find Kafka producers by looking for KafkaTemplate usage"""
    cmd = f"grep -r 'kafkaTemplate\\.send\\|KafkaTemplate' {service_path}/src/main/java --include='*.java' -l 2>/dev/null | wc -l"
    output = run_command(cmd)
    return int(output) if output.isdigit() else 0

def count_migrations(service_path):
    """Count Flyway migrations"""
    migration_dir = os.path.join(service_path, "src/main/resources/db/migration")
    if os.path.exists(migration_dir):
        cmd = f"find {migration_dir} -name '*.sql' | wc -l"
        output = run_command(cmd)
        return int(output) if output.isdigit() else 0
    return 0

def extract_kafka_topics(service_path):
    """Extract Kafka topic names from code"""
    topics = set()
    cmd = f"grep -roh 'topics\\s*=\\s*[\"']\\([^\"']*\\)[\"']' {service_path}/src/main/java --include='*.java' 2>/dev/null"
    output = run_command(cmd)
    if output:
        for line in output.split('\n'):
            match = re.search(r'topics\s*=\s*["\']([^"\']+)["\']', line)
            if match:
                topics.add(match.group(1))

    # Also check for send operations
    cmd = f"grep -roh 'send\\([\"']\\([^\"']*\\)[\"']' {service_path}/src/main/java --include='*.java' 2>/dev/null"
    output = run_command(cmd)
    if output:
        for line in output.split('\n'):
            match = re.search(r'send\(["\']([^"\']+)["\']', line)
            if match:
                topics.add(match.group(1))

    return list(topics)

def extract_entity_names(service_path):
    """Extract entity class names"""
    entities = []
    cmd = f"grep -rh '@Entity' {service_path}/src/main/java --include='*.java' -A 1 2>/dev/null"
    output = run_command(cmd)
    if output:
        lines = output.split('\n')
        for i, line in enumerate(lines):
            if '@Entity' in line and i+1 < len(lines):
                next_line = lines[i+1]
                match = re.search(r'class\s+(\w+)', next_line)
                if match:
                    entities.append(match.group(1))
    return entities

def extract_table_names(service_path):
    """Extract database table names from @Table annotations"""
    tables = []
    cmd = f"grep -roh '@Table\\(name\\s*=\\s*[\"']\\([^\"']*\\)[\"']' {service_path}/src/main/java --include='*.java' 2>/dev/null"
    output = run_command(cmd)
    if output:
        for line in output.split('\n'):
            match = re.search(r'@Table\(name\s*=\s*["\']([^"\']+)["\']', line)
            if match:
                tables.append(match.group(1))
    return tables

def find_feign_clients(service_path):
    """Find Feign client dependencies"""
    cmd = f"grep -r '@FeignClient' {service_path}/src/main/java --include='*.java' -l 2>/dev/null | wc -l"
    output = run_command(cmd)
    return int(output) if output.isdigit() else 0

def extract_dependencies(service_path):
    """Extract dependencies from pom.xml"""
    pom_file = os.path.join(service_path, "pom.xml")
    dependencies = {
        "spring_boot": False,
        "spring_security": False,
        "spring_kafka": False,
        "spring_data_jpa": False,
        "postgresql": False,
        "redis": False,
        "feign": False,
        "resilience4j": False,
        "keycloak": False
    }

    if os.path.exists(pom_file):
        try:
            with open(pom_file, 'r') as f:
                content = f.read()
                dependencies["spring_boot"] = "spring-boot-starter" in content
                dependencies["spring_security"] = "spring-boot-starter-security" in content or "spring-security" in content
                dependencies["spring_kafka"] = "spring-kafka" in content
                dependencies["spring_data_jpa"] = "spring-boot-starter-data-jpa" in content
                dependencies["postgresql"] = "postgresql" in content
                dependencies["redis"] = "spring-boot-starter-data-redis" in content
                dependencies["feign"] = "spring-cloud-starter-openfeign" in content
                dependencies["resilience4j"] = "resilience4j" in content
                dependencies["keycloak"] = "keycloak" in content
        except Exception as e:
            pass

    return dependencies

def detect_architecture_pattern(service_path, stats):
    """Detect architectural pattern used"""
    patterns = []

    # Check for layered architecture (typical Spring Boot)
    if stats["controllers"] > 0 and stats["services"] > 0 and stats["repositories"] > 0:
        patterns.append("Layered Architecture")

    # Check for hexagonal/ports-adapters
    adapter_path = os.path.join(service_path, "src/main/java")
    if os.path.exists(adapter_path):
        cmd = f"find {adapter_path} -type d -name 'adapter' -o -name 'adapters' -o -name 'port' -o -name 'ports' | wc -l"
        output = run_command(cmd)
        if output and int(output) > 0:
            patterns.append("Hexagonal/Ports-Adapters")

    # Check for CQRS
    if os.path.exists(adapter_path):
        cmd = f"grep -r 'CommandHandler\\|QueryHandler\\|Command\\|Query' {adapter_path} --include='*.java' -l 2>/dev/null | wc -l"
        output = run_command(cmd)
        if output and int(output) > 3:
            patterns.append("CQRS")

    # Check for Event Sourcing
    if os.path.exists(adapter_path):
        cmd = f"grep -r 'EventStore\\|EventSourcing\\|AggregateRoot' {adapter_path} --include='*.java' -l 2>/dev/null | wc -l"
        output = run_command(cmd)
        if output and int(output) > 0:
            patterns.append("Event Sourcing")

    # Check for Saga pattern
    if os.path.exists(adapter_path):
        cmd = f"grep -r 'Saga\\|SagaOrchestrator\\|SagaStep' {adapter_path} --include='*.java' -l 2>/dev/null | wc -l"
        output = run_command(cmd)
        if output and int(output) > 0:
            patterns.append("Saga Pattern")

    return patterns if patterns else ["Traditional Layered"]

def find_security_features(service_path):
    """Detect security implementations"""
    features = []
    src_path = os.path.join(service_path, "src/main/java")

    if not os.path.exists(src_path):
        return features

    # JWT
    cmd = f"grep -r 'JWT\\|JwtToken\\|@EnableWebSecurity' {src_path} --include='*.java' -l 2>/dev/null | wc -l"
    output = run_command(cmd)
    if output and int(output) > 0:
        features.append("JWT Authentication")

    # OAuth2
    cmd = f"grep -r 'OAuth2\\|@EnableOAuth2' {src_path} --include='*.java' -l 2>/dev/null | wc -l"
    output = run_command(cmd)
    if output and int(output) > 0:
        features.append("OAuth2")

    # Role-based access
    cmd = f"grep -r '@PreAuthorize\\|@RolesAllowed\\|@Secured' {src_path} --include='*.java' -l 2>/dev/null | wc -l"
    output = run_command(cmd)
    if output and int(output) > 0:
        features.append("Role-Based Access Control")

    # Encryption
    cmd = f"grep -r 'Encryption\\|Cipher\\|AES\\|RSA' {src_path} --include='*.java' -l 2>/dev/null | wc -l"
    output = run_command(cmd)
    if output and int(output) > 0:
        features.append("Data Encryption")

    return features

def find_api_endpoints(service_path):
    """Estimate API endpoint count"""
    src_path = os.path.join(service_path, "src/main/java")
    if not os.path.exists(src_path):
        return 0

    cmd = f"grep -r '@GetMapping\\|@PostMapping\\|@PutMapping\\|@DeleteMapping\\|@PatchMapping\\|@RequestMapping' {src_path} --include='*.java' | wc -l"
    output = run_command(cmd)
    return int(output) if output.isdigit() else 0

def analyze_service(service_name):
    """Perform comprehensive analysis of a service"""
    service_path = os.path.join(SERVICES_BASE, service_name)

    if not os.path.exists(service_path):
        return {
            "service_name": service_name,
            "exists": False,
            "error": "Service directory not found"
        }

    print(f"Analyzing {service_name}...")

    stats = {
        "java_files": count_java_files(service_path),
        "controllers": find_controllers(service_path),
        "services": find_services(service_path),
        "repositories": find_repositories(service_path),
        "entities": find_entities(service_path),
        "kafka_consumers": find_kafka_consumers(service_path),
        "kafka_producers": find_kafka_producers(service_path),
        "feign_clients": find_feign_clients(service_path)
    }

    analysis = {
        "service_name": service_name,
        "exists": True,
        "service_path": service_path,
        "statistics": {
            "total_java_files": stats["java_files"],
            "lines_of_code": count_lines_of_code(service_path),
            "controller_count": stats["controllers"],
            "service_count": stats["services"],
            "repository_count": stats["repositories"],
            "entity_count": stats["entities"],
            "api_endpoint_count": find_api_endpoints(service_path),
            "database_migrations": count_migrations(service_path),
            "kafka_consumers": stats["kafka_consumers"],
            "kafka_producers": stats["kafka_producers"],
            "feign_clients": stats["feign_clients"]
        },
        "architecture": {
            "patterns": detect_architecture_pattern(service_path, stats),
            "layered_structure": {
                "controllers": stats["controllers"] > 0,
                "services": stats["services"] > 0,
                "repositories": stats["repositories"] > 0,
                "entities": stats["entities"] > 0
            }
        },
        "domain_model": {
            "entities": extract_entity_names(service_path)[:20],  # Limit to first 20
            "tables": extract_table_names(service_path)[:20]
        },
        "messaging": {
            "kafka_topics": extract_kafka_topics(service_path)[:30],  # Limit to first 30
            "is_event_driven": stats["kafka_consumers"] > 0 or stats["kafka_producers"] > 0
        },
        "dependencies": extract_dependencies(service_path),
        "security": {
            "features": find_security_features(service_path)
        },
        "integration": {
            "uses_feign": stats["feign_clients"] > 0,
            "feign_client_count": stats["feign_clients"]
        }
    }

    return analysis

def main():
    """Main analysis function"""
    print("=" * 80)
    print("WAQITI MICROSERVICES DEEP ARCHITECTURAL ANALYSIS")
    print("=" * 80)
    print()

    results = {
        "analysis_metadata": {
            "services_analyzed": len(CRITICAL_SERVICES),
            "analysis_date": subprocess.run(
                "date +%Y-%m-%d",
                shell=True,
                capture_output=True,
                text=True
            ).stdout.strip()
        },
        "services": {}
    }

    for service_name in CRITICAL_SERVICES:
        analysis = analyze_service(service_name)
        results["services"][service_name] = analysis

    # Write to JSON file
    output_file = os.path.join(SERVICES_BASE, "DEEP_ARCHITECTURAL_ANALYSIS_REPORT.json")
    with open(output_file, 'w') as f:
        json.dump(results, f, indent=2)

    print()
    print("=" * 80)
    print(f"Analysis complete! Report saved to: {output_file}")
    print("=" * 80)

    # Print summary
    print("\n\nSUMMARY BY SERVICE:")
    print("-" * 80)
    print(f"{'Service':<35} {'LOC':>10} {'Files':>8} {'APIs':>8} {'Entities':>10}")
    print("-" * 80)

    for service_name in CRITICAL_SERVICES:
        if service_name in results["services"] and results["services"][service_name].get("exists"):
            svc = results["services"][service_name]
            stats = svc.get("statistics", {})
            print(
                f"{service_name:<35} "
                f"{stats.get('lines_of_code', 0):>10,} "
                f"{stats.get('total_java_files', 0):>8} "
                f"{stats.get('api_endpoint_count', 0):>8} "
                f"{stats.get('entity_count', 0):>10}"
            )

    print("-" * 80)

if __name__ == "__main__":
    main()
