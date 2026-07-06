ALTER TABLE telegram_links
    ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE telegram_notification_deliveries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    telegram_link_id BIGINT NOT NULL REFERENCES telegram_links(id) ON DELETE CASCADE,
    reminder_id VARCHAR(180) NOT NULL,
    reminder_type VARCHAR(40) NOT NULL,
    remind_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tg_notification_once UNIQUE (user_id, reminder_id, remind_at)
);

CREATE INDEX idx_tg_notifications_user ON telegram_notification_deliveries(user_id);
CREATE INDEX idx_tg_notifications_remind_at ON telegram_notification_deliveries(remind_at);
CREATE INDEX idx_tg_notifications_link ON telegram_notification_deliveries(telegram_link_id);
