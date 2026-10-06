package com.neueda.e2e.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * The accounts from db/migrations/V003_seed_data.sql. They have no owner, so nobody can reach
 * them over the API until one is claimed, as db/scripts/claim-seed-accounts.sh does locally.
 * Only for state the API cannot create, such as an INACTIVE account.
 */
public final class SeedAccounts {

    /** Seeded with status INACTIVE. */
    public static final String INACTIVE = "ACC005";

    /** Seeded ACTIVE and never claimed by any test. */
    public static final String UNCLAIMED = "ACC002";

    private SeedAccounts() {
    }

    public static void claim(String accountId, Trader owner) throws SQLException {
        try (Connection db = Stack.openDatabase();
             PreparedStatement claim = db.prepareStatement(
                 "UPDATE accounts SET owner_username = ? WHERE account_id = ? AND owner_username IS NULL")) {
            claim.setString(1, owner.username());
            claim.setString(2, accountId);
            if (claim.executeUpdate() != 1) {
                throw new IllegalStateException(accountId + " is missing or already claimed");
            }
        }
    }
}
