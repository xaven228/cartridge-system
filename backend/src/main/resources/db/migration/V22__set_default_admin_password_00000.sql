UPDATE app_users
SET password_hash = '$2y$10$SG1rmvjRNlO1uO3GIdzhTOqHiCV9GFil4XuylZVB9j8uM4Gq.Uwbu'
WHERE LOWER(username) = 'admin';
