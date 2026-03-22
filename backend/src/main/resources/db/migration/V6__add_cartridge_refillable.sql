ALTER TABLE cartridges
    ADD COLUMN IF NOT EXISTS refillable BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE cartridges
SET refillable = COALESCE(refillable, TRUE);
