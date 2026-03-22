INSERT INTO departments (name, description)
VALUES
    ('IT Department', 'Main office IT team'),
    ('Accounting', 'Finance and accounting'),
    ('HR', 'Human resources');

INSERT INTO cartridge_models (name, printer_model, manufacturer, color_type, resource_pages)
VALUES
    ('HP 83A', 'LaserJet Pro M125/M127', 'HP', 'BLACK', 1500),
    ('Canon 725', 'i-SENSYS LBP6000/LBP6020', 'Canon', 'BLACK', 1600),
    ('Brother TN-1075', 'HL-1110R / DCP-1512R', 'Brother', 'BLACK', 1000);

INSERT INTO cartridges (
    inventory_code,
    cartridge_model_id,
    department_id,
    quantity,
    status,
    refill_count,
    last_refill_date,
    comment
)
VALUES
    (
        'INV-HP-001',
        (SELECT id FROM cartridge_models WHERE name = 'HP 83A'),
        (SELECT id FROM departments WHERE name = 'IT Department'),
        4,
        'IN_STOCK',
        1,
        CURRENT_DATE - INTERVAL '25 days',
        'Primary stock'
    ),
    (
        'INV-CANON-001',
        (SELECT id FROM cartridge_models WHERE name = 'Canon 725'),
        (SELECT id FROM departments WHERE name = 'Accounting'),
        2,
        'INSTALLED',
        0,
        NULL,
        'Installed in accounting printer'
    ),
    (
        'INV-BROTHER-001',
        (SELECT id FROM cartridge_models WHERE name = 'Brother TN-1075'),
        (SELECT id FROM departments WHERE name = 'HR'),
        1,
        'ON_REFILL',
        2,
        CURRENT_DATE - INTERVAL '40 days',
        'Sent to refill'
    );

INSERT INTO refill_history (cartridge_id, sent_at, returned_at, status, comment, created_by)
VALUES
    (
        (SELECT id FROM cartridges WHERE inventory_code = 'INV-BROTHER-001'),
        CURRENT_DATE - INTERVAL '3 days',
        NULL,
        'SENT',
        'Current refill cycle',
        'system'
    ),
    (
        (SELECT id FROM cartridges WHERE inventory_code = 'INV-HP-001'),
        CURRENT_DATE - INTERVAL '25 days',
        CURRENT_DATE - INTERVAL '20 days',
        'RETURNED',
        'Previous successful refill',
        'system'
    );
