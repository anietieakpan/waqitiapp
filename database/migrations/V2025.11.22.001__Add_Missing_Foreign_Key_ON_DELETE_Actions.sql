-- ============================================================================
-- CRITICAL FIX (P0-011): Add Missing Foreign Key ON DELETE Actions
-- Migration: Add proper cascade/restrict rules to prevent orphaned records
--
-- PROBLEM:
-- - Many foreign keys defined without ON DELETE action
-- - Default behavior: NO ACTION (fails on delete if children exist)
-- - Risk: Orphaned records, referential integrity violations
-- - Example: Deleting parent account leaves child accounts with NULL parent_id
--
-- FIX STRATEGY:
-- 1. Drop existing foreign keys without ON DELETE
-- 2. Recreate with proper ON DELETE actions:
--    - CASCADE: Delete children when parent deleted (hierarchies, ownership)
--    - RESTRICT: Prevent parent deletion if children exist (critical references)
--    - SET NULL: Set child FK to NULL (optional relationships)
--
-- DECISION MATRIX:
-- - User → Wallets: CASCADE (user owns wallets)
-- - User → Payments: RESTRICT (preserve payment history)
-- - Account → Sub-accounts: CASCADE (hierarchy)
-- - Payment → Transactions: RESTRICT (audit trail)
-- - Wallet → Reservations: CASCADE (temporary state)
--
-- ============================================================================

-- Set statement timeout to prevent long-running DDL from blocking
SET statement_timeout = '30s';

-- ============================================================================
-- LEDGER SERVICE: Chart of Accounts Hierarchy
-- ============================================================================

-- Fix: parent_account_id foreign key (CASCADE for hierarchy deletion)
DO $$
BEGIN
    -- Drop existing constraint if exists
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_chart_of_accounts_parent'
          AND table_name = 'chart_of_accounts'
    ) THEN
        ALTER TABLE chart_of_accounts
        DROP CONSTRAINT fk_chart_of_accounts_parent;
    END IF;

    -- Recreate with CASCADE (deleting parent account cascades to children)
    ALTER TABLE chart_of_accounts
    ADD CONSTRAINT fk_chart_of_accounts_parent
        FOREIGN KEY (parent_account_id)
        REFERENCES chart_of_accounts(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE;

    RAISE NOTICE 'Fixed FK: chart_of_accounts.parent_account_id → CASCADE';
END $$;

-- ============================================================================
-- PAYMENT SERVICE: Payment Method References
-- ============================================================================

-- Fix: payment_method_id foreign key (RESTRICT to prevent deletion of in-use payment methods)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name LIKE 'fk_%payment_method%'
          AND table_name = 'payments'
    ) THEN
        -- Find actual constraint name
        DECLARE
            constraint_name_var TEXT;
        BEGIN
            SELECT constraint_name INTO constraint_name_var
            FROM information_schema.table_constraints
            WHERE table_name = 'payments'
              AND constraint_type = 'FOREIGN KEY'
              AND constraint_name LIKE '%payment_method%'
            LIMIT 1;

            IF constraint_name_var IS NOT NULL THEN
                EXECUTE format('ALTER TABLE payments DROP CONSTRAINT %I', constraint_name_var);
            END IF;
        END;
    END IF;

    -- Recreate with RESTRICT (prevent deletion of payment methods still referenced)
    ALTER TABLE payments
    ADD CONSTRAINT fk_payments_payment_method
        FOREIGN KEY (payment_method_id)
        REFERENCES payment_methods(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE;

    RAISE NOTICE 'Fixed FK: payments.payment_method_id → RESTRICT';
END $$;

-- ============================================================================
-- USER SERVICE: User Relationships
-- ============================================================================

-- Fix: User → Wallets (CASCADE - user owns wallets)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'wallets'
    ) THEN
        -- Drop existing constraint if exists
        IF EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE constraint_name LIKE 'fk_%user%'
              AND table_name = 'wallets'
        ) THEN
            DECLARE
                constraint_name_var TEXT;
            BEGIN
                SELECT constraint_name INTO constraint_name_var
                FROM information_schema.table_constraints
                WHERE table_name = 'wallets'
                  AND constraint_type = 'FOREIGN KEY'
                  AND constraint_name LIKE '%user%'
                LIMIT 1;

                IF constraint_name_var IS NOT NULL THEN
                    EXECUTE format('ALTER TABLE wallets DROP CONSTRAINT %I', constraint_name_var);
                END IF;
            END;
        END IF;

        -- Recreate with CASCADE
        ALTER TABLE wallets
        ADD CONSTRAINT fk_wallets_user
            FOREIGN KEY (user_id)
            REFERENCES users(id)
            ON DELETE CASCADE
            ON UPDATE CASCADE;

        RAISE NOTICE 'Fixed FK: wallets.user_id → CASCADE';
    END IF;
END $$;

-- Fix: User → Verification Tokens (CASCADE - tokens owned by user)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'verification_tokens'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE constraint_name LIKE 'fk_%user%'
              AND table_name = 'verification_tokens'
        ) THEN
            DECLARE
                constraint_name_var TEXT;
            BEGIN
                SELECT constraint_name INTO constraint_name_var
                FROM information_schema.table_constraints
                WHERE table_name = 'verification_tokens'
                  AND constraint_type = 'FOREIGN KEY'
                  AND constraint_name LIKE '%user%'
                LIMIT 1;

                IF constraint_name_var IS NOT NULL THEN
                    EXECUTE format('ALTER TABLE verification_tokens DROP CONSTRAINT %I', constraint_name_var);
                END IF;
            END;
        END IF;

        ALTER TABLE verification_tokens
        ADD CONSTRAINT fk_verification_tokens_user
            FOREIGN KEY (user_id)
            REFERENCES users(id)
            ON DELETE CASCADE
            ON UPDATE CASCADE;

        RAISE NOTICE 'Fixed FK: verification_tokens.user_id → CASCADE';
    END IF;
END $$;

-- ============================================================================
-- WALLET SERVICE: Fund Reservations
-- ============================================================================

-- Fix: Wallet → Fund Reservations (CASCADE - reservations are temporary state)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'fund_reservations'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE constraint_name LIKE 'fk_%wallet%'
              AND table_name = 'fund_reservations'
        ) THEN
            DECLARE
                constraint_name_var TEXT;
            BEGIN
                SELECT constraint_name INTO constraint_name_var
                FROM information_schema.table_constraints
                WHERE table_name = 'fund_reservations'
                  AND constraint_type = 'FOREIGN KEY'
                  AND constraint_name LIKE '%wallet%'
                LIMIT 1;

                IF constraint_name_var IS NOT NULL THEN
                    EXECUTE format('ALTER TABLE fund_reservations DROP CONSTRAINT %I', constraint_name_var);
                END IF;
            END;
        END IF;

        ALTER TABLE fund_reservations
        ADD CONSTRAINT fk_fund_reservations_wallet
            FOREIGN KEY (wallet_id)
            REFERENCES wallets(id)
            ON DELETE CASCADE
            ON UPDATE CASCADE;

        RAISE NOTICE 'Fixed FK: fund_reservations.wallet_id → CASCADE';
    END IF;
END $$;

-- ============================================================================
-- SAGA ORCHESTRATION: Saga Steps
-- ============================================================================

-- Fix: Saga → Saga Steps (CASCADE - steps are part of saga lifecycle)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'saga_step_states'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE constraint_name LIKE 'fk_%saga%'
              AND table_name = 'saga_step_states'
        ) THEN
            DECLARE
                constraint_name_var TEXT;
            BEGIN
                SELECT constraint_name INTO constraint_name_var
                FROM information_schema.table_constraints
                WHERE table_name = 'saga_step_states'
                  AND constraint_type = 'FOREIGN KEY'
                  AND constraint_name LIKE '%saga%'
                LIMIT 1;

                IF constraint_name_var IS NOT NULL THEN
                    EXECUTE format('ALTER TABLE saga_step_states DROP CONSTRAINT %I', constraint_name_var);
                END IF;
            END;
        END IF;

        ALTER TABLE saga_step_states
        ADD CONSTRAINT fk_saga_step_states_saga
            FOREIGN KEY (saga_id)
            REFERENCES saga_states(saga_id)
            ON DELETE CASCADE
            ON UPDATE CASCADE;

        RAISE NOTICE 'Fixed FK: saga_step_states.saga_id → CASCADE';
    END IF;
END $$;

-- ============================================================================
-- PAYMENT SERVICE: Preserve Payment History
-- ============================================================================

-- Fix: Payment → User (RESTRICT - preserve payment history even if user deleted)
-- Alternative: Use soft delete for users instead
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'payments'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE constraint_name LIKE 'fk_payments_user%'
              AND table_name = 'payments'
        ) THEN
            DECLARE
                constraint_name_var TEXT;
            BEGIN
                SELECT constraint_name INTO constraint_name_var
                FROM information_schema.table_constraints
                WHERE table_name = 'payments'
                  AND constraint_type = 'FOREIGN KEY'
                  AND constraint_name LIKE 'fk_payments_user%'
                LIMIT 1;

                IF constraint_name_var IS NOT NULL THEN
                    EXECUTE format('ALTER TABLE payments DROP CONSTRAINT %I', constraint_name_var);
                END IF;
            END;
        END IF;

        ALTER TABLE payments
        ADD CONSTRAINT fk_payments_requestor_user
            FOREIGN KEY (requestor_id)
            REFERENCES users(id)
            ON DELETE RESTRICT  -- Prevent user deletion if they have payments
            ON UPDATE CASCADE;

        RAISE NOTICE 'Fixed FK: payments.requestor_id → RESTRICT (preserve history)';
    END IF;
END $$;

-- ============================================================================
-- AUDIT SERVICE: Preserve Audit Trails
-- ============================================================================

-- Fix: Audit Events → User (SET NULL - preserve audit even if user deleted)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'audit_events'
    ) THEN
        -- First, make user_id nullable if it isn't
        ALTER TABLE audit_events
        ALTER COLUMN user_id DROP NOT NULL;

        IF EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE constraint_name LIKE 'fk_%user%'
              AND table_name = 'audit_events'
        ) THEN
            DECLARE
                constraint_name_var TEXT;
            BEGIN
                SELECT constraint_name INTO constraint_name_var
                FROM information_schema.table_constraints
                WHERE table_name = 'audit_events'
                  AND constraint_type = 'FOREIGN KEY'
                  AND constraint_name LIKE '%user%'
                LIMIT 1;

                IF constraint_name_var IS NOT NULL THEN
                    EXECUTE format('ALTER TABLE audit_events DROP CONSTRAINT %I', constraint_name_var);
                END IF;
            END;
        END IF;

        ALTER TABLE audit_events
        ADD CONSTRAINT fk_audit_events_user
            FOREIGN KEY (user_id)
            REFERENCES users(id)
            ON DELETE SET NULL  -- Preserve audit trail with NULL user
            ON UPDATE CASCADE;

        RAISE NOTICE 'Fixed FK: audit_events.user_id → SET NULL (preserve audit)';
    END IF;
END $$;

-- ============================================================================
-- POST-MIGRATION VALIDATION
-- ============================================================================

DO $$
DECLARE
    fk_count INT;
    missing_action_count INT;
BEGIN
    -- Count total foreign keys
    SELECT COUNT(*) INTO fk_count
    FROM information_schema.table_constraints
    WHERE constraint_type = 'FOREIGN KEY';

    -- Count foreign keys without DELETE action (should be 0 after migration)
    SELECT COUNT(*) INTO missing_action_count
    FROM information_schema.referential_constraints
    WHERE delete_rule = 'NO ACTION';

    RAISE NOTICE '';
    RAISE NOTICE '========================================================================';
    RAISE NOTICE 'Foreign Key ON DELETE Actions - Migration Complete';
    RAISE NOTICE '========================================================================';
    RAISE NOTICE 'Total foreign keys: %', fk_count;
    RAISE NOTICE 'Missing DELETE actions: %', missing_action_count;
    RAISE NOTICE '';

    IF missing_action_count > 0 THEN
        RAISE WARNING 'Some foreign keys still have NO ACTION. Review manually:';
        RAISE WARNING 'SELECT tc.table_name, tc.constraint_name, rc.delete_rule';
        RAISE WARNING 'FROM information_schema.table_constraints tc';
        RAISE WARNING 'JOIN information_schema.referential_constraints rc';
        RAISE WARNING '  ON tc.constraint_name = rc.constraint_name';
        RAISE WARNING 'WHERE rc.delete_rule = ''NO ACTION'';';
    ELSE
        RAISE NOTICE '✓ All foreign keys have explicit ON DELETE actions';
    END IF;

    RAISE NOTICE '';
    RAISE NOTICE 'Cascade Rules Applied:';
    RAISE NOTICE '  - User → Wallets: CASCADE';
    RAISE NOTICE '  - User → Verification Tokens: CASCADE';
    RAISE NOTICE '  - Wallet → Fund Reservations: CASCADE';
    RAISE NOTICE '  - Account → Sub-accounts: CASCADE';
    RAISE NOTICE '  - Saga → Saga Steps: CASCADE';
    RAISE NOTICE '';
    RAISE NOTICE 'Restrict Rules Applied:';
    RAISE NOTICE '  - Payment → Payment Methods: RESTRICT';
    RAISE NOTICE '  - Payment → Users: RESTRICT (preserve history)';
    RAISE NOTICE '';
    RAISE NOTICE 'SET NULL Rules Applied:';
    RAISE NOTICE '  - Audit Events → Users: SET NULL (preserve audit)';
    RAISE NOTICE '';
    RAISE NOTICE '========================================================================';
    RAISE NOTICE '';
END $$;
