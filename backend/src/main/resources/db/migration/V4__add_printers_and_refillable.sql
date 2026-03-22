ALTER TABLE cartridge_models
    ADD COLUMN refillable BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE printers (
    id BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_printers_department
        FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE CASCADE
);

CREATE INDEX idx_printers_department_id ON printers (department_id);
