package com.neueda.app.dtos;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The execution engine's decision journal as the admin journal page sees it. The app passes these through
 * from the engine's internal API (EngineClient); times are UTC ISO strings, as written in the journal.
 * Fields that are always set are marked required, so the generated UI client does not type them as optional;
 * the unmarked ones can be null.
 */
public final class JournalDtos {

    private JournalDtos() {
    }

    /** A day that has a journal. */
    public record JournalDay(
            @Schema(requiredMode = REQUIRED) String day,
            @Schema(requiredMode = REQUIRED) long sizeBytes) {
    }

    public record JournalReasonCount(
            @Schema(requiredMode = REQUIRED) String reason,
            @Schema(requiredMode = REQUIRED) int count) {
    }

    /** One order of the day and where it ended up: FILLED, REJECTED, CANCELLED, WAITING or QUEUED. */
    public record JournalOrderRow(
            @Schema(requiredMode = REQUIRED) String orderId,
            @Schema(requiredMode = REQUIRED) String receivedAt,
            @Schema(requiredMode = REQUIRED) String accountId,
            @Schema(requiredMode = REQUIRED) String symbol,
            @Schema(requiredMode = REQUIRED) String side,
            @Schema(requiredMode = REQUIRED) String orderType,
            Integer quantity,
            BigDecimal limitPrice,
            @Schema(requiredMode = REQUIRED) String outcome,
            BigDecimal fillPrice,
            String reason,
            @Schema(requiredMode = REQUIRED) int resends) {
    }

    public record JournalDaySummary(
            @Schema(requiredMode = REQUIRED) String day,
            @Schema(requiredMode = REQUIRED) int engineRuns,
            @Schema(requiredMode = REQUIRED) int quotes,
            @Schema(requiredMode = REQUIRED) int orders,
            @Schema(requiredMode = REQUIRED) int fills,
            @Schema(requiredMode = REQUIRED) int rejects,
            @Schema(requiredMode = REQUIRED) int waits,
            @Schema(requiredMode = REQUIRED) int queued,
            @Schema(requiredMode = REQUIRED) int cancels,
            @Schema(requiredMode = REQUIRED) List<JournalReasonCount> rejectReasons,
            String firstTime,
            String lastTime,
            @Schema(requiredMode = REQUIRED) List<JournalOrderRow> orderRows) {
    }

    public record JournalOrder(
            @Schema(requiredMode = REQUIRED) String orderId,
            @Schema(requiredMode = REQUIRED) String accountId,
            @Schema(requiredMode = REQUIRED) String symbol,
            @Schema(requiredMode = REQUIRED) String side,
            @Schema(requiredMode = REQUIRED) String orderType,
            Integer quantity,
            BigDecimal price) {
    }

    public record JournalQuote(
            @Schema(requiredMode = REQUIRED) String symbol,
            @Schema(requiredMode = REQUIRED) BigDecimal bid,
            @Schema(requiredMode = REQUIRED) BigDecimal ask,
            @Schema(requiredMode = REQUIRED) String asOf) {
    }

    /** A journal line about one order; a decision carries the last quote for its symbol before it. */
    public record JournalTimelineEntry(
            @Schema(requiredMode = REQUIRED) long seq,
            @Schema(requiredMode = REQUIRED) String time,
            @Schema(requiredMode = REQUIRED) String type,
            String decision,
            BigDecimal price,
            String reason,
            JournalOrder order,
            JournalQuote quote) {
    }

    public record JournalDifference(
            Long seq,
            @Schema(requiredMode = REQUIRED) String orderId,
            String recorded,
            String replayed) {
    }

    /** A replay of the day with the running engine version: did every recorded decision come out the same? */
    public record JournalVerifyResult(
            @Schema(requiredMode = REQUIRED) String day,
            @Schema(requiredMode = REQUIRED) int engineRuns,
            @Schema(requiredMode = REQUIRED) int orders,
            @Schema(requiredMode = REQUIRED) int quotes,
            @Schema(requiredMode = REQUIRED) int decisions,
            @Schema(requiredMode = REQUIRED) boolean matches,
            @Schema(requiredMode = REQUIRED) List<JournalDifference> differences) {
    }
}
