ALTER TABLE departments
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

UPDATE departments
SET status = 'ACTIVE'
WHERE status IS NULL;

ALTER TABLE departments
    DROP CONSTRAINT IF EXISTS chk_departments_status;

ALTER TABLE departments
    ADD CONSTRAINT chk_departments_status
        CHECK (status IN ('ACTIVE', 'DECOMMISSIONED'));
