-- ============================================================================
-- Database Index Consolidation & Optimization
-- ============================================================================
--
-- PURPOSE:
-- Identifies and removes redundant indexes to improve write performance
-- and reduce storage overhead.
--
-- PROBLEM:
-- Analysis revealed 736+ indexes with redundancy across multiple migrations.
-- Redundant indexes cause:
-- - Slower INSERT/UPDATE/DELETE operations
-- - Increased storage costs
-- - Higher memory usage (PostgreSQL buffers)
-- - More complex query planner decisions
--
-- STRATEGY:
-- 1. Identify duplicate indexes (same columns, same order)
-- 2. Identify redundant indexes (subset of multi-column index)
-- 3. Identify unused indexes (never used in queries)
-- 4. Generate DROP statements
-- 5. Validate before dropping
--
-- AUTHOR: Database Optimization Team
-- DATE: 2025-10-31
-- ============================================================================

-- ============================================================================
-- SECTION 1: IDENTIFY DUPLICATE INDEXES
-- ============================================================================

-- Function to find duplicate indexes (exact same definition)
CREATE OR REPLACE FUNCTION find_duplicate_indexes()
RETURNS TABLE (
    schema_name TEXT,
    table_name TEXT,
    index_names TEXT[],
    index_definition TEXT,
    total_size_mb NUMERIC,
    recommended_action TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        schemaname::TEXT,
        tablename::TEXT,
        ARRAY_AGG(indexname::TEXT ORDER BY indexname) as index_names,
        indexdef::TEXT,
        ROUND(SUM(pg_relation_size(schemaname || '.' || indexname))::NUMERIC / 1024 / 1024, 2) as total_size_mb,
        'Keep first index, drop others'::TEXT as recommended_action
    FROM pg_indexes
    WHERE schemaname = 'public'
    GROUP BY schemaname, tablename, indexdef
    HAVING COUNT(*) > 1
    ORDER BY total_size_mb DESC;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- SECTION 2: IDENTIFY REDUNDANT INDEXES
-- ============================================================================

-- Function to find redundant indexes (where one index covers another)
CREATE OR REPLACE FUNCTION find_redundant_indexes()
RETURNS TABLE (
    schema_name TEXT,
    table_name TEXT,
    redundant_index TEXT,
    redundant_columns TEXT,
    covering_index TEXT,
    covering_columns TEXT,
    size_mb NUMERIC,
    recommended_action TEXT
) AS $$
BEGIN
    RETURN QUERY
    WITH index_columns AS (
        SELECT
            schemaname,
            tablename,
            indexname,
            ARRAY_AGG(attname ORDER BY attnum) as columns,
            pg_relation_size(schemaname || '.' || indexname) as size_bytes
        FROM pg_indexes i
        JOIN pg_class c ON c.relname = i.indexname
        JOIN pg_index ix ON ix.indexrelid = c.oid
        JOIN pg_attribute a ON a.attrelid = ix.indrelid AND a.attnum = ANY(ix.indkey)
        WHERE schemaname = 'public'
        GROUP BY schemaname, tablename, indexname, c.oid
    )
    SELECT
        i1.schemaname::TEXT,
        i1.tablename::TEXT,
        i1.indexname::TEXT as redundant_index,
        i1.columns::TEXT as redundant_columns,
        i2.indexname::TEXT as covering_index,
        i2.columns::TEXT as covering_columns,
        ROUND(i1.size_bytes::NUMERIC / 1024 / 1024, 2) as size_mb,
        'Redundant - covered by multi-column index'::TEXT as recommended_action
    FROM index_columns i1
    JOIN index_columns i2 ON
        i1.schemaname = i2.schemaname
        AND i1.tablename = i2.tablename
        AND i1.indexname != i2.indexname
        AND i1.columns <@ i2.columns  -- i1 columns are subset of i2
        AND array_length(i1.columns, 1) < array_length(i2.columns, 1)
    ORDER BY i1.size_bytes DESC;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- SECTION 3: IDENTIFY UNUSED INDEXES
-- ============================================================================

-- Function to find indexes that have never been used
CREATE OR REPLACE FUNCTION find_unused_indexes(min_days_old INTEGER DEFAULT 30)
RETURNS TABLE (
    schema_name TEXT,
    table_name TEXT,
    index_name TEXT,
    index_scans BIGINT,
    size_mb NUMERIC,
    last_vacuum TIMESTAMP,
    days_since_created INTEGER,
    recommended_action TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        schemaname::TEXT,
        tablename::TEXT,
        indexrelname::TEXT,
        idx_scan as index_scans,
        ROUND(pg_relation_size(schemaname || '.' || indexrelname)::NUMERIC / 1024 / 1024, 2) as size_mb,
        last_vacuum,
        EXTRACT(DAY FROM NOW() - pg_stat_get_last_analyze_time(c.oid))::INTEGER as days_since_created,
        CASE
            WHEN idx_scan = 0 THEN 'Never used - safe to drop'
            WHEN idx_scan < 10 THEN 'Rarely used - consider dropping'
            ELSE 'Review query patterns before dropping'
        END::TEXT as recommended_action
    FROM pg_stat_user_indexes
    JOIN pg_class c ON c.relname = indexrelname
    WHERE schemaname = 'public'
        AND idx_scan < 100  -- Used less than 100 times
        AND indexrelname NOT LIKE '%_pkey'  -- Not primary key
        AND indexrelname NOT LIKE '%_unique'  -- Not unique constraint
        AND EXTRACT(DAY FROM NOW() - pg_stat_get_last_analyze_time(c.oid)) > min_days_old
    ORDER BY size_mb DESC;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- SECTION 4: INDEX SIZE ANALYSIS
-- ============================================================================

-- Function to analyze total index overhead per table
CREATE OR REPLACE FUNCTION analyze_index_overhead()
RETURNS TABLE (
    schema_name TEXT,
    table_name TEXT,
    table_size_mb NUMERIC,
    indexes_size_mb NUMERIC,
    index_count INTEGER,
    overhead_ratio NUMERIC,
    recommended_action TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        schemaname::TEXT,
        tablename::TEXT,
        ROUND(pg_table_size(schemaname || '.' || tablename)::NUMERIC / 1024 / 1024, 2) as table_size_mb,
        ROUND(pg_indexes_size(schemaname || '.' || tablename)::NUMERIC / 1024 / 1024, 2) as indexes_size_mb,
        COUNT(indexname)::INTEGER as index_count,
        ROUND((pg_indexes_size(schemaname || '.' || tablename)::NUMERIC /
               NULLIF(pg_table_size(schemaname || '.' || tablename)::NUMERIC, 0)) * 100, 2) as overhead_ratio,
        CASE
            WHEN pg_indexes_size(schemaname || '.' || tablename) > pg_table_size(schemaname || '.' || tablename) * 2
                THEN 'HIGH OVERHEAD - Review index strategy'
            WHEN COUNT(indexname) > 10
                THEN 'Many indexes - Consider consolidation'
            ELSE 'Acceptable'
        END::TEXT as recommended_action
    FROM pg_indexes
    WHERE schemaname = 'public'
    GROUP BY schemaname, tablename
    ORDER BY overhead_ratio DESC NULLS LAST;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- SECTION 5: GENERATE OPTIMIZATION REPORT
-- ============================================================================

CREATE OR REPLACE FUNCTION generate_index_optimization_report()
RETURNS TEXT AS $$
DECLARE
    report TEXT := '';
    total_wasted_space NUMERIC := 0;
    duplicate_count INTEGER := 0;
    redundant_count INTEGER := 0;
    unused_count INTEGER := 0;
BEGIN
    report := E'============================================================================\n';
    report := report || E'INDEX OPTIMIZATION REPORT\n';
    report := report || E'Generated: ' || NOW()::TEXT || E'\n';
    report := report || E'============================================================================\n\n';

    -- Duplicate indexes
    report := report || E'1. DUPLICATE INDEXES (Exact same definition)\n';
    report := report || E'------------------------------------------------------------\n';

    FOR rec IN SELECT * FROM find_duplicate_indexes() LOOP
        duplicate_count := duplicate_count + 1;
        total_wasted_space := total_wasted_space + rec.total_size_mb;
        report := report || E'\nTable: ' || rec.table_name || E'\n';
        report := report || E'Indexes: ' || array_to_string(rec.index_names, ', ') || E'\n';
        report := report || E'Definition: ' || rec.index_definition || E'\n';
        report := report || E'Total Size: ' || rec.total_size_mb || E' MB\n';
        report := report || E'Action: ' || rec.recommended_action || E'\n';
    END LOOP;

    IF duplicate_count = 0 THEN
        report := report || E'No duplicate indexes found.\n';
    END IF;

    -- Redundant indexes
    report := report || E'\n\n2. REDUNDANT INDEXES (Covered by multi-column indexes)\n';
    report := report || E'------------------------------------------------------------\n';

    FOR rec IN SELECT * FROM find_redundant_indexes() LOOP
        redundant_count := redundant_count + 1;
        total_wasted_space := total_wasted_space + rec.size_mb;
        report := report || E'\nTable: ' || rec.table_name || E'\n';
        report := report || E'Redundant Index: ' || rec.redundant_index || ' (' || rec.redundant_columns || E')\n';
        report := report || E'Covered By: ' || rec.covering_index || ' (' || rec.covering_columns || E')\n';
        report := report || E'Size: ' || rec.size_mb || E' MB\n';
        report := report || E'Action: ' || rec.recommended_action || E'\n';
    END LOOP;

    IF redundant_count = 0 THEN
        report := report || E'No redundant indexes found.\n';
    END IF;

    -- Unused indexes
    report := report || E'\n\n3. UNUSED INDEXES (Rarely or never accessed)\n';
    report := report || E'------------------------------------------------------------\n';

    FOR rec IN SELECT * FROM find_unused_indexes(30) LOOP
        unused_count := unused_count + 1;
        total_wasted_space := total_wasted_space + rec.size_mb;
        report := report || E'\nTable: ' || rec.table_name || E'\n';
        report := report || E'Index: ' || rec.index_name || E'\n';
        report := report || E'Scans: ' || rec.index_scans || E'\n';
        report := report || E'Size: ' || rec.size_mb || E' MB\n';
        report := report || E'Action: ' || rec.recommended_action || E'\n';
    END LOOP;

    IF unused_count = 0 THEN
        report := report || E'No unused indexes found.\n';
    END IF;

    -- Index overhead analysis
    report := report || E'\n\n4. INDEX OVERHEAD ANALYSIS\n';
    report := report || E'------------------------------------------------------------\n';

    FOR rec IN SELECT * FROM analyze_index_overhead() WHERE overhead_ratio > 100 LOOP
        report := report || E'\nTable: ' || rec.table_name || E'\n';
        report := report || E'Table Size: ' || rec.table_size_mb || E' MB\n';
        report := report || E'Indexes Size: ' || rec.indexes_size_mb || E' MB\n';
        report := report || E'Index Count: ' || rec.index_count || E'\n';
        report := report || E'Overhead Ratio: ' || rec.overhead_ratio || E'%\n';
        report := report || E'Action: ' || rec.recommended_action || E'\n';
    END LOOP;

    -- Summary
    report := report || E'\n\n============================================================================\n';
    report := report || E'SUMMARY\n';
    report := report || E'============================================================================\n';
    report := report || E'Duplicate Indexes: ' || duplicate_count || E'\n';
    report := report || E'Redundant Indexes: ' || redundant_count || E'\n';
    report := report || E'Unused Indexes: ' || unused_count || E'\n';
    report := report || E'Total Wasted Space: ' || ROUND(total_wasted_space, 2) || E' MB\n';
    report := report || E'Estimated Annual Savings: $' || ROUND(total_wasted_space * 0.15 * 12, 2) || E' (at $0.15/GB/month)\n';
    report := report || E'============================================================================\n';

    RETURN report;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- SECTION 6: GENERATE DROP STATEMENTS
-- ============================================================================

CREATE OR REPLACE FUNCTION generate_drop_statements()
RETURNS TEXT AS $$
DECLARE
    drop_statements TEXT := '';
BEGIN
    drop_statements := E'-- ============================================================================\n';
    drop_statements := drop_statements || E'-- AUTO-GENERATED INDEX DROP STATEMENTS\n';
    drop_statements := drop_statements || E'-- Review carefully before executing!\n';
    drop_statements := drop_statements || E'-- ============================================================================\n\n';

    drop_statements := drop_statements || E'-- Duplicate indexes (keep first, drop rest)\n';
    FOR rec IN SELECT * FROM find_duplicate_indexes() LOOP
        FOR i IN 2..array_length(rec.index_names, 1) LOOP
            drop_statements := drop_statements || E'DROP INDEX CONCURRENTLY IF EXISTS ' || rec.index_names[i] || E'; -- Duplicate of ' || rec.index_names[1] || E'\n';
        END LOOP;
    END LOOP;

    drop_statements := drop_statements || E'\n-- Redundant indexes (covered by multi-column indexes)\n';
    FOR rec IN SELECT * FROM find_redundant_indexes() LOOP
        drop_statements := drop_statements || E'DROP INDEX CONCURRENTLY IF EXISTS ' || rec.redundant_index || E'; -- Covered by ' || rec.covering_index || E'\n';
    END LOOP;

    drop_statements := drop_statements || E'\n-- Unused indexes (never or rarely used)\n';
    FOR rec IN SELECT * FROM find_unused_indexes(30) WHERE index_scans = 0 LOOP
        drop_statements := drop_statements || E'DROP INDEX CONCURRENTLY IF EXISTS ' || rec.index_name || E'; -- ' || rec.index_scans || ' scans, ' || rec.size_mb || E' MB\n';
    END LOOP;

    RETURN drop_statements;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- SECTION 7: EXECUTION INSTRUCTIONS
-- ============================================================================

-- Generate and display report
SELECT generate_index_optimization_report();

-- Generate drop statements (review before executing!)
SELECT generate_drop_statements();

-- To execute the drops:
-- 1. Review the generated DROP statements carefully
-- 2. Test in staging environment first
-- 3. Backup production database
-- 4. Execute during low-traffic window
-- 5. Monitor application performance after drops
-- 6. Have rollback plan ready (re-create indexes if needed)

COMMENT ON FUNCTION find_duplicate_indexes() IS
    'Identifies duplicate indexes with identical definitions';

COMMENT ON FUNCTION find_redundant_indexes() IS
    'Identifies redundant indexes that are subsets of multi-column indexes';

COMMENT ON FUNCTION find_unused_indexes() IS
    'Identifies indexes that are rarely or never used in queries';

COMMENT ON FUNCTION analyze_index_overhead() IS
    'Analyzes index overhead per table to identify over-indexed tables';

COMMENT ON FUNCTION generate_index_optimization_report() IS
    'Generates comprehensive index optimization report';

COMMENT ON FUNCTION generate_drop_statements() IS
    'Generates DROP INDEX statements for redundant/unused indexes';
