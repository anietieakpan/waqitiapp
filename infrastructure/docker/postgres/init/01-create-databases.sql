-- Multi-database initialization for Waqiti Development Environment
-- This script creates all necessary databases for different microservices

-- Create databases for each microservice
CREATE DATABASE waqiti_users;
CREATE DATABASE waqiti_accounts;  
CREATE DATABASE waqiti_payments;
CREATE DATABASE waqiti_transactions;
CREATE DATABASE waqiti_notifications;
CREATE DATABASE waqiti_kyc;
CREATE DATABASE waqiti_audit;
CREATE DATABASE waqiti_analytics;
CREATE DATABASE waqiti_compliance;
CREATE DATABASE waqiti_crypto;
CREATE DATABASE waqiti_ml;
CREATE DATABASE waqiti_integration;
CREATE DATABASE waqiti_bank_integration;
CREATE DATABASE waqiti_reconciliation;
CREATE DATABASE waqiti_dispute;
CREATE DATABASE waqiti_fraud;
CREATE DATABASE waqiti_reporting;

-- Grant permissions to waqiti user for all databases
GRANT ALL PRIVILEGES ON DATABASE waqiti_users TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_accounts TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_payments TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_transactions TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_notifications TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_kyc TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_audit TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_analytics TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_compliance TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_crypto TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_ml TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_integration TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_bank_integration TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_reconciliation TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_dispute TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_fraud TO waqiti;
GRANT ALL PRIVILEGES ON DATABASE waqiti_reporting TO waqiti;

-- Create extensions commonly needed by microservices
\c waqiti_users;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c waqiti_accounts;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c waqiti_payments;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c waqiti_transactions;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c waqiti_notifications;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c waqiti_kyc;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c waqiti_audit;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c waqiti_analytics;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c waqiti_compliance;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c waqiti_crypto;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c waqiti_ml;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c waqiti_integration;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c waqiti_bank_integration;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c waqiti_reconciliation;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c waqiti_dispute;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c waqiti_fraud;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c waqiti_reporting;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";