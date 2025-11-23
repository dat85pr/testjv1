-- Admin user gốc
INSERT INTO users (username, email, password, full_name, active, verified, role, created_at, updated_at)
VALUES ('admin', 'admin@socialblog.com', 'admin123', 'Administrator', true, true, 'ADMIN', NOW(), NOW())
    ON DUPLICATE KEY UPDATE role = 'ADMIN';