-- Refresh tokens issued by auth-service. Only a SHA-256 hash of the token is stored.
-- A token is usable while revoked_at is null and expires_at is in the future; refresh and logout revoke it.
CREATE TABLE refresh_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    username VARCHAR(50) NOT NULL REFERENCES users(username) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);
