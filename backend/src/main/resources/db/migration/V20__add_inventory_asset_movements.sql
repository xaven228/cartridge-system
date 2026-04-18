CREATE TABLE inventory_asset_movements (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    from_department_id BIGINT,
    from_room_id BIGINT,
    to_department_id BIGINT,
    to_room_id BIGINT,
    moved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor VARCHAR(255),
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_inventory_asset_movements_asset
        FOREIGN KEY (asset_id) REFERENCES inventory_assets (id),
    CONSTRAINT fk_inventory_asset_movements_from_department
        FOREIGN KEY (from_department_id) REFERENCES departments (id),
    CONSTRAINT fk_inventory_asset_movements_from_room
        FOREIGN KEY (from_room_id) REFERENCES rooms (id),
    CONSTRAINT fk_inventory_asset_movements_to_department
        FOREIGN KEY (to_department_id) REFERENCES departments (id),
    CONSTRAINT fk_inventory_asset_movements_to_room
        FOREIGN KEY (to_room_id) REFERENCES rooms (id)
);

CREATE INDEX idx_inventory_asset_movements_asset_id ON inventory_asset_movements (asset_id);
CREATE INDEX idx_inventory_asset_movements_moved_at ON inventory_asset_movements (moved_at);
