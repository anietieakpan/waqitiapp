-- Fix DECIMAL(15,2) and DECIMAL(10,2) to DECIMAL(19,4) for all financial amounts in Insurance service
-- Priority: HIGH - Critical for large coverage amounts and claim accuracy
-- Impact: Prevents precision loss in high-value policies

-- insurance_policy table - Fix all amount fields
ALTER TABLE insurance_policy
    ALTER COLUMN coverage_amount TYPE DECIMAL(19,4),
    ALTER COLUMN premium_amount TYPE DECIMAL(19,4),
    ALTER COLUMN deductible_amount TYPE DECIMAL(19,4),
    ALTER COLUMN total_premiums_paid TYPE DECIMAL(19,4),
    ALTER COLUMN total_claims_paid TYPE DECIMAL(19,4);

-- insurance_claim table - Fix all claim amount fields
ALTER TABLE insurance_claim
    ALTER COLUMN claim_amount TYPE DECIMAL(19,4),
    ALTER COLUMN approved_amount TYPE DECIMAL(19,4),
    ALTER COLUMN deductible_applied TYPE DECIMAL(19,4);

-- insurance_premium_payment table - Fix payment amount fields
ALTER TABLE insurance_premium_payment
    ALTER COLUMN amount TYPE DECIMAL(19,4),
    ALTER COLUMN late_fee TYPE DECIMAL(19,4);

-- insurance_quote table - Fix quote amount fields
ALTER TABLE insurance_quote
    ALTER COLUMN requested_coverage_amount TYPE DECIMAL(19,4),
    ALTER COLUMN quoted_premium TYPE DECIMAL(19,4);

-- Add comments for documentation
COMMENT ON COLUMN insurance_policy.coverage_amount IS 'Coverage amount with 4 decimal precision for high-value policies';
COMMENT ON COLUMN insurance_claim.claim_amount IS 'Claim amount with 4 decimal precision for accurate claim processing';
COMMENT ON COLUMN insurance_claim.approved_amount IS 'Approved amount with 4 decimal precision for accurate payouts';

-- Analyze tables to update statistics
ANALYZE insurance_policy;
ANALYZE insurance_claim;
ANALYZE insurance_premium_payment;
ANALYZE insurance_quote;
