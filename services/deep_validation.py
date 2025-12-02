#!/usr/bin/env python3
"""
Deep validation with minimal false positives
Focuses on truly missing classes and broken method calls
"""

import os
import re
import json
from pathlib import Path
from collections import defaultdict, Counter

class DeepValidator:
    def __init__(self, base_path):
        self.base_path = Path(base_path)
        self.services_path = self.base_path / "services"

        # Comprehensive class registry
        self.class_files = {}  # fully_qualified_name -> file_path
        self.imports_map = defaultdict(set)  # file_path -> set of imported classes
        self.class_definitions = {}  # fully_qualified_name -> (type, is_public, annotations)

    def build_class_registry(self):
        """Build comprehensive registry of all classes, interfaces, and enums"""
        print("Building comprehensive class registry...")

        for java_file in self.services_path.rglob("*.java"):
            if '/test/' in str(java_file) or '/tests/' in str(java_file):
                continue  # Skip test files

            try:
                with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()

                # Extract package
                package_match = re.search(r'package\s+([\w.]+);', content)
                if not package_match:
                    continue

                package = package_match.group(1)

                # Find all class/interface/enum definitions
                patterns = [
                    r'(public\s+)?(class|interface|enum|@interface)\s+(\w+)',
                ]

                for pattern in patterns:
                    for match in re.finditer(pattern, content):
                        is_public = bool(match.group(1))
                        def_type = match.group(2)
                        class_name = match.group(3)

                        fqn = f"{package}.{class_name}"
                        self.class_files[fqn] = str(java_file)

                        # Check for inner classes (static nested)
                        inner_pattern = rf'{class_name}\s*{{[^}}]*?(public\s+)?(static\s+)?(class|interface|enum)\s+(\w+)'
                        for inner_match in re.finditer(inner_pattern, content):
                            inner_name = inner_match.group(4)
                            inner_fqn = f"{fqn}.{inner_name}"
                            self.class_files[inner_fqn] = str(java_file)

                        self.class_definitions[fqn] = {
                            'type': def_type,
                            'is_public': is_public,
                            'file': str(java_file)
                        }

                # Extract imports from this file
                for import_match in re.finditer(r'import\s+([\w.]+);', content):
                    imported = import_match.group(1)
                    if 'com.waqiti' in imported and not imported.endswith('*'):
                        self.imports_map[str(java_file)].add(imported)

            except Exception as e:
                pass

        print(f"  Found {len(self.class_files)} class definitions")
        print(f"  Scanned {len(self.imports_map)} files with imports")

    def find_truly_missing_imports(self):
        """Find imports that truly don't exist"""
        print("\nFinding truly missing imports...")

        missing = []

        for file_path, imports in self.imports_map.items():
            for imported_class in imports:
                # Check if class exists
                if imported_class not in self.class_files:
                    # Not in our codebase
                    missing.append({
                        'file': file_path,
                        'import': imported_class,
                        'type': 'MISSING_CLASS'
                    })

        print(f"  Found {len(missing)} truly missing imports")
        return missing

    def analyze_common_service_patterns(self):
        """Check for common broken service method patterns"""
        print("\nAnalyzing common service method patterns...")

        issues = []

        # Common method calls to check
        patterns_to_check = [
            # Format: (regex_pattern, expected_service_class, common_methods)
            (r'(\w*[Ll]edger\w*[Ss]ervice)\.(\w+)\(', 'LedgerService',
             ['createEntry', 'postTransaction', 'reconcile', 'getBalance']),
            (r'(\w*[Ww]allet\w*[Ss]ervice)\.(\w+)\(', 'WalletService',
             ['debit', 'credit', 'transfer', 'getBalance', 'lockWallet']),
            (r'(\w*[Ff]raud\w*[Dd]etection\w*[Ss]ervice)\.(\w+)\(', 'FraudDetectionService',
             ['checkTransaction', 'evaluateRisk', 'flagTransaction']),
        ]

        # Sample files to check (avoid checking all 11k+ files)
        sample_files = []
        for service_dir in self.services_path.iterdir():
            if service_dir.is_dir() and service_dir.name.endswith('-service'):
                service_files = list(service_dir.rglob('*Service.java'))
                sample_files.extend(service_files[:5])  # Sample 5 from each service

        for java_file in sample_files[:100]:  # Limit to 100 files
            try:
                with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()

                for pattern, service_name, known_methods in patterns_to_check:
                    for match in re.finditer(pattern, content):
                        method_name = match.group(2)
                        if method_name and method_name not in known_methods:
                            issues.append({
                                'file': str(java_file),
                                'service': service_name,
                                'method': method_name,
                                'type': 'SUSPICIOUS_METHOD_CALL'
                            })

            except Exception as e:
                pass

        print(f"  Found {len(issues)} suspicious method calls")
        return issues

    def check_spring_bean_registrations(self):
        """Check for @Autowired classes that might not be beans"""
        print("\nChecking Spring bean registrations...")

        issues = []

        # Find all classes with Spring stereotype annotations
        beans = set()
        for fqn, info in self.class_definitions.items():
            file_path = info['file']
            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
                    if re.search(r'@(Component|Service|Repository|Controller|RestController|Configuration)', content):
                        beans.add(fqn)
            except:
                pass

        print(f"  Found {len(beans)} registered beans")

        # Sample check: Look for @Autowired fields
        sample_count = 0
        for java_file in list(self.imports_map.keys())[:200]:  # Sample 200 files
            try:
                with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()

                # Find @Autowired fields
                autowired_pattern = r'@(Autowired|Inject)\s+(?:private|protected|public)?\s+([\w<>.,\s]+)\s+(\w+);'
                for match in re.finditer(autowired_pattern, content):
                    field_type = match.group(2).strip()
                    # Simplify generics
                    base_type = re.sub(r'<.*>', '', field_type).strip()

                    # Try to find FQN from imports
                    fqn = None
                    for imp in self.imports_map.get(java_file, []):
                        if imp.endswith(f'.{base_type}'):
                            fqn = imp
                            break

                    if fqn and fqn in self.class_files and fqn not in beans:
                        issues.append({
                            'file': java_file,
                            'class': fqn,
                            'type': 'MISSING_BEAN_ANNOTATION'
                        })
                        sample_count += 1

            except Exception as e:
                pass

        print(f"  Found {len(issues)} potential missing bean annotations")
        return issues

    def generate_report(self, missing_imports, suspicious_methods, missing_beans):
        """Generate final report"""

        # Group by service
        by_service = defaultdict(lambda: defaultdict(list))

        for item in missing_imports:
            service = self._extract_service(item['file'])
            by_service[service]['missing_imports'].append(item)

        for item in suspicious_methods:
            service = self._extract_service(item['file'])
            by_service[service]['suspicious_methods'].append(item)

        for item in missing_beans:
            service = self._extract_service(item['file'])
            by_service[service]['missing_beans'].append(item)

        # Find most common missing classes
        missing_class_counter = Counter()
        for item in missing_imports:
            missing_class_counter[item['import']] += 1

        report = {
            'summary': {
                'total_missing_imports': len(missing_imports),
                'total_suspicious_methods': len(suspicious_methods),
                'total_missing_beans': len(missing_beans),
                'services_affected': len(by_service),
                'total_classes_found': len(self.class_files)
            },
            'most_common_missing_classes': [
                {'class': cls, 'occurrences': count}
                for cls, count in missing_class_counter.most_common(50)
            ],
            'by_service': {
                service: {
                    'missing_imports': len(issues['missing_imports']),
                    'suspicious_methods': len(issues['suspicious_methods']),
                    'missing_beans': len(issues['missing_beans']),
                    'total': sum(len(v) for v in issues.values()),
                    'samples': {
                        'missing_imports': issues['missing_imports'][:10],
                        'suspicious_methods': issues['suspicious_methods'][:10],
                        'missing_beans': issues['missing_beans'][:5]
                    }
                }
                for service, issues in sorted(by_service.items(),
                                             key=lambda x: sum(len(v) for v in x[1].values()),
                                             reverse=True)[:20]
            }
        }

        return report

    def _extract_service(self, file_path):
        """Extract service name from file path"""
        if '/services/' in file_path:
            parts = file_path.split('/services/')[1].split('/')
            return parts[0]
        return 'unknown'

    def run(self):
        """Run deep validation"""
        self.build_class_registry()
        missing_imports = self.find_truly_missing_imports()
        suspicious_methods = self.analyze_common_service_patterns()
        missing_beans = self.check_spring_bean_registrations()

        report = self.generate_report(missing_imports, suspicious_methods, missing_beans)

        # Save report
        report_path = self.services_path / "DEEP_VALIDATION_REPORT.json"
        with open(report_path, 'w') as f:
            json.dump(report, f, indent=2)

        print(f"\n{'='*80}")
        print("DEEP VALIDATION COMPLETE")
        print(f"{'='*80}")
        print(f"Missing Imports (truly broken): {report['summary']['total_missing_imports']}")
        print(f"Suspicious Method Calls: {report['summary']['total_suspicious_methods']}")
        print(f"Missing Bean Annotations: {report['summary']['total_missing_beans']}")
        print(f"Services Affected: {report['summary']['services_affected']}")
        print(f"\nReport saved to: {report_path}")

        return report

if __name__ == "__main__":
    validator = DeepValidator("/Users/anietieakpan/git/waqiti-app")
    validator.run()
