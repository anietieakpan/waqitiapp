-- Fix DECIMAL(15,2) to DECIMAL(19,4) for all financial amounts in BNPL service
-- Priority: HIGH - Financial precision critical for loan calculations
-- Impact: Prevents precision loss in installment calculations and interest

-- bnpl_applications table - Fix all amount fields
ALTER TABLE bnpl_applications
    ALTER COLUMN purchase_amount TYPE DECIMAL(19,4),
    ALTER COLUMN down_payment TYPE DECIMAL(19,4),
    ALTER COLUMN financed_amount TYPE DECIMAL(19,4),
    ALTER COLUMN installment_amount TYPE DECIMAL(19,4),
    ALTER COLUMN total_amount TYPE DECIMAL(19,4);

-- bnpl_installments table - Fix all payment fields
ALTER TABLE bnpl_installments
    ALTER COLUMN principal_amount TYPE DECIMAL(19,4),
    ALTER COLUMN interest_amount TYPE DECIMAL(19,4),
    ALTER COLUMN total_amount TYPE DECIMAL(19,4),
    ALTER COLUMN paid_amount TYPE DECIMAL(19,4),
    ALTER COLUMN outstanding_amount TYPE DECIMAL(19,4),
    ALTER COLUMN late_fee_amount TYPE DECIMAL(19,4);

-- credit_assessments table - Fix income and debt fields
ALTER TABLE credit_assessments
    ALTER COLUMN monthly_income TYPE DECIMAL(19,4),
    ALTER COLUMN monthly_debt TYPE DECIMAL(19,4),
    ALTER COLUMN available_credit TYPE DECIMAL(19,4),
    ALTER COLUMN recommended_limit TYPE DECIMAL(19,4);

-- loan_applications table - Fix all loan amounts (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'loan_applications') THEN
        ALTER TABLE loan_applications
            ALTER COLUMN requested_amount TYPE DECIMAL(19,4),
            ALTER COLUMN approved_amount TYPE DECIMAL(19,4),
            ALTER COLUMN disbursed_amount TYPE DECIMAL(19,4),
            ALTER COLUMN outstanding_principal TYPE DECIMAL(19,4),
            ALTER COLUMN outstanding_interest TYPE DECIMAL(19,4),
            ALTER COLUMN total_outstanding TYPE DECIMAL(19,4);
    END IF;
END $$;

-- loan_installments table (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'loan_installments') THEN
        ALTER TABLE loan_installments
            ALTER COLUMN principal_amount TYPE DECIMAL(19,4),
            ALTER COLUMN interest_amount TYPE DECIMAL(19,4),
            ALTER COLUMN total_amount TYPE DECIMAL(19,4),
            ALTER COLUMN paid_amount TYPE DECIMAL(19,4),
            ALTER COLUMN outstanding_amount TYPE DECIMAL(19,4),
            ALTER COLUMN late_fee_amount TYPE DECIMAL(19,4);
    END IF;
END $$;

-- Add comments for documentation
COMMENT ON COLUMN bnpl_applications.purchase_amount IS 'Purchase amount with 4 decimal precision for accurate calculations';
COMMENT ON COLUMN bnpl_applications.installment_amount IS 'Installment amount with 4 decimal precision to prevent rounding errors';
COMMENT ON COLUMN bnpl_installments.interest_amount IS 'Interest with 4 decimal precision for accurate accrual';

-- Analyze tables to update statistics
ANALYZE bnpl_applications;
ANALYZE bnpl_installments;
ANALYZE credit_assessments;
