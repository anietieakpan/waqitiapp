-- Cleanup script for tests
DELETE FROM bill_payment_audit_logs;
DELETE FROM bill_share_participants;
DELETE FROM bill_share_requests;
DELETE FROM bill_payments;
DELETE FROM auto_pay_configs;
DELETE FROM bill_reminders;
DELETE FROM bills;
DELETE FROM biller_connections;
DELETE FROM billers;
