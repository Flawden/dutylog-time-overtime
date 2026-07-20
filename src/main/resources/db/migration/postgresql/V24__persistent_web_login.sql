-- Persistent web login tokens for the explicit "remember me" option.
-- The actual password is never stored here. Each browser receives a random
-- series/token pair; changing a password or role removes all tokens for that user.
CREATE TABLE persistent_logins (
    series VARCHAR(64) PRIMARY KEY,
    username VARCHAR(40) NOT NULL,
    token VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL
);

CREATE INDEX idx_persistent_logins_username ON persistent_logins(username);
