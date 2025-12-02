-- Create Notifications Table
CREATE TABLE notifications (
                               id UUID PRIMARY KEY,
                               user_id UUID NOT NULL,
                               title VARCHAR(100) NOT NULL,
                               message VARCHAR(1000) NOT NULL,
                               type VARCHAR(20) NOT NULL,
                               category VARCHAR(100),
                               reference_id VARCHAR(100),
                               read BOOLEAN NOT NULL DEFAULT FALSE,
                               action_url VARCHAR(500),
                               created_at TIMESTAMP NOT NULL,
                               expires_at TIMESTAMP,
                               read_at TIMESTAMP,
                               delivery_status VARCHAR(20) NOT NULL,
                               delivery_error VARCHAR(500),
                               version BIGINT NOT NULL DEFAULT 0,
                               created_by VARCHAR(100)
);

-- Create Notification Preferences Table
CREATE TABLE notification_preferences (
                                          user_id UUID PRIMARY KEY,
                                          app_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                          email_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                          sms_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                                          push_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                                          quiet_hours_start INTEGER,
                                          quiet_hours_end INTEGER,
                                          email VARCHAR(255),
                                          phone_number VARCHAR(20),
                                          device_token VARCHAR(500),
                                          created_at TIMESTAMP NOT NULL,
                                          updated_at TIMESTAMP NOT NULL,
                                          version BIGINT NOT NULL DEFAULT 0,
                                          created_by VARCHAR(100),
                                          updated_by VARCHAR(100)
);

-- Create Category Preferences Table
CREATE TABLE category_preferences (
                                      user_id UUID NOT NULL REFERENCES notification_preferences(user_id),
                                      category VARCHAR(50) NOT NULL,
                                      enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                      PRIMARY KEY (user_id, category)
);

-- Create Notification Templates Table
CREATE TABLE notification_templates (
                                        id UUID PRIMARY KEY,
                                        code VARCHAR(100) NOT NULL UNIQUE,
                                        name VARCHAR(100) NOT NULL,
                                        category VARCHAR(100) NOT NULL,
                                        title_template VARCHAR(200) NOT NULL,
                                        message_template VARCHAR(2000) NOT NULL,
                                        email_subject_template VARCHAR(2000),
                                        email_body_template VARCHAR(5000),
                                        sms_template VARCHAR(200),
                                        action_url_template VARCHAR(500),
                                        enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                        created_at TIMESTAMP NOT NULL,
                                        updated_at TIMESTAMP NOT NULL,
                                        created_by VARCHAR(100),
                                        updated_by VARCHAR(100)
);

-- Create indexes
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);
CREATE INDEX idx_notifications_read ON notifications(read);
CREATE INDEX idx_notifications_delivery_status ON notifications(delivery_status);
CREATE INDEX idx_notifications_category ON notifications(category);
CREATE INDEX idx_notifications_reference_id ON notifications(reference_id);
CREATE INDEX idx_notification_templates_code ON notification_templates(code);
CREATE INDEX idx_notification_templates_category ON notification_templates(category);