ALTER TABLE printers
    ADD COLUMN room_id BIGINT;

ALTER TABLE printers
    ADD CONSTRAINT fk_printers_room
        FOREIGN KEY (room_id) REFERENCES rooms (id);

CREATE INDEX idx_printers_room_id ON printers (room_id);
