# Database Migration Versioning Standard

**Effective Date:** October 31, 2025
**Status:** MANDATORY for all new migrations
**Applies To:** All Flyway/Liquibase migrations

---

## 🎯 **PROBLEM STATEMENT**

Current migration versioning is inconsistent across the codebase:
- Sequential: `V001`, `V002`, `V003`
- Numeric: `V100`, `V101`, `V102`
- Date-based: `V2025.10.23.001`, `V2025.10.26.003`
- Special: `V999`, `V1000`, `V3_00`, `V3_01`

This creates:
- ❌ Unpredictable execution order
- ❌ Merge conflicts in version numbers
- ❌ Difficulty tracking which migrations are related
- ❌ No clear temporal ordering

---

## ✅ **MANDATORY STANDARD**

### **Version Format**

```
V{YYYY.MM.DD.XXX}__{description}.sql
```

**Components:**
- `V` - Flyway version prefix (required)
- `YYYY` - 4-digit year
- `MM` - 2-digit month (01-12)
- `DD` - 2-digit day (01-31)
- `XXX` - 3-digit sequence number (001-999)
- `__` - Double underscore separator (required by Flyway)
- `description` - Lowercase with underscores, no SQL keywords

### **Examples**

✅ **CORRECT:**
```
V2025.10.31.001__add_foreign_key_constraints.sql
V2025.10.31.002__create_payment_reconciliation_table.sql
V2025.11.01.001__add_indexes_for_reporting.sql
V2025.11.01.002__add_user_email_verification.sql
```

❌ **INCORRECT:**
```
V100__some_migration.sql                    # No date
V2025_10_31_001__migration.sql              # Wrong date format
V2025.10.31.1__migration.sql                # Sequence not 3 digits
V2025.10.31.001_migration.sql               # Single underscore
V2025.10.31.001__CREATE-INDEXES.sql         # SQL keyword in name
V2025.10.31.001__Create_Indexes.sql         # Not lowercase
```

---

## 📋 **MIGRATION NAMING CONVENTIONS**

### **Description Guidelines**

1. **Use lowercase with underscores**
   - ✅ `add_user_email_index`
   - ❌ `AddUserEmailIndex`

2. **Start with action verb**
   - ✅ `add_`, `create_`, `alter_`, `drop_`, `fix_`
   - ❌ `new_table`, `table_changes`

3. **Be descriptive but concise**
   - ✅ `add_wallet_balance_constraints`
   - ❌ `migration_to_add_some_new_constraints_for_wallet_balances`

4. **Avoid SQL keywords**
   - ✅ `create_user_profile_table`
   - ❌ `CREATE_TABLE_users`

5. **Maximum 50 characters**

### **Common Prefixes**

| Prefix | Usage | Example |
|--------|-------|---------|
| `add_` | Adding columns, constraints, indexes | `add_transaction_idempotency_key.sql` |
| `create_` | Creating new tables | `create_payment_reconciliation_table.sql` |
| `alter_` | Modifying existing tables | `alter_wallet_add_reserved_balance.sql` |
| `drop_` | Removing tables, columns, indexes | `drop_deprecated_payment_methods_table.sql` |
| `fix_` | Bug fixes in schema | `fix_duplicate_user_indexes.sql` |
| `migrate_` | Data migrations | `migrate_legacy_transactions_to_partitioned.sql` |
| `partition_` | Partitioning changes | `partition_audit_events_by_week.sql` |
| `index_` | Index-only changes | `index_optimize_transaction_queries.sql` |
| `constraint_` | Constraint-only changes | `constraint_add_payment_amount_validation.sql` |
| `security_` | Security-related changes | `security_add_rls_policies_to_wallets.sql` |

---

## 🔄 **MIGRATION TYPES**

### **1. Schema Migrations (DDL)**

Structural changes to database schema.

**Examples:**
```sql
-- V2025.10.31.001__create_payment_reconciliation_table.sql
CREATE TABLE payment_reconciliation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    reconciled_at TIMESTAMP,
    ...
);

-- V2025.10.31.002__add_wallet_reserved_balance.sql
ALTER TABLE wallets
ADD COLUMN reserved_balance DECIMAL(20,2) NOT NULL DEFAULT 0.00;
```

### **2. Data Migrations (DML)**

Changes to existing data.

**Examples:**
```sql
-- V2025.10.31.003__migrate_old_transaction_format.sql
UPDATE transactions
SET new_status = CASE old_status
    WHEN 'PENDING' THEN 'IN_PROGRESS'
    WHEN 'DONE' THEN 'COMPLETED'
    ELSE old_status
END;

-- V2025.10.31.004__populate_user_full_names.sql
UPDATE users
SET full_name = first_name || ' ' || last_name
WHERE full_name IS NULL;
```

### **3. Index Migrations**

Index creation/modification.

**Examples:**
```sql
-- V2025.10.31.005__index_transactions_for_reporting.sql
CREATE INDEX CONCURRENTLY idx_transactions_user_status_date
    ON transactions(user_id, status, created_at DESC)
    WHERE status IN ('COMPLETED', 'FAILED');
```

### **4. Constraint Migrations**

Adding/removing constraints.

**Examples:**
```sql
-- V2025.10.31.006__constraint_wallet_balance_non_negative.sql
ALTER TABLE wallets
ADD CONSTRAINT chk_wallet_balance_non_negative
CHECK (balance >= 0);
```

### **5. Partition Migrations**

Partition management.

**Examples:**
```sql
-- V2025.10.31.007__partition_create_november_transactions.sql
CREATE TABLE transactions_y2025m11 PARTITION OF transactions_partitioned
FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
```

---

## 📝 **MIGRATION FILE STRUCTURE**

### **Standard Template**

```sql
-- ============================================================================
-- Migration: {Brief Description}
-- Version: V{YYYY.MM.DD.XXX}
-- Author: {Team/Person}
-- Date: {YYYY-MM-DD}
-- ============================================================================
--
-- PURPOSE:
-- {1-2 sentences describing what this migration does and why}
--
-- CHANGES:
-- - {List of changes}
-- - {Another change}
--
-- DEPENDENCIES:
-- - Requires: V{YYYY.MM.DD.XXX}__{previous_migration} (if any)
-- - Affects: {tables/services affected}
--
-- ROLLBACK:
-- See: down/V{YYYY.MM.DD.XXX}__{description}.sql
--
-- PERFORMANCE IMPACT:
-- - Estimated duration: {time estimate}
-- - Locks acquired: {table locks, if any}
-- - Downtime required: {YES/NO}
--
-- TESTING:
-- - Unit tested: {YES/NO}
-- - Integration tested: {YES/NO}
-- - Staging validated: {YES/NO}
--
-- ============================================================================

-- Migration starts here
BEGIN;

-- Set transaction characteristics
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '300s';

-- Your migration SQL here
CREATE TABLE example (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Validation query (optional)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_name = 'example') THEN
        RAISE EXCEPTION 'Migration validation failed: example table not created';
    END IF;
END $$;

-- Record migration execution
INSERT INTO schema_migrations_audit (
    migration_version,
    migration_name,
    executed_at,
    applied_by
) VALUES (
    'V{YYYY.MM.DD.XXX}',
    '{description}',
    CURRENT_TIMESTAMP,
    CURRENT_USER
);

COMMIT;

-- Migration complete
COMMENT ON TABLE example IS 'Created by V{YYYY.MM.DD.XXX} - {purpose}';
```

---

## 🔙 **ROLLBACK MIGRATIONS**

### **Mandatory Down Migrations**

Every UP migration MUST have a corresponding DOWN migration.

**Directory Structure:**
```
database/migrations/
├── V2025.10.31.001__add_foreign_key_constraints.sql          # UP
├── down/
│   └── V2025.10.31.001__add_foreign_key_constraints.sql      # DOWN
```

**Down Migration Template:**
```sql
-- ============================================================================
-- ROLLBACK Migration: {Brief Description}
-- Version: V{YYYY.MM.DD.XXX}
-- Rollback For: V{YYYY.MM.DD.XXX}__{description}.sql
-- ============================================================================
--
-- PURPOSE:
-- Rolls back changes from V{YYYY.MM.DD.XXX}__{description}.sql
--
-- WARNING:
-- {Any data loss warnings}
--
-- ============================================================================

BEGIN;

-- Reverse the changes in opposite order
DROP TABLE IF EXISTS example CASCADE;

-- Remove from audit
DELETE FROM schema_migrations_audit
WHERE migration_version = 'V{YYYY.MM.DD.XXX}';

COMMIT;
```

---

## 🔢 **SEQUENCE NUMBER ALLOCATION**

### **Per-Day Allocation**

Each day gets 999 possible migrations (001-999).

**Allocation Strategy:**

| Sequence Range | Purpose | Example |
|----------------|---------|---------|
| 001-099 | Schema changes (DDL) | Create/alter/drop tables |
| 100-199 | Index changes | Creating/dropping indexes |
| 200-299 | Constraint changes | Adding/removing constraints |
| 300-399 | Data migrations (DML) | Bulk updates, corrections |
| 400-499 | Partitioning | Partition creation/management |
| 500-599 | Security changes | RLS policies, grants |
| 600-699 | Performance optimization | Query optimization, statistics |
| 700-799 | Emergency fixes | Critical production fixes |
| 800-899 | Cleanup/maintenance | Dropping deprecated tables |
| 900-999 | Reserved | Future use |

### **Conflict Resolution**

If multiple developers create migrations on same day:

1. **During Development:**
   - Use higher sequence numbers to avoid conflicts
   - Coordinate via team chat/standup

2. **During Merge:**
   - First merged migration keeps its number
   - Later migrations renumber to next available

3. **Post-Merge:**
   - Run `validate-migration-order.sh` script
   - Fix any ordering issues before deployment

---

## 🛠️ **MIGRATION BEST PRACTICES**

### **1. Idempotency**

Migrations should be safe to run multiple times.

✅ **GOOD:**
```sql
CREATE TABLE IF NOT EXISTS users (...);

ALTER TABLE wallets
ADD COLUMN IF NOT EXISTS reserved_balance DECIMAL(20,2);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_user
ON transactions(user_id);
```

❌ **BAD:**
```sql
CREATE TABLE users (...);  -- Fails if table exists
ALTER TABLE wallets ADD COLUMN reserved_balance DECIMAL(20,2);  -- Fails if column exists
```

### **2. Backward Compatibility**

New migrations should not break existing application code.

✅ **GOOD:**
```sql
-- Add new column with default value (safe)
ALTER TABLE users
ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;

-- Add new optional column (safe)
ALTER TABLE payments
ADD COLUMN external_reference VARCHAR(255);
```

❌ **BAD:**
```sql
-- Rename column (breaks existing queries)
ALTER TABLE users RENAME COLUMN email TO email_address;

-- Drop column (breaks existing code)
ALTER TABLE payments DROP COLUMN merchant_id;
```

### **3. Performance Considerations**

**Use CONCURRENTLY for indexes:**
```sql
CREATE INDEX CONCURRENTLY idx_transactions_date
ON transactions(transaction_date);
```

**Set lock timeouts:**
```sql
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '300s';
```

**Batch large data migrations:**
```sql
-- Process in batches to avoid long locks
DO $$
DECLARE
    batch_size INTEGER := 1000;
BEGIN
    LOOP
        UPDATE users SET status = 'ACTIVE'
        WHERE id IN (
            SELECT id FROM users
            WHERE status = 'PENDING'
            LIMIT batch_size
        );

        EXIT WHEN NOT FOUND;
        COMMIT;
    END LOOP;
END $$;
```

### **4. Testing Requirements**

Before deploying a migration:

- [ ] Tested on local database
- [ ] Tested on staging database
- [ ] Rollback tested
- [ ] Performance impact assessed
- [ ] Lock duration measured
- [ ] Backup verified
- [ ] Runbook created for production deployment

---

## 📊 **MIGRATION AUDIT TABLE**

All migrations must log execution to audit table:

```sql
CREATE TABLE IF NOT EXISTS schema_migrations_audit (
    id BIGSERIAL PRIMARY KEY,
    migration_version VARCHAR(50) NOT NULL,
    migration_name VARCHAR(255) NOT NULL,
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time_ms INTEGER,
    applied_by VARCHAR(100) NOT NULL,
    rollback_version VARCHAR(50),
    rolled_back_at TIMESTAMP,
    notes TEXT
);

CREATE INDEX idx_migrations_audit_version
ON schema_migrations_audit(migration_version);

CREATE INDEX idx_migrations_audit_executed
ON schema_migrations_audit(executed_at DESC);
```

---

## 🚨 **EMERGENCY MIGRATION PROCEDURE**

For critical production fixes:

1. **Use 700-799 sequence range**
2. **Prefix with `EMERGENCY_`**
3. **Include ticket number in description**

**Example:**
```
V2025.10.31.701__EMERGENCY_fix_transaction_reconciliation_INC12345.sql
```

4. **Follow expedited approval process**
5. **Execute during maintenance window if possible**
6. **Have rollback plan ready**

---

## ✅ **VALIDATION SCRIPT**

Run before committing migrations:

```bash
#!/bin/bash
# validate-migrations.sh

# Check file naming
for file in database/migrations/V*.sql; do
    if [[ ! $file =~ V[0-9]{4}\.[0-9]{2}\.[0-9]{2}\.[0-9]{3}__[a-z_]+\.sql ]]; then
        echo "ERROR: Invalid migration name: $file"
        exit 1
    fi
done

# Check for corresponding down migration
for file in database/migrations/V*.sql; do
    down_file="database/migrations/down/$(basename $file)"
    if [[ ! -f $down_file ]]; then
        echo "WARNING: Missing down migration for: $file"
    fi
done

echo "Migration validation passed"
```

---

## 📚 **REFERENCES**

- Flyway Documentation: https://flywaydb.org/documentation/
- PostgreSQL Versioning Best Practices
- Internal: `database/scripts/migration-tools/`

---

**Document Owner:** Database Team
**Last Updated:** October 31, 2025
**Review Frequency:** Quarterly
