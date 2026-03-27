WITH duplicate_groups AS (
    SELECT
        MIN(c.id) AS keep_id,
        ARRAY_AGG(c.id) AS grouped_ids,
        SUM(c.quantity) AS total_quantity,
        MAX(c.refill_count) AS max_refill_count,
        MAX(c.last_refill_date) AS max_last_refill_date
    FROM cartridges c
    WHERE c.status = 'IN_STOCK'
      AND NOT EXISTS (
        SELECT 1
        FROM printer_installations pi
        WHERE pi.cartridge_id = c.id
          AND pi.quantity > 0
      )
    GROUP BY c.department_id, c.cartridge_model_id, c.refillable, c.empty
    HAVING COUNT(*) > 1
),
updated_rows AS (
    UPDATE cartridges c
    SET quantity = g.total_quantity,
        refill_count = g.max_refill_count,
        last_refill_date = g.max_last_refill_date
    FROM duplicate_groups g
    WHERE c.id = g.keep_id
    RETURNING g.keep_id, g.grouped_ids
)
DELETE FROM cartridges c
USING updated_rows u
WHERE c.id = ANY(u.grouped_ids)
  AND c.id <> u.keep_id;
