ALTER TABLE printers
ADD COLUMN printer_type VARCHAR(32) NOT NULL DEFAULT 'MONOCHROME';

CREATE TABLE printer_slots (
    id BIGSERIAL PRIMARY KEY,
    printer_id BIGINT NOT NULL REFERENCES printers(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    cartridge_model_id BIGINT REFERENCES cartridge_models(id),
    previous_replacement_date DATE,
    last_replacement_date DATE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

INSERT INTO printer_slots (
    printer_id,
    name,
    cartridge_model_id,
    previous_replacement_date,
    last_replacement_date,
    created_at,
    updated_at
)
SELECT
    p.id,
    'Основной',
    p.cartridge_model_id,
    p.previous_replacement_date,
    p.last_replacement_date,
    p.created_at,
    p.updated_at
FROM printers p;

ALTER TABLE printer_installations
ADD COLUMN printer_slot_id BIGINT;

UPDATE printer_installations pi
SET printer_slot_id = ps.id
FROM printer_slots ps
WHERE ps.printer_id = pi.printer_id;

ALTER TABLE printer_installations
ALTER COLUMN printer_slot_id SET NOT NULL;

ALTER TABLE printer_installations
ADD CONSTRAINT fk_printer_installations_slot
FOREIGN KEY (printer_slot_id) REFERENCES printer_slots(id) ON DELETE CASCADE;
