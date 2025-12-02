#!/usr/bin/env python3
"""
Rollback script for Spring Boot 3.x migration
Restores files from backup created during migration
"""

import os
import json
import shutil
import logging
import argparse
from pathlib import Path
from datetime import datetime

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class MigrationRollback:
    """Handles rollback of migration changes"""
    
    def __init__(self, backup_dir: Path):
        self.backup_dir = backup_dir
        self.manifest_path = backup_dir / "backup_manifest.json"
        self.rollback_stats = {
            'files_restored': 0,
            'files_failed': 0,
            'errors': []
        }
    
    def load_manifest(self) -> Dict:
        """Load backup manifest"""
        try:
            with open(self.manifest_path, 'r') as f:
                return json.load(f)
        except Exception as e:
            logger.error(f"Failed to load backup manifest: {e}")
            return {}
    
    def rollback_file(self, original_path: str, backup_info: Dict) -> bool:
        """Rollback a single file"""
        try:
            backup_path = Path(backup_info['backup_path'])
            original_file = Path(original_path)
            
            if not backup_path.exists():
                logger.error(f"Backup file not found: {backup_path}")
                return False
            
            # Create parent directory if needed
            original_file.parent.mkdir(parents=True, exist_ok=True)
            
            # Copy backup to original location
            shutil.copy2(backup_path, original_file)
            logger.info(f"Restored: {original_file}")
            
            return True
            
        except Exception as e:
            logger.error(f"Failed to restore {original_path}: {e}")
            return False
    
    def run_rollback(self) -> Dict:
        """Execute the rollback process"""
        logger.info(f"Starting rollback from: {self.backup_dir}")
        
        if not self.backup_dir.exists():
            logger.error(f"Backup directory not found: {self.backup_dir}")
            return self.rollback_stats
        
        # Load manifest
        manifest = self.load_manifest()
        if not manifest:
            logger.error("No backup manifest found or manifest is empty")
            return self.rollback_stats
        
        logger.info(f"Found {len(manifest)} files to restore")
        
        # Restore each file
        for original_path, backup_info in manifest.items():
            if self.rollback_file(original_path, backup_info):
                self.rollback_stats['files_restored'] += 1
            else:
                self.rollback_stats['files_failed'] += 1
                self.rollback_stats['errors'].append(f"Failed to restore: {original_path}")
        
        # Generate report
        self.generate_report()
        
        return self.rollback_stats
    
    def generate_report(self):
        """Generate rollback report"""
        logger.info(f"\nRollback Report:")
        logger.info(f"  Files restored: {self.rollback_stats['files_restored']}")
        logger.info(f"  Files failed: {self.rollback_stats['files_failed']}")
        
        if self.rollback_stats['errors']:
            logger.warning(f"\nErrors encountered: {len(self.rollback_stats['errors'])}")
            for error in self.rollback_stats['errors'][:10]:
                logger.warning(f"  - {error}")

def list_available_backups(base_dir: Path = Path("backups")) -> List[Path]:
    """List available backup directories"""
    backups = []
    
    if base_dir.exists():
        for item in base_dir.iterdir():
            if item.is_dir() and item.name.startswith("migration_"):
                manifest_path = item / "backup_manifest.json"
                if manifest_path.exists():
                    backups.append(item)
    
    return sorted(backups, reverse=True)  # Most recent first

def main():
    parser = argparse.ArgumentParser(description='Rollback Spring Boot 3.x migration')
    parser.add_argument('--backup-dir', type=str, help='Specific backup directory to use')
    parser.add_argument('--list', action='store_true', help='List available backups')
    
    args = parser.parse_args()
    
    if args.list:
        backups = list_available_backups()
        if backups:
            print("\nAvailable backups:")
            for backup in backups:
                print(f"  - {backup}")
        else:
            print("No backups found")
        return
    
    if args.backup_dir:
        backup_dir = Path(args.backup_dir)
    else:
        # Use most recent backup
        backups = list_available_backups()
        if not backups:
            logger.error("No backups found")
            return
        backup_dir = backups[0]
        logger.info(f"Using most recent backup: {backup_dir}")
    
    # Run rollback
    rollback = MigrationRollback(backup_dir)
    stats = rollback.run_rollback()
    
    # Exit with error code if failures occurred
    if stats['files_failed'] > 0:
        exit(1)

if __name__ == "__main__":
    main()