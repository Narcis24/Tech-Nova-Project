package com.neueda.app.services;

import com.neueda.app.dtos.BalanceHistoryResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BalanceHistoryService {
    static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private final JdbcTemplate jdbc;

    public BalanceHistoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public BalanceHistoryResponse previousSession(String accountId) {
        return jdbc.query("""
            SELECT session_date, portfolio_value, cash_balance, captured_at
            FROM account_balance_history WHERE account_id = ? AND session_date < ?
            ORDER BY session_date DESC LIMIT 1
            """, (rs, row) -> new BalanceHistoryResponse(rs.getObject(1, LocalDate.class),
                rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getObject(4, OffsetDateTime.class)),
            accountId, LocalDate.now(MARKET_ZONE)).stream().findFirst()
            .orElse(new BalanceHistoryResponse(null, null, null, null));
    }

    /** After the US close. Skip holidays/missing feeds and incomplete valuations instead of inventing history.
     * The price pipeline must have loaded today's close by this time. Repeated runs never overwrite a snapshot.
     */
    @Scheduled(cron = "${dashboard.balance-snapshot-cron:0 15 16 * * MON-FRI}", zone = "America/New_York")
    public void captureSession() { captureSession(LocalDate.now(MARKET_ZONE)); }

    int captureSession(LocalDate sessionDate) {
        return jdbc.update("""
            INSERT INTO account_balance_history (account_id, session_date, cash_balance, portfolio_value)
            SELECT a.account_id, ?, a.cash_balance,
                   a.cash_balance + COALESCE(SUM(p.quantity * q.close), 0)
            FROM accounts a
            LEFT JOIN positions p ON p.account_id = a.account_id AND p.quantity > 0
            LEFT JOIN price_data q ON q.symbol = p.symbol AND q.trade_date = ?
            WHERE EXISTS (SELECT 1 FROM price_data WHERE trade_date = ?)
            GROUP BY a.account_id, a.cash_balance
            HAVING COUNT(p.symbol) = COUNT(q.symbol)
            ON CONFLICT (account_id, session_date) DO NOTHING
            """, sessionDate, sessionDate, sessionDate);
    }
}
