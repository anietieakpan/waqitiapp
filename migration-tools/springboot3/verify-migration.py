#!/usr/bin/env python3
"""
Verification script for Spring Boot 3.x migration
Checks for remaining issues and generates a detailed report
"""

import os
import re
import json
import logging
import argparse
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Set

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class MigrationVerifier:
    """Verifies the migration was successful"""
    
    def __init__(self, project_root: Path):
        self.project_root = project_root
        self.issues = {
            'javax_imports': [],
            'type_annotations': [],
            'security_deprecations': [],
            'duplicate_imports': [],
            'missing_imports': [],
            'compilation_errors': [],
            'other_issues': []
        }
        self.stats = {
            'files_checked': 0,
            'files_with_issues': 0,
            'total_issues': 0
        }
    
    def find_java_files(self) -> List[Path]:
        """Find all Java files in the project"""
        java_files = []
        services_dir = self.project_root / "services"
        
        if services_dir.exists():
            for file_path in services_dir.rglob("*.java"):
                if "/test/" not in str(file_path) and "/target/" not in str(file_path):
                    java_files.append(file_path)
                    
        return java_files
    
    def check_javax_imports(self, file_path: Path, content: str) -> List[Dict]:
        """Check for remaining javax imports that should be jakarta"""
        issues = []
        
        # Patterns for javax imports that should be migrated
        javax_patterns = [
            (r'import\s+javax\.persistence\.', 'jakarta.persistence'),
            (r'import\s+javax\.validation\.', 'jakarta.validation'),
            (r'import\s+javax\.servlet\.', 'jakarta.servlet'),
            (r'import\s+javax\.annotation\.', 'jakarta.annotation'),
            (r'import\s+javax\.transaction\.', 'jakarta.transaction'),
            (r'import\s+javax\.websocket\.', 'jakarta.websocket'),
            (r'import\s+javax\.enterprise\.', 'jakarta.enterprise'),
            (r'import\s+javax\.inject\.', 'jakarta.inject'),
            (r'import\s+javax\.ws\.rs\.', 'jakarta.ws.rs'),
        ]
        
        for pattern, replacement in javax_patterns:
            matches = list(re.finditer(pattern, content))
            for match in matches:
                line_num = content[:match.start()].count('\n') + 1
                issues.append({
                    'file': str(file_path),
                    'line': line_num,
                    'issue': f"Found {match.group()} - should be {replacement}",
                    'type': 'javax_import'
                })
        
        return issues
    
    def check_type_annotations(self, file_path: Path, content: str) -> List[Dict]:
        """Check for remaining @Type annotations"""
        issues = []
        
        # Patterns for @Type annotations
        type_patterns = [
            r'@Type\s*\(\s*type\s*=\s*"jsonb"\s*\)',
            r'@Type\s*\(\s*JsonType\.class\s*\)',
            r'@Type\s*\(\s*io\.hypersistence\.utils\.hibernate\.type\.json\.JsonType\.class\s*\)',
        ]
        
        for pattern in type_patterns:
            matches = list(re.finditer(pattern, content))
            for match in matches:
                line_num = content[:match.start()].count('\n') + 1
                issues.append({
                    'file': str(file_path),
                    'line': line_num,
                    'issue': f"Found {match.group()} - should be @JdbcTypeCode(SqlTypes.JSON)",
                    'type': 'type_annotation'
                })
        
        return issues
    
    def check_security_deprecations(self, file_path: Path, content: str) -> List[Dict]:
        """Check for deprecated Spring Security methods"""
        issues = []
        
        # Patterns for deprecated methods
        deprecated_patterns = [
            (r'\.antMatchers\s*\(', '.requestMatchers('),
            (r'\.mvcMatchers\s*\(', '.requestMatchers('),
            (r'\.authorizeRequests\s*\(\s*\)', '.authorizeHttpRequests()'),
        ]
        
        for pattern, replacement in deprecated_patterns:
            matches = list(re.finditer(pattern, content))
            for match in matches:
                line_num = content[:match.start()].count('\n') + 1
                issues.append({
                    'file': str(file_path),
                    'line': line_num,
                    'issue': f"Found {match.group()} - should be {replacement}",
                    'type': 'security_deprecation'
                })
        
        return issues
    
    def check_duplicate_imports(self, file_path: Path, content: str) -> List[Dict]:
        """Check for duplicate import statements"""
        issues = []
        
        imports = re.findall(r'import\s+([\w\.]+);', content)
        seen = set()
        duplicates = set()
        
        for imp in imports:
            if imp in seen:
                duplicates.add(imp)
            seen.add(imp)
        
        if duplicates:
            issues.append({
                'file': str(file_path),
                'line': 0,
                'issue': f"Duplicate imports found: {', '.join(duplicates)}",
                'type': 'duplicate_import'
            })
        
        return issues
    
    def check_missing_imports(self, file_path: Path, content: str) -> List[Dict]:
        """Check for missing imports after migration"""
        issues = []
        
        # Check if @JdbcTypeCode is used but not imported
        if '@JdbcTypeCode' in content and 'import org.hibernate.annotations.JdbcTypeCode;' not in content:
            issues.append({
                'file': str(file_path),
                'line': 0,
                'issue': "Missing import: org.hibernate.annotations.JdbcTypeCode",
                'type': 'missing_import'
            })
        
        # Check if SqlTypes is used but not imported
        if 'SqlTypes.JSON' in content and 'import org.hibernate.type.SqlTypes;' not in content:
            issues.append({
                'file': str(file_path),
                'line': 0,
                'issue': "Missing import: org.hibernate.type.SqlTypes",
                'type': 'missing_import'
            })
        
        return issues
    
    def check_file(self, file_path: Path) -> List[Dict]:
        """Check a single file for migration issues"""
        all_issues = []
        
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Run all checks
            all_issues.extend(self.check_javax_imports(file_path, content))
            all_issues.extend(self.check_type_annotations(file_path, content))
            all_issues.extend(self.check_security_deprecations(file_path, content))
            all_issues.extend(self.check_duplicate_imports(file_path, content))
            all_issues.extend(self.check_missing_imports(file_path, content))
            
        except Exception as e:
            all_issues.append({
                'file': str(file_path),
                'line': 0,
                'issue': f"Error reading file: {str(e)}",
                'type': 'file_error'
            })
        
        return all_issues
    
    def verify_migration(self) -> Dict:
        """Run verification on all Java files"""
        logger.info(f"Starting migration verification...")
        logger.info(f"Project root: {self.project_root}")
        
        # Find all Java files
        java_files = self.find_java_files()
        logger.info(f"Found {len(java_files)} Java files to verify")
        
        # Check each file
        for file_path in java_files:
            self.stats['files_checked'] += 1
            
            issues = self.check_file(file_path)
            
            if issues:
                self.stats['files_with_issues'] += 1
                self.stats['total_issues'] += len(issues)
                
                # Categorize issues
                for issue in issues:
                    issue_type = issue['type']
                    if issue_type == 'javax_import':
                        self.issues['javax_imports'].append(issue)
                    elif issue_type == 'type_annotation':
                        self.issues['type_annotations'].append(issue)
                    elif issue_type == 'security_deprecation':
                        self.issues['security_deprecations'].append(issue)
                    elif issue_type == 'duplicate_import':
                        self.issues['duplicate_imports'].append(issue)
                    elif issue_type == 'missing_import':
                        self.issues['missing_imports'].append(issue)
                    else:
                        self.issues['other_issues'].append(issue)
        
        # Generate report
        self.generate_report()
        
        return {
            'stats': self.stats,
            'issues': self.issues
        }
    
    def generate_report(self):
        """Generate verification report"""
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        report_path = Path(f"migration-logs/verification_report_{timestamp}.json")
        report_path.parent.mkdir(exist_ok=True)
        
        report = {
            'timestamp': datetime.now().isoformat(),
            'project_root': str(self.project_root),
            'statistics': self.stats,
            'issues_by_type': {
                'javax_imports': len(self.issues['javax_imports']),
                'type_annotations': len(self.issues['type_annotations']),
                'security_deprecations': len(self.issues['security_deprecations']),
                'duplicate_imports': len(self.issues['duplicate_imports']),
                'missing_imports': len(self.issues['missing_imports']),
                'other_issues': len(self.issues['other_issues'])
            },
            'detailed_issues': self.issues
        }
        
        with open(report_path, 'w') as f:
            json.dump(report, f, indent=2)
        
        # Print summary
        logger.info(f"\nVerification Report:")
        logger.info(f"  Files checked: {self.stats['files_checked']}")
        logger.info(f"  Files with issues: {self.stats['files_with_issues']}")
        logger.info(f"  Total issues found: {self.stats['total_issues']}")
        
        if self.stats['total_issues'] > 0:
            logger.warning(f"\nIssues by type:")
            for issue_type, issues in self.issues.items():
                if issues:
                    logger.warning(f"  {issue_type}: {len(issues)}")
            
            # Show sample issues
            logger.warning(f"\nSample issues:")
            sample_count = 0
            for issue_type, issues in self.issues.items():
                if issues and sample_count < 10:
                    for issue in issues[:2]:  # Show 2 samples per type
                        logger.warning(f"  [{issue_type}] {issue['file']}:{issue['line']} - {issue['issue']}")
                        sample_count += 1
                        if sample_count >= 10:
                            break
        
        logger.info(f"\nFull report saved to: {report_path}")

def main():
    parser = argparse.ArgumentParser(description='Verify Spring Boot 3.x migration')
    parser.add_argument('--project-root', type=str, default='.', help='Project root directory')
    parser.add_argument('--fix-issues', action='store_true', help='Attempt to fix found issues')
    
    args = parser.parse_args()
    
    project_root = Path(args.project_root).resolve()
    
    # Run verification
    verifier = MigrationVerifier(project_root)
    results = verifier.verify_migration()
    
    # Exit with error code if issues found
    if results['stats']['total_issues'] > 0:
        exit(1)

if __name__ == "__main__":
    main()