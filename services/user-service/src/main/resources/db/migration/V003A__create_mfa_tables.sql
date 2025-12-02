-- File: services/user-service/src/main/resources/db/migration/V2__create_mfa_tables.sql
-- Create MFA Configurations Table
CREATE TABLE mfa_configurations (
                                    id UUID PRIMARY KEY,
                                    user_id UUID NOT NULL REFERENCES users(id),
                                    method VARCHAR(20) NOT NULL,
                                    enabled BOOLEAN NOT NULL DEFAULT FALSE,
                                    secret VARCHAR(255),
                                    verified BOOLEAN NOT NULL DEFAULT FALSE,
                                    created_at TIMESTAMP NOT NULL,
                                    updated_at TIMESTAMP NOT NULL,
                                    version BIGINT NOT NULL,

                                    CONSTRAINT uk_user_method UNIQUE (user_id, method)
);

-- Create index for faster lookup
CREATE INDEX idx_mfa_configurations_user_id ON mfa_configurations(user_id);

-- Create table for temporary verification codes (for SMS/Email methods)
CREATE TABLE mfa_verification_codes (
                                        id UUID PRIMARY KEY,
                                        user_id UUID NOT NULL REFERENCES users(id),
                                        method VARCHAR(20) NOT NULL,
                                        code VARCHAR(10) NOT NULL,
                                        expiry_date TIMESTAMP NOT NULL,
                                        created_at TIMESTAMP NOT NULL,
                                        used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_mfa_verification_codes_user_id ON mfa_verification_codes(user_id);
CREATE INDEX idx_mfa_verification_codes_expiry_date ON mfa_verification_codes(expiry_date);