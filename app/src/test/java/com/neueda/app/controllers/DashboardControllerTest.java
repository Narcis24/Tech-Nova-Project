package com.neueda.app.controllers;

import com.neueda.app.services.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DashboardControllerTest {
    @Test void checksOwnershipBeforeReadingBalanceHistory() {
        var access = mock(AccountAccess.class);
        var balances = mock(BalanceHistoryService.class);
        var market = mock(MarketOverviewService.class);
        var controller = new DashboardController(access, balances, market);
        controller.getBalanceHistory("ACC001");
        var ordered = inOrder(access, balances);
        ordered.verify(access).requireOwned("ACC001"); ordered.verify(balances).previousSession("ACC001");
    }
    @Test void refusesHistoryWhenOwnershipCheckFails() {
        var access = mock(AccountAccess.class); var balances = mock(BalanceHistoryService.class);
        doThrow(new IllegalArgumentException("not owned")).when(access).requireOwned("ACC002");
        assertThrows(IllegalArgumentException.class, () -> new DashboardController(access, balances, mock(MarketOverviewService.class)).getBalanceHistory("ACC002"));
        verifyNoInteractions(balances);
    }
}
