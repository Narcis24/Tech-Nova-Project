-- Email for password reset. Existing users have none, so they cannot reset until they add one.
ALTER TABLE users ADD COLUMN email VARCHAR(254);
-- One account per email, ignoring case (John@x.com and john@x.com are the same address)
CREATE UNIQUE INDEX users_email_key ON users (LOWER(email));

-- Password reset tokens issued by auth-service. Only a SHA-256 hash of the token is stored.
-- A token is usable while used_at is null and expires_at is in the future.
CREATE TABLE password_reset_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    username   VARCHAR(50) NOT NULL REFERENCES users(username) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ
);
CREATE INDEX password_reset_tokens_username_idx ON password_reset_tokens (username);
