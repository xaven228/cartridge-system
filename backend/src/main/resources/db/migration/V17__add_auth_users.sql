CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    can_view_catalog BOOLEAN NOT NULL DEFAULT TRUE,
    can_edit_catalog BOOLEAN NOT NULL DEFAULT FALSE,
    can_operate BOOLEAN NOT NULL DEFAULT FALSE,
    can_view_logs BOOLEAN NOT NULL DEFAULT FALSE,
    can_export_reports BOOLEAN NOT NULL DEFAULT FALSE,
    can_manage_users BOOLEAN NOT NULL DEFAULT FALSE,
    can_manage_thresholds BOOLEAN NOT NULL DEFAULT FALSE,
    can_manual_datetime BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_app_users_role
        CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER'))
);

CREATE INDEX idx_app_users_username ON app_users (username);
