CREATE TABLE notification_thresholds (
    id BIGSERIAL PRIMARY KEY,
    cartridge_model_id BIGINT NOT NULL,
    department_id BIGINT,
    minimum_quantity INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notification_thresholds_model
        FOREIGN KEY (cartridge_model_id) REFERENCES cartridge_models (id),
    CONSTRAINT fk_notification_thresholds_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT chk_notification_thresholds_minimum
        CHECK (minimum_quantity >= 0)
);

CREATE UNIQUE INDEX uq_notification_threshold_model_department
    ON notification_thresholds (cartridge_model_id, department_id);

CREATE UNIQUE INDEX uq_notification_threshold_model_global
    ON notification_thresholds (cartridge_model_id)
    WHERE department_id IS NULL;
