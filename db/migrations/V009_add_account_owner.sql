-- An account belongs to the user who opened it; app refuses (403) any request on someone else's.
-- Seed accounts have no owner, so nobody can reach them until claimed (db/scripts/claim-seed-accounts.sh).
ALTER TABLE accounts ADD COLUMN owner_username VARCHAR(50) REFERENCES users(username);

CREATE INDEX idx_accounts_owner_username ON accounts(owner_username);
