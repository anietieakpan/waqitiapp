#!/usr/bin/env python3
"""
Efficient batch migration script for Spring Boot 3 javax -> jakarta conversion
Critical fix for production compilation issues
"""

import os
import re
import subprocess
import sys
from pathlib import Path

def migrate_javax_to_jakarta():
    """Migrate all javax imports to jakarta in Java files"""
    
    # Mapping of javax packages to jakarta equivalents
    mappings = {
        'javax.persistence': 'jakarta.persistence',
        'javax.validation': 'jakarta.validation', 
        'javax.servlet': 'jakarta.servlet',
        'javax.transaction': 'jakarta.transaction',
        'javax.annotation': 'jakarta.annotation',
        'javax.inject': 'jakarta.inject',
        'javax.enterprise': 'jakarta.enterprise',
        'javax.interceptor': 'jakarta.interceptor',
        'javax.decorator': 'jakarta.decorator',
        'javax.security': 'jakarta.security',
        'javax.ws.rs': 'jakarta.ws.rs',
        'javax.xml.bind': 'jakarta.xml.bind',
        'javax.jms': 'jakarta.jms',
        'javax.mail': 'jakarta.mail',
        'javax.json': 'jakarta.json',
        'javax.jsonb': 'jakarta.jsonb',
        'javax.faces': 'jakarta.faces',
        'javax.websocket': 'jakarta.websocket',
        'javax.ejb': 'jakarta.ejb',
        'javax.batch': 'jakarta.batch'
    }
    
    # Use sed commands for efficient bulk replacement
    for javax_pkg, jakarta_pkg in mappings.items():
        try:
            # Replace import statements
            cmd = f"find . -name '*.java' -type f -exec sed -i 's/import {javax_pkg.replace('.', '\\.')}/import {jakarta_pkg}/g' {{}} +"
            subprocess.run(cmd, shell=True, check=True)
            
            # Replace static imports  
            cmd = f"find . -name '*.java' -type f -exec sed -i 's/import static {javax_pkg.replace('.', '\\.')}/import static {jakarta_pkg}/g' {{}} +"
            subprocess.run(cmd, shell=True, check=True)
            
            print(f"✅ Migrated {javax_pkg} -> {jakarta_pkg}")
            
        except subprocess.CalledProcessError as e:
            print(f"❌ Error migrating {javax_pkg}: {e}")
    
    # Verify migration
    try:
        result = subprocess.run(
            "find . -name '*.java' -exec grep -l 'import javax\\.' {} \\; | wc -l",
            shell=True, capture_output=True, text=True
        )
        remaining = int(result.stdout.strip())
        
        if remaining == 0:
            print("✅ Migration complete - no javax imports remain")
        else:
            print(f"⚠️ {remaining} files still contain javax imports")
            
    except Exception as e:
        print(f"Error verifying migration: {e}")

if __name__ == "__main__":
    print("🔧 Starting efficient javax -> jakarta migration")
    migrate_javax_to_jakarta()
    print("✨ Migration script completed")