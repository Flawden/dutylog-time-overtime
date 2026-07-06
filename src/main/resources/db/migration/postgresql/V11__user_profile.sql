-- Профиль пользователя: отображаемое имя и день рождения (оба опциональны)
ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name varchar(60);
ALTER TABLE users ADD COLUMN IF NOT EXISTS birthday date;
