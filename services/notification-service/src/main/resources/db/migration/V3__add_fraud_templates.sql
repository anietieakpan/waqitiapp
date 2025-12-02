-- File: services/notification-service/src/main/resources/db/migration/V3__add_fraud_templates.sql

-- Create template for fraud alert notifications
INSERT INTO notification_templates (
    id, code, name, category, title_template, message_template,
    email_subject_template, email_body_template, sms_template,
    action_url_template, enabled, created_at, updated_at
) VALUES (
    gen_random_uuid(), 'FRAUD_ALERT', 'Fraud Alert Notification', 'SECURITY_ALERT',
    '${severity} Fraud Alert - ${alertType}', 'Fraud detected: ${description}',
    '[${severity}] Fraud Alert - Transaction ${transactionId}',
    '<div style="font-family: Arial, sans-serif; padding: 20px; max-width: 600px; margin: 0 auto; border: 2px solid #dc3545; border-radius: 8px; background-color: #f8f9fa;">
        <div style="text-align: center; background-color: #dc3545; color: white; padding: 15px; margin: -20px -20px 20px -20px; border-radius: 6px 6px 0 0;">
            <h1 style="margin: 0; font-size: 24px;">🚨 ${severity} FRAUD ALERT</h1>
        </div>
        <div style="padding: 20px; background-color: white; border-radius: 4px;">
            <h2 style="color: #dc3545; margin-top: 0;">${title}</h2>
            <p><strong>Alert ID:</strong> ${alertId}</p>
            <p><strong>Alert Type:</strong> ${alertType}</p>
            <p><strong>Description:</strong> ${description}</p>
            <p><strong>Transaction ID:</strong> ${transactionId}</p>
            <p><strong>Amount:</strong> ${amount} ${currency}</p>
            <p><strong>Fraud Score:</strong> ${fraudScore}</p>
            <p><strong>Risk Level:</strong> ${riskLevel}</p>
            <p><strong>Detection Method:</strong> ${detectionMethod}</p>
            <p><strong>Timestamp:</strong> ${timestamp}</p>
            
            <div style="background-color: #fff3cd; border: 1px solid #ffeaa7; border-radius: 4px; padding: 15px; margin: 20px 0;">
                <p style="margin: 0; font-weight: bold; color: #856404;">⚠️ Action Required:</p>
                <p style="margin: 5px 0 0 0; color: #856404;">${actionRequired ? "Immediate investigation required" : "Review recommended"}</p>
            </div>
            
            <div style="text-align: center; margin-top: 30px;">
                <a href="${actionUrl}" style="display: inline-block; padding: 12px 30px; background-color: #dc3545; color: white; text-decoration: none; border-radius: 4px; font-weight: bold;">INVESTIGATE NOW</a>
            </div>
        </div>
        <p style="margin-top: 20px; font-size: 12px; color: #6c757d; text-align: center;">
            This is an automated fraud detection alert. Please review immediately.
        </p>
    </div>',
    'FRAUD ALERT: ${severity} - ${alertType} detected for transaction ${transactionId}. Fraud score: ${fraudScore}. Investigate immediately.',
    '/fraud/alerts/${alertId}', true, NOW(), NOW()
);

-- Create template for transaction verification notifications
INSERT INTO notification_templates (
    id, code, name, category, title_template, message_template,
    email_subject_template, email_body_template, sms_template,
    action_url_template, enabled, created_at, updated_at
) VALUES (
    gen_random_uuid(), 'TRANSACTION_VERIFICATION', 'Transaction Verification', 'SECURITY',
    'Verify Transaction', 'Please verify your transaction of ${amount} ${currency}',
    'Verify Your Transaction - ${merchantName}',
    '<div style="font-family: Arial, sans-serif; padding: 20px; max-width: 600px; margin: 0 auto; border: 1px solid #17a2b8; border-radius: 8px; background-color: #f8f9fa;">
        <div style="text-align: center; background-color: #17a2b8; color: white; padding: 15px; margin: -20px -20px 20px -20px; border-radius: 6px 6px 0 0;">
            <h1 style="margin: 0; font-size: 24px;">🔐 Transaction Verification</h1>
        </div>
        <div style="padding: 20px; background-color: white; border-radius: 4px;">
            <h2 style="color: #17a2b8; margin-top: 0;">Verify Your Transaction</h2>
            <p>We detected a transaction that requires your verification:</p>
            
            <div style="background-color: #e1f5fe; border: 1px solid #b3e5fc; border-radius: 4px; padding: 15px; margin: 20px 0;">
                <p><strong>Transaction ID:</strong> ${transactionId}</p>
                <p><strong>Amount:</strong> ${amount} ${currency}</p>
                <p><strong>Merchant:</strong> ${merchantName}</p>
                <p><strong>Time:</strong> ${timestamp}</p>
            </div>
            
            <div style="background-color: #fff3cd; border: 1px solid #ffeaa7; border-radius: 4px; padding: 15px; margin: 20px 0; text-align: center;">
                <h3 style="margin: 0 0 10px 0; color: #856404;">Verification Code</h3>
                <div style="font-size: 28px; font-weight: bold; color: #856404; letter-spacing: 3px;">${verificationCode}</div>
                <p style="margin: 10px 0 0 0; font-size: 12px; color: #856404;">Enter this code to approve the transaction</p>
            </div>
            
            <p style="color: #6c757d; font-size: 14px;">If you did not initiate this transaction, please contact our security team immediately.</p>
        </div>
        <p style="margin-top: 20px; font-size: 12px; color: #6c757d; text-align: center;">
            This verification code expires in 10 minutes.
        </p>
    </div>',
    'Waqiti: Verify transaction ${amount} ${currency} at ${merchantName}. Code: ${verificationCode}',
    '/verify/transaction/${transactionId}', true, NOW(), NOW()
);

-- Create template for security alert notifications
INSERT INTO notification_templates (
    id, code, name, category, title_template, message_template,
    email_subject_template, email_body_template, sms_template,
    action_url_template, enabled, created_at, updated_at
) VALUES (
    gen_random_uuid(), 'SECURITY_ALERT', 'Security Alert', 'SECURITY_ALERT',
    'Security Alert - ${eventType}', 'Security event detected: ${eventDescription}',
    'Security Alert - ${eventType}',
    '<div style="font-family: Arial, sans-serif; padding: 20px; max-width: 600px; margin: 0 auto; border: 2px solid #fd7e14; border-radius: 8px; background-color: #f8f9fa;">
        <div style="text-align: center; background-color: #fd7e14; color: white; padding: 15px; margin: -20px -20px 20px -20px; border-radius: 6px 6px 0 0;">
            <h1 style="margin: 0; font-size: 24px;">${urgent ? "🚨" : "⚠️"} Security Alert</h1>
        </div>
        <div style="padding: 20px; background-color: white; border-radius: 4px;">
            <h2 style="color: #fd7e14; margin-top: 0;">${eventType}</h2>
            <p><strong>Event:</strong> ${eventDescription}</p>
            <p><strong>Time:</strong> ${timestamp}</p>
            <p><strong>IP Address:</strong> ${ipAddress}</p>
            <p><strong>Location:</strong> ${location}</p>
            
            <div style="background-color: ${urgent ? "#f8d7da" : "#d4edda"}; border: 1px solid ${urgent ? "#f5c6cb" : "#c3e6cb"}; border-radius: 4px; padding: 15px; margin: 20px 0;">
                <p style="margin: 0; font-weight: bold; color: ${urgent ? "#721c24" : "#155724"};">
                    ${urgent ? "🚨 Urgent Action Required" : "ℹ️ Information"}
                </p>
                <p style="margin: 5px 0 0 0; color: ${urgent ? "#721c24" : "#155724"};">
                    ${urgent ? "Please review this security event immediately and take appropriate action." : "This is an informational security notification."}
                </p>
            </div>
            
            <div style="text-align: center; margin-top: 30px;">
                <a href="/security/dashboard" style="display: inline-block; padding: 12px 30px; background-color: #fd7e14; color: white; text-decoration: none; border-radius: 4px; font-weight: bold;">VIEW SECURITY DASHBOARD</a>
            </div>
        </div>
        <p style="margin-top: 20px; font-size: 12px; color: #6c757d; text-align: center;">
            If you did not perform this action, please secure your account immediately.
        </p>
    </div>',
    'Waqiti Security: ${eventType} detected from ${location}. ${urgent ? "Review immediately." : ""}',
    '/security/dashboard', true, NOW(), NOW()
);