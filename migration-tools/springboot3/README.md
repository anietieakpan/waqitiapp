# Spring Boot 3.x Migration Tools

This directory contains robust migration scripts to upgrade the Waqiti application from Spring Boot 2.x to Spring Boot 3.x.

## Overview

The migration handles:
- **javax to jakarta namespace changes** (persistence, validation, servlet, annotation, etc.)
- **Hibernate @Type to @JdbcTypeCode migration** for JSON columns
- **Spring Security deprecated method updates** (antMatchers to requestMatchers)
- **Comprehensive backup and rollback support**
- **Detailed logging and error reporting**

## Scripts

### 1. `migrate-to-springboot3.py`
Main migration script with:
- Automatic backup of all modified files
- Dry-run mode for testing
- Comprehensive error handling
- Detailed migration statistics
- Validation of migrated files

### 2. `rollback-migration.py`
Rollback script to restore files from backup:
- Lists available backups
- Restores files to pre-migration state
- Verifies restoration success

### 3. `verify-migration.py`
Verification script to check migration results:
- Detects remaining javax imports
- Finds unmigrated @Type annotations
- Identifies duplicate imports
- Checks for missing imports
- Reports deprecated Spring Security methods

### 4. `run-migration.sh`
Shell script to execute the full migration process safely

## Usage

### Step 1: Dry Run (Recommended)
First, test the migration without making changes:
```bash
./run-migration.sh --dry-run
```

Review the output and migration report in `migration-logs/`.

### Step 2: Run Migration
If the dry run looks good:
```bash
./run-migration.sh
```

This will:
1. Create backups of all files
2. Apply migration changes
3. Verify the results
4. Generate detailed reports

### Step 3: Verify Results
Check the migration was successful:
```bash
python3 verify-migration.py
```

### Step 4: Test Your Application
After migration:
1. Run `mvn clean compile` to check for compilation errors
2. Run your test suite
3. Start the application and verify functionality

### Rollback (if needed)
If issues arise, rollback to the backup:
```bash
python3 rollback-migration.py
```

Or rollback a specific backup:
```bash
python3 rollback-migration.py --backup-dir backups/migration_20240101_120000
```

## Migration Details

### javax to jakarta Changes
- `javax.persistence.*` → `jakarta.persistence.*`
- `javax.validation.*` → `jakarta.validation.*`
- `javax.servlet.*` → `jakarta.servlet.*`
- `javax.annotation.*` → `jakarta.annotation.*`
- `javax.transaction.*` → `jakarta.transaction.*`

### Hibernate Changes
- `@Type(type = "jsonb")` → `@JdbcTypeCode(SqlTypes.JSON)`
- `@Type(JsonType.class)` → `@JdbcTypeCode(SqlTypes.JSON)`
- Adds required imports automatically

### Spring Security Changes
- `.antMatchers()` → `.requestMatchers()`
- `.mvcMatchers()` → `.requestMatchers()`
- `.authorizeRequests()` → `.authorizeHttpRequests()`

## Reports and Logs

All migration activities are logged to `migration-logs/`:
- `migration_TIMESTAMP.log` - Detailed migration log
- `migration_report_TIMESTAMP.json` - Migration statistics
- `verification_report_TIMESTAMP.json` - Verification results

## Backup Structure

Backups are stored in `backups/migration_TIMESTAMP/`:
- Preserves full directory structure
- Includes backup manifest with checksums
- Enables selective file restoration

## Troubleshooting

### Common Issues

1. **Compilation errors after migration**
   - Run verification script to find remaining issues
   - Check for custom javax imports not covered by migration

2. **Missing imports**
   - The script adds Hibernate imports automatically
   - Manually add any other missing imports

3. **Runtime errors**
   - Check for behavioral changes in Spring Boot 3.x
   - Review Spring Boot 3.0 migration guide

### Manual Fixes

Some issues may require manual intervention:
- Custom javax implementations
- Complex Spring Security configurations
- Third-party library compatibility

## Safety Features

1. **Automatic Backups**: All files backed up before modification
2. **Validation**: Each migrated file is validated
3. **Atomic Operations**: Files are only modified if backup succeeds
4. **Detailed Logging**: Every action is logged
5. **Dry Run Mode**: Test migrations without changes

## Requirements

- Python 3.8+
- Read/write access to project files
- Sufficient disk space for backups

## Support

For issues or questions:
1. Check the migration logs for detailed error messages
2. Run the verification script to identify specific problems
3. Use rollback if needed and investigate the issues
4. Consider manual fixes for edge cases