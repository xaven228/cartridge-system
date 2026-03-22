ALTER TABLE printers
    ADD COLUMN IF NOT EXISTS quantity INTEGER NOT NULL DEFAULT 1;

UPDATE printers
SET quantity = 1
WHERE quantity IS NULL OR quantity < 1;

CREATE TABLE IF NOT EXISTS printer_installations (
    id BIGSERIAL PRIMARY KEY,
    printer_id BIGINT NOT NULL REFERENCES printers(id) ON DELETE CASCADE,
    cartridge_id BIGINT NOT NULL REFERENCES cartridges(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_printer_installations_printer_cartridge UNIQUE (printer_id, cartridge_id)
);

CREATE INDEX IF NOT EXISTS idx_printer_installations_printer_id ON printer_installations(printer_id);
CREATE INDEX IF NOT EXISTS idx_printer_installations_cartridge_id ON printer_installations(cartridge_id);
