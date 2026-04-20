INSERT INTO app_users (
    username,
    password_hash,
    full_name,
    role,
    is_active,
    can_view_catalog,
    can_edit_catalog,
    can_operate,
    can_view_logs,
    can_export_reports,
    can_manage_users,
    can_manage_thresholds,
    can_manual_datetime
)
VALUES (
    'admin',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOHi9vFoZT3zh0+BTtPlqvGjYH6G6D/ad',
    'Администратор',
    'ADMIN',
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    TRUE
)
ON CONFLICT (username) DO NOTHING;
