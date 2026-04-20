ALTER TABLE cartridges
    DROP CONSTRAINT IF EXISTS chk_cartridges_status;

ALTER TABLE cartridges
    ADD CONSTRAINT chk_cartridges_status
        CHECK (status IN ('IN_STOCK', 'RESERVE', 'INSTALLED', 'ON_REFILL', 'WRITTEN_OFF'));
