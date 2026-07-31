CREATE TABLE calendar_feed_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    token_hint VARCHAR(12) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rotated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_calendar_feed_subscription_user UNIQUE (user_id),
    CONSTRAINT uk_calendar_feed_subscription_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_calendar_feed_subscription_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_calendar_feed_subscription_lookup
    ON calendar_feed_subscriptions(token_hash);
