package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * The execution engine's decision journal as the admin journal page sees it. The app passes these through
 * from the engine's internal API (EngineClient); times are UTC ISO strings, as written in the journal.
 * Fields that are always set are marked required, so the generated UI client does not type them as optional.
 */
public final class JournalDtos {

    private JournalDtos() {
    }

    /** A day that has a journal. */
    public record JournalDay(@Schema(requiredMode = RequiredMode.REQUIRED) String day, @Schema(requiredMode = RequiredMode.REQUIRED) long sizeBytes) {
    }

    public record JournalReasonCount(@Schema(requiredMode = RequiredMode.REQUIRED) String reason, @Schema(requiredMode = RequiredMode.REQUIRED) int count) {
    }

    /** One order of the day and where it ended up: FILLED, REJECTED, CANCELLED, WAITING or QUEUED. */
    public record JournalOrderRow(@Schema(requiredMode = RequiredMode.REQUIRED) String orderId, @Schema(requiredMode = RequiredMode.REQUIRED) String receivedAt, @Schema(requiredMode = RequiredMode.REQUIRED) String accountId, @Schema(requiredMode = RequiredMode.REQUIRED) String symbol, @Schema(requiredMode = RequiredMode.REQUIRED) String side,
            @Schema(requiredMode = RequiredMode.REQUIRED) String orderType, Integer quantity, BigDecimal limitPrice, @Schema(requiredMode = RequiredMode.REQUIRED) String outcome, BigDecimal fillPrice,
            String reason, @Schema(requiredMode = RequiredMode.REQUIRED) int resends) {
    }

    public record JournalDaySummary(@Schema(requiredMode = RequiredMode.REQUIRED) String day, @Schema(requiredMode = RequiredMode.REQUIRED) int engineRuns, @Schema(requiredMode = RequiredMode.REQUIRED) int quotes, @Schema(requiredMode = RequiredMode.REQUIRED) int orders, @Schema(requiredMode = RequiredMode.REQUIRED) int fills, @Schema(requiredMode = RequiredMode.REQUIRED) int rejects,
            @Schema(requiredMode = RequiredMode.REQUIRED) int waits, @Schema(requiredMode = RequiredMode.REQUIRED) int queued, @Schema(requiredMode = RequiredMode.REQUIRED) int cancels, @Schema(requiredMode = RequiredMode.REQUIRED) List<JournalReasonCount> rejectReasons, String firstTime,
            String lastTime, @Schema(requiredMode = RequiredMode.REQUIRED) List<JournalOrderRow> orderRows) {
    }

    public record JournalOrder(@Schema(requiredMode = RequiredMode.REQUIRED) String orderId, @Schema(requiredMode = RequiredMode.REQUIRED) String accountId, @Schema(requiredMode = RequiredMode.REQUIRED) String symbol, @Schema(requiredMode = RequiredMode.REQUIRED) String side, @Schema(requiredMode = RequiredMode.REQUIRED) String orderType,
            Integer quantity, BigDecimal price) {
    }

    public record JournalQuote(@Schema(requiredMode = RequiredMode.REQUIRED) String symbol, @Schema(requiredMode = RequiredMode.REQUIRED) BigDecimal bid, @Schema(requiredMode = RequiredMode.REQUIRED) BigDecimal ask, @Schema(requiredMode = RequiredMode.REQUIRED) String asOf) {
    }

    /** A journal line about one order; a decision carries the last quote for its symbol before it. */
    public record JournalTimelineEntry(@Schema(requiredMode = RequiredMode.REQUIRED) long seq, @Schema(requiredMode = RequiredMode.REQUIRED) String time, @Schema(requiredMode = RequiredMode.REQUIRED) String type, String decision, BigDecimal price,
            String reason, JournalOrder order, JournalQuote quote) {
    }

    public record JournalDifference(Long seq, @Schema(requiredMode = RequiredMode.REQUIRED) String orderId, String recorded, String replayed) {
    }

    /** A replay of the day with the running engine version: did every recorded decision come out the same? */
    public record JournalVerifyResult(@Schema(requiredMode = RequiredMode.REQUIRED) String day, @Schema(requiredMode = RequiredMode.REQUIRED) int engineRuns, @Schema(requiredMode = RequiredMode.REQUIRED) int orders, @Schema(requiredMode = RequiredMode.REQUIRED) int quotes, @Schema(requiredMode = RequiredMode.REQUIRED) int decisions,
            @Schema(requiredMode = RequiredMode.REQUIRED) boolean matches, @Schema(requiredMode = RequiredMode.REQUIRED) List<JournalDifference> differences) {
    }
}
