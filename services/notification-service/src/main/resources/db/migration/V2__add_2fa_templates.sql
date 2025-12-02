-- File: services/notification-service/src/main/resources/db/migration/V2__add_2fa_templates.sql

-- Create template for 2FA SMS notifications
INSERT INTO notification_templates (
    id, code, name, category, title_template, message_template,
    email_subject_template, email_body_template, sms_template,
    action_url_template, enabled, created_at, updated_at
) VALUES (
             gen_random_uuid(), 'two_factor_code', '2FA Verification Code', 'SECURITY',
             'Verification Code', 'Your verification code is: ${code}',
             'Security Verification Code',
             '<div style="font-family: Arial, sans-serif; padding: 20px; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 5px;">
                 <h2 style="color: #333;">Security Verification</h2>
                 <p>Your verification code is: <strong>${code}</strong></p>
                 <p>This code will expire in 10 minutes.</p>
                 <p>If you did not request this code, please ignore this message.</p>
                 <p style="margin-top: 30px; font-size: 12px; color: #777;">
                     This is an automated message, please do not reply.
                 </p>
             </div>',
             'Your Waqiti verification code is: ${code}',
             NULL, true, NOW(), NOW()
         );

-- Create template for 2FA email notifications
INSERT INTO notification_templates (
    id, code, name, category, title_template, message_template,
    email_subject_template, email_body_template, sms_template,
    action_url_template, enabled, created_at, updated_at
) VALUES (
             gen_random_uuid(), 'two_factor_email', '2FA Email Verification Code', 'SECURITY',
             'Verification Code', 'Your verification code is: ${code}',
             'Security Verification Code',
             '<div style="font-family: Arial, sans-serif; padding: 20px; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 5px;">
                 <h2 style="color: #333;">Security Verification</h2>
                 <p>Your verification code for ${email} is: <strong>${code}</strong></p>
                 <p>This code will expire in 10 minutes.</p>
                 <p>If you did not request this code, please ignore this message.</p>
                 <p style="margin-top: 30px; font-size: 12px; color: #777;">
                     This is an automated message, please do not reply.
                 </p>
             </div>',
             NULL,
             NULL, true, NOW(), NOW()
         );