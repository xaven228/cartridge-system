DELETE FROM refill_history
WHERE cartridge_id IN (
    SELECT id
    FROM cartridges
    WHERE inventory_code IN ('INV-HP-001', 'INV-CANON-001', 'INV-BROTHER-001')
);

DELETE FROM cartridges
WHERE inventory_code IN ('INV-HP-001', 'INV-CANON-001', 'INV-BROTHER-001');

DELETE FROM cartridge_models
WHERE name IN ('HP 83A', 'Canon 725', 'Brother TN-1075');

DELETE FROM departments
WHERE name IN ('IT Department', 'Accounting', 'HR');
