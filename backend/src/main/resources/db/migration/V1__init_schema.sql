CREATE TABLE cartridge_models (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    printer_model VARCHAR(255),
    manufacturer VARCHAR(255),
    color_type VARCHAR(255),
    resource_pages INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE departments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE cartridges (
    id BIGSERIAL PRIMARY KEY,
    inventory_code VARCHAR(255) NOT NULL UNIQUE,
    cartridge_model_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    refill_count INTEGER NOT NULL DEFAULT 0,
    last_refill_date DATE,
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_cartridges_model
        FOREIGN KEY (cartridge_model_id) REFERENCES cartridge_models (id),
    CONSTRAINT fk_cartridges_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT chk_cartridges_quantity_nonnegative
        CHECK (quantity >= 0),
    CONSTRAINT chk_cartridges_refill_count_nonnegative
        CHECK (refill_count >= 0),
    CONSTRAINT chk_cartridges_status
        CHECK (status IN ('IN_STOCK', 'INSTALLED', 'ON_REFILL', 'WRITTEN_OFF'))
);

CREATE TABLE refill_history (
    id BIGSERIAL PRIMARY KEY,
    cartridge_id BIGINT NOT NULL,
    sent_at DATE,
    returned_at DATE,
    status VARCHAR(32) NOT NULL,
    comment VARCHAR(1000),
    created_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_refill_history_cartridge
        FOREIGN KEY (cartridge_id) REFERENCES cartridges (id),
    CONSTRAINT chk_refill_history_status
        CHECK (status IN ('SENT', 'RETURNED', 'FAILED', 'WRITTEN_OFF'))
);

CREATE INDEX idx_cartridges_model_id ON cartridges (cartridge_model_id);
CREATE INDEX idx_cartridges_department_id ON cartridges (department_id);
CREATE INDEX idx_refill_history_cartridge_id ON refill_history (cartridge_id);
