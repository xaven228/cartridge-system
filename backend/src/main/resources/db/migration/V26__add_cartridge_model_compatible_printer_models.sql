ALTER TABLE cartridge_models
    ADD COLUMN IF NOT EXISTS compatible_printer_models TEXT;

UPDATE cartridge_models
SET compatible_printer_models = BTRIM(printer_model)
WHERE compatible_printer_models IS NULL
  AND printer_model IS NOT NULL
  AND BTRIM(printer_model) <> '';
