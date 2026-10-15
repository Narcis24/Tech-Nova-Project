package com.neueda.app.services;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against the local Compose database inside a rolled-back transaction. */
@EnabledIfEnvironmentVariable(named = "DASHBOARD_TEST_DB_URL", matches = ".+")
class BalanceHistoryServiceIntegrationTest {
    @Test void capturesActualValuesOnceAndSkipsIncompleteQuotes() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("DASHBOARD_TEST_DB_URL"), "technova", System.getenv("POSTGRES_PASSWORD"))) {
            connection.setAutoCommit(false);
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            String id = "TEST-" + UUID.randomUUID();
            String symbol = "T" + UUID.randomUUID().toString().substring(0, 7);
            LocalDate day = LocalDate.now().minusDays(10);
            try {
                jdbc.update("INSERT INTO instruments (symbol,name,asset_class,currency,tradable) VALUES (?, 'Test','ETF','USD',true)", symbol);
                jdbc.update("INSERT INTO accounts (account_id,holder_name,cash_balance,status,version,last_updated) VALUES (?, 'Test',100,'ACTIVE',0,CURRENT_TIMESTAMP)", id);
                jdbc.update("INSERT INTO positions (account_id,symbol,quantity,average_cost) VALUES (?,?,2,40)", id, symbol);
                var service = new BalanceHistoryService(jdbc);
                assertNull(service.previousSession(id).portfolioValue());
                service.captureSession(day);
                assertNull(service.previousSession(id).portfolioValue());
                jdbc.update("INSERT INTO price_data (symbol,trade_date,open,high,low,close,volume) VALUES (?,?,50,50,50,50,1)", symbol, day);
                service.captureSession(day);
                assertEquals(0, new java.math.BigDecimal("200").compareTo(service.previousSession(id).portfolioValue()));
                jdbc.update("UPDATE accounts SET cash_balance=150 WHERE account_id=?", id);
                service.captureSession(day);
                assertEquals(0, new java.math.BigDecimal("200").compareTo(service.previousSession(id).portfolioValue()));
                assertEquals(day, service.previousSession(id).sessionDate());
            } finally { connection.rollback(); }
        }
    }
}
