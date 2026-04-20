-- The original seed hash used in V18 contained '+' which BCryptPasswordEncoder rejects.
-- Fix existing installations that already have the broken hash in the persisted DB volume.
UPDATE app_users
SET password_hash = '$2a$10$z4r10y1/0C/ZdaFD328NQuVV.cQJQ4C/QSqUtZJX1G1k71efFkVIa'
WHERE LOWER(username) = 'admin'
  AND password_hash LIKE '%+%';
