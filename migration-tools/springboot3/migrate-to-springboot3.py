#!/usr/bin/env python3
"""
Spring Boot 3.x Migration Tool for Waqiti Application
Handles migration from Spring Boot 2.x to 3.x with:
- javax to jakarta namespace changes
- Hibernate @Type to @JdbcTypeCode migration
- Spring Security deprecated method updates
- Comprehensive logging and rollback support
"""

import os
import re
import shutil
import json
import logging
import argparse
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Tuple, Optional
import hashlib

# Configure logging
LOG_DIR = Path("migration-logs")
LOG_DIR.mkdir(exist_ok=True)

timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
log_file = LOG_DIR / f"migration_{timestamp}.log"

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(log_file),
        logging.StreamHandler()
    ]
)

logger = logging.getLogger(__name__)

class MigrationBackup:
    """Handles backup and restoration of files"""
    
    def __init__(self, backup_dir: Path):
        self.backup_dir = backup_dir
        self.backup_dir.mkdir(parents=True, exist_ok=True)
        self.backup_manifest = {}
        
    def backup_file(self, file_path: Path) -> Optional[str]:
        """Backup a file and return backup path"""
        try:
            # Create relative backup path
            rel_path = file_path.relative_to(Path.cwd())
            backup_path = self.backup_dir / rel_path
            backup_path.parent.mkdir(parents=True, exist_ok=True)
            
            # Calculate checksum
            with open(file_path, 'rb') as f:
                checksum = hashlib.md5(f.read()).hexdigest()
            
            # Copy file
            shutil.copy2(file_path, backup_path)
            
            # Store in manifest
            self.backup_manifest[str(file_path)] = {
                'backup_path': str(backup_path),
                'original_checksum': checksum,
                'timestamp': datetime.now().isoformat()
            }
            
            return str(backup_path)
            
        except Exception as e:
            logger.error(f"Failed to backup {file_path}: {e}")
            return None
    
    def restore_file(self, file_path: Path) -> bool:
        """Restore a file from backup"""
        try:
            if str(file_path) in self.backup_manifest:
                backup_info = self.backup_manifest[str(file_path)]
                backup_path = Path(backup_info['backup_path'])
                
                if backup_path.exists():
                    shutil.copy2(backup_path, file_path)
                    logger.info(f"Restored {file_path} from backup")
                    return True
                    
            logger.error(f"No backup found for {file_path}")
            return False
            
        except Exception as e:
            logger.error(f"Failed to restore {file_path}: {e}")
            return False
    
    def save_manifest(self):
        """Save backup manifest to file"""
        manifest_path = self.backup_dir / "backup_manifest.json"
        with open(manifest_path, 'w') as f:
            json.dump(self.backup_manifest, f, indent=2)

class SpringBoot3Migrator:
    """Main migration class"""
    
    def __init__(self, project_root: Path, dry_run: bool = False):
        self.project_root = project_root
        self.dry_run = dry_run
        self.backup = MigrationBackup(Path(f"backups/migration_{timestamp}"))
        self.migration_stats = {
            'files_processed': 0,
            'files_modified': 0,
            'files_failed': 0,
            'javax_imports_fixed': 0,
            'type_annotations_fixed': 0,
            'security_configs_fixed': 0,
            'errors': []
        }
        
        # Migration patterns
        self.javax_to_jakarta_patterns = [
            (r'import\s+javax\.persistence\.', 'import jakarta.persistence.'),
            (r'import\s+javax\.validation\.', 'import jakarta.validation.'),
            (r'import\s+javax\.servlet\.', 'import jakarta.servlet.'),
            (r'import\s+javax\.annotation\.', 'import jakarta.annotation.'),
            (r'import\s+javax\.transaction\.', 'import jakarta.transaction.'),
            (r'import\s+javax\.websocket\.', 'import jakarta.websocket.'),
            (r'import\s+javax\.enterprise\.', 'import jakarta.enterprise.'),
            (r'import\s+javax\.inject\.', 'import jakarta.inject.'),
            (r'import\s+javax\.ws\.rs\.', 'import jakarta.ws.rs.'),
        ]
        
        self.type_annotation_patterns = [
            # @Type(type = "jsonb") -> @JdbcTypeCode(SqlTypes.JSON)
            (r'@Type\s*\(\s*type\s*=\s*"jsonb"\s*\)', '@JdbcTypeCode(SqlTypes.JSON)'),
            # Handle multiline @Type annotations
            (r'@Type\s*\(\s*type\s*=\s*"jsonb"\s*\)\s*\n', '@JdbcTypeCode(SqlTypes.JSON)\n'),
        ]
        
        self.type_class_patterns = [
            # @Type(JsonType.class) -> @JdbcTypeCode(SqlTypes.JSON)
            (r'@Type\s*\(\s*JsonType\.class\s*\)', '@JdbcTypeCode(SqlTypes.JSON)'),
            # @Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
            (r'@Type\s*\(\s*io\.hypersistence\.utils\.hibernate\.type\.json\.JsonType\.class\s*\)', 
             '@JdbcTypeCode(SqlTypes.JSON)'),
        ]
        
        self.security_patterns = [
            # antMatchers -> requestMatchers
            (r'\.antMatchers\s*\(', '.requestMatchers('),
            # mvcMatchers -> requestMatchers
            (r'\.mvcMatchers\s*\(', '.requestMatchers('),
        ]
    
    def find_java_files(self) -> List[Path]:
        """Find all Java files in the project"""
        java_files = []
        services_dir = self.project_root / "services"
        
        if services_dir.exists():
            for file_path in services_dir.rglob("*.java"):
                # Skip test files and generated files
                if "/test/" not in str(file_path) and "/target/" not in str(file_path):
                    java_files.append(file_path)
                    
        logger.info(f"Found {len(java_files)} Java files to process")
        return java_files
    
    def needs_hibernate_imports(self, content: str) -> bool:
        """Check if file needs Hibernate imports for @JdbcTypeCode"""
        return any(pattern[0] in content for pattern in self.type_annotation_patterns + self.type_class_patterns)
    
    def add_hibernate_imports(self, content: str) -> str:
        """Add necessary Hibernate imports if not present"""
        if self.needs_hibernate_imports(content):
            # Check if imports already exist
            has_jdbc_type_code = "import org.hibernate.annotations.JdbcTypeCode;" in content
            has_sql_types = "import org.hibernate.type.SqlTypes;" in content
            
            if not has_jdbc_type_code or not has_sql_types:
                # Find the last import statement
                import_pattern = r'(import\s+[\w\.]+;)'
                imports = list(re.finditer(import_pattern, content))
                
                if imports:
                    last_import = imports[-1]
                    insert_pos = last_import.end()
                    
                    new_imports = []
                    if not has_jdbc_type_code:
                        new_imports.append("import org.hibernate.annotations.JdbcTypeCode;")
                    if not has_sql_types:
                        new_imports.append("import org.hibernate.type.SqlTypes;")
                    
                    if new_imports:
                        import_text = "\n" + "\n".join(new_imports)
                        content = content[:insert_pos] + import_text + content[insert_pos:]
                        
        return content
    
    def remove_old_type_import(self, content: str) -> str:
        """Remove old @Type import if no longer needed"""
        # Check if @Type is still used after migration
        if not re.search(r'@Type\s*\(', content):
            # Remove the import
            content = re.sub(r'import\s+org\.hibernate\.annotations\.Type;\s*\n', '', content)
            
        return content
    
    def migrate_file(self, file_path: Path) -> Tuple[bool, List[str]]:
        """Migrate a single file"""
        logger.info(f"Processing: {file_path}")
        errors = []
        modified = False
        
        try:
            # Read file
            with open(file_path, 'r', encoding='utf-8') as f:
                original_content = f.read()
            
            content = original_content
            
            # Apply javax to jakarta migrations
            for pattern, replacement in self.javax_to_jakarta_patterns:
                new_content = re.sub(pattern, replacement, content)
                if new_content != content:
                    self.migration_stats['javax_imports_fixed'] += len(re.findall(pattern, content))
                    content = new_content
                    modified = True
            
            # Apply @Type to @JdbcTypeCode migrations
            for pattern, replacement in self.type_annotation_patterns + self.type_class_patterns:
                new_content = re.sub(pattern, replacement, content)
                if new_content != content:
                    self.migration_stats['type_annotations_fixed'] += len(re.findall(pattern, content))
                    content = new_content
                    modified = True
            
            # Add Hibernate imports if needed
            if modified and self.needs_hibernate_imports(content):
                content = self.add_hibernate_imports(content)
                content = self.remove_old_type_import(content)
            
            # Apply Spring Security migrations
            for pattern, replacement in self.security_patterns:
                new_content = re.sub(pattern, replacement, content)
                if new_content != content:
                    self.migration_stats['security_configs_fixed'] += len(re.findall(pattern, content))
                    content = new_content
                    modified = True
            
            # Write changes if modified
            if modified and not self.dry_run:
                # Backup original file
                if self.backup.backup_file(file_path):
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(content)
                    logger.info(f"Successfully migrated: {file_path}")
                else:
                    errors.append(f"Failed to backup file: {file_path}")
                    modified = False
            
            return modified, errors
            
        except Exception as e:
            error_msg = f"Error processing {file_path}: {str(e)}"
            logger.error(error_msg)
            errors.append(error_msg)
            return False, errors
    
    def validate_migration(self, file_path: Path) -> List[str]:
        """Validate migrated file for potential issues"""
        issues = []
        
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Check for remaining javax imports
            javax_pattern = r'import\s+javax\.(persistence|validation|servlet|annotation)\.'
            if re.search(javax_pattern, content):
                issues.append(f"File still contains javax imports: {file_path}")
            
            # Check for remaining @Type annotations
            if re.search(r'@Type\s*\(', content):
                issues.append(f"File still contains @Type annotations: {file_path}")
            
            # Check for duplicate imports
            imports = re.findall(r'import\s+([\w\.]+);', content)
            duplicates = [imp for imp in imports if imports.count(imp) > 1]
            if duplicates:
                issues.append(f"File contains duplicate imports: {set(duplicates)}")
                
        except Exception as e:
            issues.append(f"Validation error for {file_path}: {str(e)}")
            
        return issues
    
    def run_migration(self) -> Dict:
        """Run the full migration process"""
        logger.info(f"Starting Spring Boot 3.x migration {'(DRY RUN)' if self.dry_run else ''}")
        logger.info(f"Project root: {self.project_root}")
        
        # Find all Java files
        java_files = self.find_java_files()
        
        # Process each file
        for file_path in java_files:
            self.migration_stats['files_processed'] += 1
            
            modified, errors = self.migrate_file(file_path)
            
            if modified:
                self.migration_stats['files_modified'] += 1
                
                # Validate the migration
                if not self.dry_run:
                    issues = self.validate_migration(file_path)
                    if issues:
                        self.migration_stats['errors'].extend(issues)
                        logger.warning(f"Validation issues for {file_path}: {issues}")
            
            if errors:
                self.migration_stats['files_failed'] += 1
                self.migration_stats['errors'].extend(errors)
        
        # Save backup manifest
        if not self.dry_run:
            self.backup.save_manifest()
        
        # Generate report
        self.generate_report()
        
        return self.migration_stats
    
    def generate_report(self):
        """Generate migration report"""
        report_path = LOG_DIR / f"migration_report_{timestamp}.json"
        
        report = {
            'timestamp': datetime.now().isoformat(),
            'dry_run': self.dry_run,
            'project_root': str(self.project_root),
            'statistics': self.migration_stats,
            'backup_location': str(self.backup.backup_dir) if not self.dry_run else None
        }
        
        with open(report_path, 'w') as f:
            json.dump(report, f, indent=2)
        
        logger.info(f"\nMigration Report:")
        logger.info(f"  Files processed: {self.migration_stats['files_processed']}")
        logger.info(f"  Files modified: {self.migration_stats['files_modified']}")
        logger.info(f"  Files failed: {self.migration_stats['files_failed']}")
        logger.info(f"  javax imports fixed: {self.migration_stats['javax_imports_fixed']}")
        logger.info(f"  @Type annotations fixed: {self.migration_stats['type_annotations_fixed']}")
        logger.info(f"  Security configs fixed: {self.migration_stats['security_configs_fixed']}")
        
        if self.migration_stats['errors']:
            logger.warning(f"\nErrors encountered: {len(self.migration_stats['errors'])}")
            for error in self.migration_stats['errors'][:10]:  # Show first 10 errors
                logger.warning(f"  - {error}")
        
        logger.info(f"\nFull report saved to: {report_path}")

def main():
    parser = argparse.ArgumentParser(description='Migrate Waqiti application to Spring Boot 3.x')
    parser.add_argument('--dry-run', action='store_true', help='Run without making changes')
    parser.add_argument('--project-root', type=str, default='.', help='Project root directory')
    parser.add_argument('--rollback', type=str, help='Rollback using backup directory')
    
    args = parser.parse_args()
    
    project_root = Path(args.project_root).resolve()
    
    if args.rollback:
        # TODO: Implement rollback functionality
        logger.error("Rollback functionality not yet implemented")
        return
    
    # Run migration
    migrator = SpringBoot3Migrator(project_root, dry_run=args.dry_run)
    stats = migrator.run_migration()
    
    # Exit with error code if failures occurred
    if stats['files_failed'] > 0 or stats['errors']:
        exit(1)

if __name__ == "__main__":
    main()