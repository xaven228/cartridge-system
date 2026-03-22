ALTER TABLE printers
    DROP COLUMN IF EXISTS quantity;

ALTER TABLE printers
    ADD COLUMN IF NOT EXISTS cartridge_model_id BIGINT,
    ADD COLUMN IF NOT EXISTS previous_replacement_date DATE,
    ADD COLUMN IF NOT EXISTS last_replacement_date DATE;

ALTER TABLE printers
    ADD CONSTRAINT fk_printers_cartridge_model
        FOREIGN KEY (cartridge_model_id) REFERENCES cartridge_models(id);
