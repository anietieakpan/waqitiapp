#!/usr/bin/env python3
"""
Script to fix deprecated prometheus configuration in Spring Boot 3.x
Changes management.metrics.export.prometheus to management.prometheus.metrics.export
"""

import os
import re
import sys
from pathlib import Path

def fix_prometheus_config(file_path):
    """Fix prometheus configuration in a single file."""
    with open(file_path, 'r') as f:
        content = f.read()
    
    # Pattern to match the deprecated configuration
    # This pattern handles the full block structure
    pattern = r'(management:\s*\n(?:.*\n)*?)(\s+)metrics:\s*\n\s+export:\s*\n\s+prometheus:\s*\n(\s+enabled:\s*(?:true|false))'
    
    # Check if pattern exists
    if not re.search(pattern, content):
        return False
    
    # Replace with the new structure
    def replacement(match):
        prefix = match.group(1)
        indent = match.group(2)
        enabled_line = match.group(3)
        return f"{prefix}{indent}prometheus:\n{indent}  metrics:\n{indent}    export:\n{enabled_line}"
    
    new_content = re.sub(pattern, replacement, content)
    
    # Write back the file
    with open(file_path, 'w') as f:
        f.write(new_content)
    
    return True

def main():
    # Find all yml files
    yml_files = []
    for root, dirs, files in os.walk('.'):
        # Skip hidden directories and node_modules
        dirs[:] = [d for d in dirs if not d.startswith('.') and d != 'node_modules']
        
        for file in files:
            if file.endswith('.yml') or file.endswith('.yaml'):
                yml_files.append(os.path.join(root, file))
    
    print(f"Found {len(yml_files)} YAML files to check...")
    
    fixed_count = 0
    for file_path in yml_files:
        try:
            if fix_prometheus_config(file_path):
                print(f"Fixed: {file_path}")
                fixed_count += 1
        except Exception as e:
            print(f"Error processing {file_path}: {e}", file=sys.stderr)
    
    print(f"\nDone! Fixed {fixed_count} files.")

if __name__ == "__main__":
    main()