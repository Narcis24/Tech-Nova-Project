import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';

import {
  AdminJournalControllerService,
  JournalDay,
  JournalDaySummary,
  JournalOrderRow,
  JournalTimelineEntry,
  JournalVerifyResult,
} from '../../../generated/trading-api-client';

// Market time: "09:30" means the open. The journal itself is in UTC, shown on hover.
const NEW_YORK = new Intl.DateTimeFormat('en-US', {
  timeZone: 'America/New_York',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

const EVENT_LABELS: Record<string, string> = {
  ORDER_RECEIVED: 'Order received',
  ORDER_CANCELLED: 'Cancelled by the holder',
  ENGINE_STARTED: 'Engine restarted',
};

const DECISION_LABELS: Record<string, string> = {
  FILL: 'Filled',
  REJECT: 'Rejected',
  WAIT: 'Waiting for its price',
  QUEUED_MARKET_CLOSED: 'Queued: market closed',
};

/**
 * The execution engine's decision journal for admins: pick a day, see its totals and every order's
 * outcome, open an order's timeline with the quote behind each decision, download the raw journal,
 * and verify the day by replaying it with the running engine.
 */
@Component({
  selector: 'app-admin-journal',
  imports: [DecimalPipe],
  templateUrl: './admin-journal.component.html',
  styleUrls: ['../../../shared/admin-page.css', './admin-journal.component.css'],
})
export class AdminJournalComponent {
  private readonly api = inject(AdminJournalControllerService);

  /** null until loaded, so "loading" and "no journals" look different */
  readonly days = signal<JournalDay[] | null>(null);
  readonly selectedDay = signal<string | null>(null);
  readonly summary = signal<JournalDaySummary | null>(null);
  readonly loadingDay = signal(false);
  readonly error = signal<string | null>(null);

  readonly selectedOrder = signal<JournalOrderRow | null>(null);
  readonly timeline = signal<JournalTimelineEntry[] | null>(null);
  readonly timelineError = signal<string | null>(null);

  readonly verifying = signal(false);
  readonly verifyResult = signal<JournalVerifyResult | null>(null);
  readonly verifyError = signal<string | null>(null);
  readonly downloading = signal(false);

  readonly tiles = computed(() => {
    const s = this.summary();
    if (!s) {
      return [];
    }
    return [
      { label: 'Orders', value: s.orders },
      { label: 'Filled', value: s.fills },
      { label: 'Rejected', value: s.rejects },
      { label: 'Waits', value: s.waits },
      { label: 'Queued', value: s.queued },
      { label: 'Cancelled', value: s.cancels },
      { label: 'Engine runs', value: s.engineRuns },
      { label: 'Quotes', value: s.quotes },
    ];
  });

  constructor() {
    this.loadDays();
  }

  loadDays(): void {
    this.error.set(null);
    this.api.days().subscribe({
      next: (days) => {
        this.days.set(days);
        if (days.length) {
          this.selectDay(days[0].day);
        }
      },
      error: (e) => {
        this.days.set([]);
        this.error.set(message(e, "Couldn't load the journal."));
      },
    });
  }

  selectDay(day: string): void {
    this.selectedDay.set(day);
    this.summary.set(null);
    this.closeOrder();
    this.verifyResult.set(null);
    this.verifyError.set(null);
    this.loadingDay.set(true);
    this.error.set(null);
    this.api.day(day).subscribe({
      next: (summary) => {
        this.loadingDay.set(false);
        this.summary.set(summary);
      },
      error: (e) => {
        this.loadingDay.set(false);
        this.error.set(message(e, "Couldn't load this day."));
      },
    });
  }

  openOrder(row: JournalOrderRow): void {
    const day = this.selectedDay();
    if (!day) {
      return;
    }
    this.selectedOrder.set(row);
    this.timeline.set(null);
    this.timelineError.set(null);
    this.api.timeline(day, row.orderId).subscribe({
      next: (entries) => this.timeline.set(entries),
      error: (e) => this.timelineError.set(message(e, "Couldn't load this order's timeline.")),
    });
  }

  closeOrder(): void {
    this.selectedOrder.set(null);
    this.timeline.set(null);
    this.timelineError.set(null);
  }

  verify(): void {
    const day = this.selectedDay();
    if (!day || this.verifying()) {
      return;
    }
    this.verifying.set(true);
    this.verifyResult.set(null);
    this.verifyError.set(null);
    this.api.verify(day).subscribe({
      next: (result) => {
        this.verifying.set(false);
        this.verifyResult.set(result);
      },
      error: (e) => {
        this.verifying.set(false);
        this.verifyError.set(message(e, "Couldn't verify this day."));
      },
    });
  }

  download(): void {
    const day = this.selectedDay();
    if (!day || this.downloading()) {
      return;
    }
    this.downloading.set(true);
    this.api.download(day).subscribe({
      next: (file) => {
        this.downloading.set(false);
        saveFile(file, `${day}.jsonl.gz`);
      },
      error: (e) => {
        this.downloading.set(false);
        this.error.set(message(e, "Couldn't download the journal."));
      },
    });
  }

  /** "09:31:05" in New York time */
  nyTime(iso: string | undefined | null): string {
    return iso ? NEW_YORK.format(new Date(iso)) : '';
  }

  eventLabel(entry: JournalTimelineEntry): string {
    return entry.decision
      ? (DECISION_LABELS[entry.decision] ?? entry.decision)
      : (EVENT_LABELS[entry.type] ?? entry.type);
  }

  outcomeLabel(row: JournalOrderRow): string {
    switch (row.outcome) {
      case 'FILLED':
        return 'Filled';
      case 'REJECTED':
        return 'Rejected';
      case 'CANCELLED':
        return 'Cancelled';
      case 'QUEUED':
        return 'Queued';
      default:
        return 'Waiting';
    }
  }

  sizeLabel(bytes: number): string {
    return bytes >= 1024 * 1024
      ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
      : `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
}

/** The API's message when it gave one (e.g. "No journal for that day or order"), otherwise a fallback */
function message(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse && typeof error.error?.message === 'string') {
    return error.error.message;
  }
  return fallback;
}

function saveFile(file: Blob, name: string): void {
  const url = URL.createObjectURL(file);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  URL.revokeObjectURL(url);
}
