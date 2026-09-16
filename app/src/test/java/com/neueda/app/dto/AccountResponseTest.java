package com.neueda.app.dto;

import com.neueda.app.enums.AccountStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;


public class AccountResponseTest {
    @Test 
    public void accountResponseTest() {

        String accountID = "12345L";
        BigDecimal cash_balance = new BigDecimal("2500.50");
        AccountStatus status = AccountStatus.ACTIVE;

        AccountResponse response = new AccountResponse();
        response.setAccountID(accountID);
        response.setCash_balance(cash_balance);
        response.setStatus(status);

        assertEquals(accountID, response.getAccountID());
        assertEquals(cash_balance, response.getCash_balance());
        assertEquals(status, response.getStatus());

    }
}
