-- v20.6: отдельный служебный профиль диагностики.
-- Первый созданный пользователь становится администратором существующей установки.
ALTER TABLE users ADD COLUMN IF NOT EXISTS role varchar(20) NOT NULL DEFAULT 'USER';

UPDATE users
SET role = 'ADMIN'
WHERE id = (SELECT MIN(id) FROM users)
  AND COALESCE(role, 'USER') = 'USER';
