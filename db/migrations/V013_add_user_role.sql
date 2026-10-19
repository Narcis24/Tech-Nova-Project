-- Roles carried in the access token's roles claim. A user can hold several (an admin who also trades).
-- The first admin is granted by hand (db/scripts/make-admin.sh), never through public registration.
CREATE TABLE user_roles (
    username VARCHAR(50) NOT NULL REFERENCES users(username) ON DELETE CASCADE,
    role     VARCHAR(20) NOT NULL CONSTRAINT chk_user_roles_role CHECK (role IN ('TRADER', 'ADMIN')),
    PRIMARY KEY (username, role)
);

-- Everyone registered so far is a trader
INSERT INTO user_roles (username, role) SELECT username, 'TRADER' FROM users;

ALTER TABLE users
    -- An account an admin creates has no password until its owner sets one from the emailed link.
    -- Login treats a missing hash as a wrong password.
    ALTER COLUMN password_hash DROP NOT NULL,
    -- Who created the account: the admin's username, or null for self-registration
    ADD COLUMN created_by VARCHAR(50) REFERENCES users(username) ON DELETE SET NULL,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
