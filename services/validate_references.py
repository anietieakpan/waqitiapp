#!/usr/bin/env python3
"""
Comprehensive Internal Reference Validator for Waqiti Codebase
Validates imports, method calls, Spring beans, and Feign clients
"""

import os
import re
import json
from pathlib import Path
from collections import defaultdict
from dataclasses import dataclass, field
from typing import List, Dict, Set, Tuple

@dataclass
class BrokenReference:
    source_file: str
    line_number: int
    reference_type: str
    target: str
    expected: str
    actual: str
    severity: str
    impact: str
    recommended_fix: str

@dataclass
class ValidationReport:
    broken_imports: List[BrokenReference] = field(default_factory=list)
    broken_method_calls: List[BrokenReference] = field(default_factory=list)
    missing_beans: List[BrokenReference] = field(default_factory=list)
    broken_feign_clients: List[BrokenReference] = field(default_factory=list)
    circular_dependencies: List[Tuple[str, str]] = field(default_factory=list)
    deprecated_usage: List[BrokenReference] = field(default_factory=list)

class ReferenceValidator:
    def __init__(self, base_path: str):
        self.base_path = Path(base_path)
        self.services_path = self.base_path / "services"
        self.report = ValidationReport()

        # Cache structures
        self.class_registry: Dict[str, str] = {}  # fully_qualified_name -> file_path
        self.public_classes: Set[str] = set()
        self.component_classes: Set[str] = set()
        self.service_classes: Set[str] = set()
        self.repository_classes: Set[str] = set()
        self.feign_clients: Dict[str, Dict] = {}
        self.class_methods: Dict[str, Set[str]] = defaultdict(set)

    def scan_all_classes(self):
        """Phase 0: Build registry of all classes and their properties"""
        print("Phase 0: Building class registry...")

        for java_file in self.services_path.rglob("*.java"):
            try:
                with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()

                # Extract package and class name
                package_match = re.search(r'package\s+([\w.]+);', content)
                class_match = re.search(r'(public\s+)?(class|interface|enum)\s+(\w+)', content)

                if package_match and class_match:
                    package_name = package_match.group(1)
                    class_name = class_match.group(3)
                    fully_qualified = f"{package_name}.{class_name}"

                    self.class_registry[fully_qualified] = str(java_file)

                    # Check if public
                    if class_match.group(1):
                        self.public_classes.add(fully_qualified)

                    # Check for Spring annotations
                    if re.search(r'@Component\b', content):
                        self.component_classes.add(fully_qualified)
                    if re.search(r'@Service\b', content):
                        self.service_classes.add(fully_qualified)
                    if re.search(r'@Repository\b', content):
                        self.repository_classes.add(fully_qualified)

                    # Check for Feign client
                    feign_match = re.search(r'@FeignClient\s*\([^)]*name\s*=\s*"([^"]+)"[^)]*\)', content)
                    if feign_match:
                        self.feign_clients[fully_qualified] = {
                            'file': str(java_file),
                            'service_name': feign_match.group(1),
                            'content': content
                        }

                    # Extract method signatures
                    method_pattern = r'(public|protected|private)?\s+[\w<>\[\],\s]+\s+(\w+)\s*\([^)]*\)'
                    for method_match in re.finditer(method_pattern, content):
                        method_name = method_match.group(2)
                        if method_name not in ['class', 'if', 'for', 'while', 'switch']:
                            self.class_methods[fully_qualified].add(method_name)

            except Exception as e:
                print(f"Error scanning {java_file}: {e}")

        print(f"Registered {len(self.class_registry)} classes")
        print(f"  - {len(self.public_classes)} public classes")
        print(f"  - {len(self.component_classes)} @Component")
        print(f"  - {len(self.service_classes)} @Service")
        print(f"  - {len(self.repository_classes)} @Repository")
        print(f"  - {len(self.feign_clients)} @FeignClient")

    def validate_imports(self):
        """Phase 1: Validate all import statements"""
        print("\nPhase 1: Validating import statements...")

        checked = 0
        for java_file in self.services_path.rglob("*.java"):
            try:
                with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
                    lines = f.readlines()

                for line_num, line in enumerate(lines, 1):
                    import_match = re.match(r'import\s+([\w.]+);', line.strip())
                    if import_match:
                        imported_class = import_match.group(1)

                        # Only check internal waqiti imports
                        if 'com.waqiti' in imported_class:
                            if imported_class not in self.class_registry:
                                # Check if it's a wildcard import
                                if not line.strip().endswith('*;'):
                                    self.report.broken_imports.append(BrokenReference(
                                        source_file=str(java_file),
                                        line_number=line_num,
                                        reference_type="IMPORT",
                                        target=imported_class,
                                        expected=f"Class {imported_class} should exist",
                                        actual="Class not found in codebase",
                                        severity="ERROR",
                                        impact="Compilation will fail",
                                        recommended_fix=f"Remove import or create class {imported_class}"
                                    ))
                            elif imported_class not in self.public_classes:
                                # Class exists but is not public
                                self.report.broken_imports.append(BrokenReference(
                                    source_file=str(java_file),
                                    line_number=line_num,
                                    reference_type="IMPORT",
                                    target=imported_class,
                                    expected=f"Class {imported_class} should be public",
                                    actual="Class is not public",
                                    severity="WARNING",
                                    impact="May cause compilation issues",
                                    recommended_fix=f"Make class public or adjust visibility"
                                ))

                    checked += 1
                    if checked % 10000 == 0:
                        print(f"  Checked {checked} lines...")

            except Exception as e:
                print(f"Error validating imports in {java_file}: {e}")

        print(f"Found {len(self.report.broken_imports)} broken imports")

    def validate_method_calls(self):
        """Phase 2: Validate common method call patterns"""
        print("\nPhase 2: Validating method calls...")

        # Common service patterns to check
        service_patterns = [
            (r'ledgerService\.(\w+)\(', 'com.waqiti.ledger.service.LedgerService'),
            (r'walletService\.(\w+)\(', 'com.waqiti.wallet.service.WalletService'),
            (r'fraudDetectionService\.(\w+)\(', 'com.waqiti.fraud.service.FraudDetectionService'),
            (r'paymentService\.(\w+)\(', 'com.waqiti.payment.service.PaymentService'),
            (r'userService\.(\w+)\(', 'com.waqiti.user.service.UserService'),
            (r'accountService\.(\w+)\(', 'com.waqiti.account.service.AccountService'),
            (r'transactionService\.(\w+)\(', 'com.waqiti.transaction.service.TransactionService'),
        ]

        for java_file in self.services_path.rglob("*.java"):
            try:
                with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
                    lines = content.split('\n')

                for pattern, service_class in service_patterns:
                    for match in re.finditer(pattern, content):
                        method_name = match.group(1)

                        # Check if service class exists
                        if service_class in self.class_registry:
                            # Check if method exists
                            if method_name not in self.class_methods.get(service_class, set()):
                                # Find line number
                                line_num = content[:match.start()].count('\n') + 1

                                self.report.broken_method_calls.append(BrokenReference(
                                    source_file=str(java_file),
                                    line_number=line_num,
                                    reference_type="METHOD_CALL",
                                    target=f"{service_class}.{method_name}",
                                    expected=f"Method {method_name} should exist in {service_class}",
                                    actual=f"Method not found. Available methods: {', '.join(list(self.class_methods.get(service_class, set()))[:5])}...",
                                    severity="ERROR",
                                    impact="Runtime error or compilation failure",
                                    recommended_fix=f"Implement {method_name} in {service_class} or fix method name"
                                ))

            except Exception as e:
                print(f"Error validating methods in {java_file}: {e}")

        print(f"Found {len(self.report.broken_method_calls)} broken method calls")

    def validate_spring_beans(self):
        """Phase 3: Validate Spring Bean dependencies"""
        print("\nPhase 3: Validating Spring Bean dependencies...")

        for java_file in self.services_path.rglob("*.java"):
            try:
                with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
                    lines = content.split('\n')

                # Find @Autowired or @Inject fields
                autowired_pattern = r'@(Autowired|Inject)\s+(?:private|protected|public)?\s+([\w<>.,\s]+)\s+(\w+);'

                for match in re.finditer(autowired_pattern, content):
                    field_type = match.group(2).strip()
                    field_name = match.group(3)

                    # Simplify generic types
                    base_type = re.sub(r'<.*>', '', field_type).strip()

                    # Check if it's a waqiti class
                    if not base_type.startswith('com.waqiti'):
                        # Try to find the full qualified name from imports
                        import_pattern = rf'import\s+([\w.]+\.{re.escape(base_type)});'
                        import_match = re.search(import_pattern, content)
                        if import_match:
                            base_type = import_match.group(1)

                    if 'com.waqiti' in base_type:
                        # Check if class exists and is a component
                        if base_type not in self.class_registry:
                            line_num = content[:match.start()].count('\n') + 1
                            self.report.missing_beans.append(BrokenReference(
                                source_file=str(java_file),
                                line_number=line_num,
                                reference_type="SPRING_BEAN",
                                target=base_type,
                                expected=f"Bean {base_type} should exist",
                                actual="Class not found",
                                severity="ERROR",
                                impact="Application startup will fail",
                                recommended_fix=f"Create class {base_type} or fix type"
                            ))
                        elif (base_type not in self.component_classes and
                              base_type not in self.service_classes and
                              base_type not in self.repository_classes):
                            line_num = content[:match.start()].count('\n') + 1
                            self.report.missing_beans.append(BrokenReference(
                                source_file=str(java_file),
                                line_number=line_num,
                                reference_type="SPRING_BEAN",
                                target=base_type,
                                expected=f"Bean {base_type} should be annotated with @Component/@Service/@Repository",
                                actual="Missing Spring annotation",
                                severity="ERROR",
                                impact="Bean not found exception at startup",
                                recommended_fix=f"Add @Service or @Component to {base_type}"
                            ))

            except Exception as e:
                print(f"Error validating beans in {java_file}: {e}")

        print(f"Found {len(self.report.missing_beans)} missing/invalid beans")

    def validate_feign_clients(self):
        """Phase 4: Validate Feign Client configurations"""
        print("\nPhase 4: Validating Feign Client configurations...")

        for fqn, feign_info in self.feign_clients.items():
            content = feign_info['content']
            service_name = feign_info['service_name']
            file_path = feign_info['file']

            # Check for fallback
            fallback_match = re.search(r'fallback\s*=\s*(\w+)\.class', content)
            if fallback_match:
                fallback_class = fallback_match.group(1)

                # Try to find the full qualified name
                fallback_fqn = None
                for cls in self.class_registry:
                    if cls.endswith(f'.{fallback_class}'):
                        fallback_fqn = cls
                        break

                if not fallback_fqn:
                    self.report.broken_feign_clients.append(BrokenReference(
                        source_file=file_path,
                        line_number=1,
                        reference_type="FEIGN_FALLBACK",
                        target=fallback_class,
                        expected=f"Fallback class {fallback_class} should exist",
                        actual="Fallback class not found",
                        severity="ERROR",
                        impact="Circuit breaker will not work",
                        recommended_fix=f"Create fallback class {fallback_class}"
                    ))
                elif fallback_fqn not in self.component_classes:
                    self.report.broken_feign_clients.append(BrokenReference(
                        source_file=file_path,
                        line_number=1,
                        reference_type="FEIGN_FALLBACK",
                        target=fallback_class,
                        expected=f"Fallback class {fallback_class} should be @Component",
                        actual="Missing @Component annotation",
                        severity="ERROR",
                        impact="Fallback will not be registered",
                        recommended_fix=f"Add @Component to {fallback_class}"
                    ))

        print(f"Found {len(self.report.broken_feign_clients)} Feign client issues")

    def detect_circular_dependencies(self):
        """Detect circular import dependencies"""
        print("\nDetecting circular dependencies...")

        # Build dependency graph
        dependencies: Dict[str, Set[str]] = defaultdict(set)

        for java_file in self.services_path.rglob("*.java"):
            try:
                with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()

                # Get current package and class
                package_match = re.search(r'package\s+([\w.]+);', content)
                class_match = re.search(r'(public\s+)?(class|interface|enum)\s+(\w+)', content)

                if package_match and class_match:
                    current_fqn = f"{package_match.group(1)}.{class_match.group(3)}"

                    # Find all imports
                    for import_match in re.finditer(r'import\s+([\w.]+);', content):
                        imported = import_match.group(1)
                        if 'com.waqiti' in imported and imported in self.class_registry:
                            dependencies[current_fqn].add(imported)

            except Exception as e:
                pass

        # Simple cycle detection (A -> B -> A)
        for cls_a, deps in dependencies.items():
            for cls_b in deps:
                if cls_a in dependencies.get(cls_b, set()):
                    self.report.circular_dependencies.append((cls_a, cls_b))

        print(f"Found {len(self.report.circular_dependencies)} circular dependencies")

    def generate_report(self) -> Dict:
        """Generate comprehensive JSON report"""

        # Group by severity
        critical = []
        errors = []
        warnings = []

        all_issues = (self.report.broken_imports +
                     self.report.broken_method_calls +
                     self.report.missing_beans +
                     self.report.broken_feign_clients)

        for issue in all_issues:
            if issue.severity == "ERROR":
                if "startup" in issue.impact.lower() or "compilation" in issue.impact.lower():
                    critical.append(issue)
                else:
                    errors.append(issue)
            else:
                warnings.append(issue)

        # Count by service
        service_issues = defaultdict(int)
        for issue in all_issues:
            service_match = re.search(r'/services/([^/]+)/', issue.source_file)
            if service_match:
                service_issues[service_match.group(1)] += 1

        return {
            "summary": {
                "total_issues": len(all_issues),
                "critical": len(critical),
                "errors": len(errors),
                "warnings": len(warnings),
                "circular_dependencies": len(self.report.circular_dependencies),
                "services_affected": len(service_issues),
                "classes_scanned": len(self.class_registry)
            },
            "by_category": {
                "broken_imports": len(self.report.broken_imports),
                "broken_method_calls": len(self.report.broken_method_calls),
                "missing_beans": len(self.report.missing_beans),
                "broken_feign_clients": len(self.report.broken_feign_clients)
            },
            "critical_issues": [
                {
                    "source": issue.source_file,
                    "line": issue.line_number,
                    "type": issue.reference_type,
                    "target": issue.target,
                    "expected": issue.expected,
                    "actual": issue.actual,
                    "impact": issue.impact,
                    "fix": issue.recommended_fix
                }
                for issue in critical[:50]  # Top 50
            ],
            "errors": [
                {
                    "source": issue.source_file,
                    "line": issue.line_number,
                    "type": issue.reference_type,
                    "target": issue.target,
                    "expected": issue.expected,
                    "actual": issue.actual,
                    "impact": issue.impact,
                    "fix": issue.recommended_fix
                }
                for issue in errors[:100]  # Top 100
            ],
            "warnings": [
                {
                    "source": issue.source_file,
                    "line": issue.line_number,
                    "type": issue.reference_type,
                    "target": issue.target,
                    "expected": issue.expected,
                    "actual": issue.actual,
                    "impact": issue.impact,
                    "fix": issue.recommended_fix
                }
                for issue in warnings[:100]  # Top 100
            ],
            "circular_dependencies": [
                {"class_a": a, "class_b": b}
                for a, b in self.report.circular_dependencies[:50]
            ],
            "services_affected": dict(sorted(service_issues.items(), key=lambda x: x[1], reverse=True)[:20])
        }

    def run_validation(self):
        """Run all validation phases"""
        print("Starting comprehensive validation...")
        print(f"Base path: {self.base_path}")
        print(f"Services path: {self.services_path}")

        self.scan_all_classes()
        self.validate_imports()
        self.validate_method_calls()
        self.validate_spring_beans()
        self.validate_feign_clients()
        self.detect_circular_dependencies()

        report = self.generate_report()

        # Write report
        report_path = self.services_path / "REFERENCE_VALIDATION_REPORT.json"
        with open(report_path, 'w') as f:
            json.dump(report, f, indent=2)

        print(f"\n{'='*80}")
        print("VALIDATION COMPLETE")
        print(f"{'='*80}")
        print(f"Total Issues: {report['summary']['total_issues']}")
        print(f"  Critical: {report['summary']['critical']}")
        print(f"  Errors: {report['summary']['errors']}")
        print(f"  Warnings: {report['summary']['warnings']}")
        print(f"Circular Dependencies: {report['summary']['circular_dependencies']}")
        print(f"Services Affected: {report['summary']['services_affected']}")
        print(f"\nFull report written to: {report_path}")

        return report

if __name__ == "__main__":
    validator = ReferenceValidator("/Users/anietieakpan/git/waqiti-app")
    report = validator.run_validation()
