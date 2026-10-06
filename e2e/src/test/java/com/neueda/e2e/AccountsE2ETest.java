package com.neueda.e2e;

import static com.neueda.e2e.support.OrderRequest.limitBuy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.neueda.e2e.support.SeedAccounts;
import com.neueda.e2e.support.Trader;

/** Opening and funding accounts, and the rule that an account belongs to the user who opened it. */
class AccountsE2ETest extends E2ETestBase {

    @Test
    @DisplayName("A new account starts empty; deposits and withdrawals move its balance")
    void depositAndWithdrawMoveTheBalance() {
        String account = me.openAccount("0.01");

        me.deposit(account, "500.00");
        me.withdraw(account, "120.50");

        assertAmount("379.51", me.balance(account));
    }

    @Test
    @DisplayName("Withdrawing more than the balance is refused with 400 and leaves it unchanged")
    void overdraftIsRefused() {
        String account = me.openAccount("100.00");

        me.cashMovement(account, "withdraw", "100.01").then().statusCode(400);

        assertAmount("100.00", me.balance(account));
    }

    @Test
    @DisplayName("A user lists only the accounts they opened")
    void accountListIsPerUser() {
        String mine = me.openAccount("100.00");
        Trader other = Trader.register();

        assertTrue(me.accountIds().contains(mine));
        assertEquals(List.of(), other.accountIds());
    }

    @Test
    @DisplayName("Another user cannot read, withdraw from or trade on my account")
    void someoneElsesAccountIsForbidden() {
        String mine = me.openAccount("1000.00");
        Trader other = Trader.register();

        other.request().get("/v1/accounts/" + mine).then().statusCode(403);
        other.request().get("/v1/accounts/" + mine + "/positions").then().statusCode(403);
        other.request().get("/v1/accounts/" + mine + "/snapshot").then().statusCode(403);
        other.cashMovement(mine, "withdraw", "10.00").then().statusCode(403);
        other.submit(limitBuy(mine, "AAPL", 1, "1.00")).then().statusCode(403);

        assertAmount("1000.00", me.balance(mine));
    }

    @Test
    @DisplayName("Another user cannot see or cancel my order")
    void someoneElsesOrderIsForbidden() {
        String mine = me.openAccount("1000.00");
        String orderId = me.place(limitBuy(mine, "MSFT", 1, "1.00"));
        Trader other = Trader.register();

        other.request().get("/v1/orders/" + orderId).then().statusCode(403);
        other.request().delete("/v1/orders/" + orderId).then().statusCode(403);

        assertTrue(me.order(orderId).isPending());
    }

    @Test
    @DisplayName("Unclaimed seed accounts are not reachable by anyone")
    void seedAccountsAreUnowned() {
        me.request().get("/v1/accounts/" + SeedAccounts.UNCLAIMED).then().statusCode(403);
    }
}
