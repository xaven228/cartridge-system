CREATE TABLE rooms (
    id BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_rooms_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT uq_rooms_department_name
        UNIQUE (department_id, name),
    CONSTRAINT chk_rooms_status
        CHECK (status IN ('ACTIVE', 'DECOMMISSIONED'))
);

CREATE TABLE inventory_assets (
    id BIGSERIAL PRIMARY KEY,
    inventory_code VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100),
    department_id BIGINT,
    room_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_USE',
    quantity INTEGER NOT NULL DEFAULT 1,
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_inventory_assets_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_inventory_assets_room
        FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT chk_inventory_assets_status
        CHECK (status IN ('IN_USE', 'IN_STOCK', 'IN_REPAIR', 'WRITTEN_OFF')),
    CONSTRAINT chk_inventory_assets_quantity
        CHECK (quantity >= 0)
);

CREATE TABLE hall_requests (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    requester_name VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    planned_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_hall_requests_room
        FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT chk_hall_requests_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_hall_requests_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED'))
);

CREATE INDEX idx_rooms_department_id ON rooms (department_id);
CREATE INDEX idx_inventory_assets_department_id ON inventory_assets (department_id);
CREATE INDEX idx_inventory_assets_room_id ON inventory_assets (room_id);
CREATE INDEX idx_hall_requests_room_id ON hall_requests (room_id);
CREATE INDEX idx_hall_requests_status ON hall_requests (status);
