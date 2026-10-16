import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';

import { RiskLimitControllerService, RiskLimitResponse } from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';

type Rule = NonNullable<RiskLimitResponse['rule']>;

/** One rule's card: what the server says, what the user typed, and the state of its last save. */
interface LimitCard {
  limit: RiskLimitResponse;
  draft: string;
  saving: boolean;
  error: string | null;
}

const WORDING: Record<Rule, { title: string; sentence: string }> = {
  SINGLE_INSTRUMENT: { title: 'Single stock', sentence: 'Warn me when one stock is over' },
  ASSET_CLASS: { title: 'Asset class', sentence: 'Warn me when one asset class is over' },
  CASH_FLOOR: { title: 'Cash', sentence: 'Warn me when cash falls below' },
};

// 0 to 100 with up to two decimals, the same range the API accepts
const THRESHOLD_PATTERN = /^\d{1,3}(\.\d{1,2})?$/;

/** The account holder's pre-trade risk limits: turn each rule on or off, set its threshold, or reset it to the default. */
@Component({
  selector: 'app-risk-limits',
  imports: [DecimalPipe],
  templateUrl: './risk-limits.component.html',
  styleUrl: './risk-limits.component.css',
})
export class RiskLimitsComponent {
  private readonly api = inject(RiskLimitControllerService);
  readonly accountId = inject(AccountService).currentAccountId;

  readonly loading = signal(false);
  readonly loadError = signal(false);
  readonly cards = signal<LimitCard[]>([]);
  readonly wording = WORDING;
  private readonly revision = signal(0);
  private readonly request = computed(() => ({ id: this.accountId(), revision: this.revision() }));

  constructor() {
    // Reloads when the account changes or the user retries
    toObservable(this.request)
      .pipe(
        switchMap(({ id }) => {
          if (!id) return of({ loading: false, error: false, limits: [] as RiskLimitResponse[] });
          return this.api.getLimits(id).pipe(
            map((limits) => ({ loading: false, error: false, limits })),
            catchError(() => of({ loading: false, error: true, limits: [] as RiskLimitResponse[] })),
            startWith({ loading: true, error: false, limits: [] as RiskLimitResponse[] }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ loading, error, limits }) => {
        this.loading.set(loading);
        this.loadError.set(error);
        this.cards.set(limits.map((limit) => toCard(limit)));
      });
  }

  retry(): void {
    this.revision.update((v) => v + 1);
  }

  setDraft(rule: Rule, value: string): void {
    this.patch(rule, { draft: value, error: null });
  }

  /** True when the typed threshold is a valid change worth saving. */
  canSave(card: LimitCard): boolean {
    const draft = card.draft.trim();
    if (card.saving || !THRESHOLD_PATTERN.test(draft)) return false;
    const value = Number(draft);
    return value >= 0 && value <= 100 && value !== Number(card.limit.thresholdPct);
  }

  invalid(card: LimitCard): boolean {
    const draft = card.draft.trim();
    return draft !== '' && (!THRESHOLD_PATTERN.test(draft) || Number(draft) > 100);
  }

  saveThreshold(card: LimitCard): void {
    if (!this.canSave(card)) return;
    this.save(
      card,
      this.api.updateLimit(this.accountId()!, card.limit.rule!, {
        thresholdPct: Number(card.draft.trim()),
        enabled: card.limit.enabled,
      }),
    );
  }

  toggle(card: LimitCard): void {
    if (card.saving) return;
    this.save(
      card,
      this.api.updateLimit(this.accountId()!, card.limit.rule!, {
        thresholdPct: Number(card.limit.thresholdPct),
        enabled: !card.limit.enabled,
      }),
    );
  }

  reset(card: LimitCard): void {
    if (card.saving) return;
    this.save(card, this.api.resetLimit(this.accountId()!, card.limit.rule!));
  }

  private save(card: LimitCard, request: Observable<RiskLimitResponse>): void {
    const rule = card.limit.rule!;
    const accountId = this.accountId();
    this.patch(rule, { saving: true, error: null });
    request.subscribe({
      next: (limit) => {
        // The user may have switched account while this was saving
        if (this.accountId() !== accountId) return;
        this.cards.update((cards) => cards.map((c) => (c.limit.rule === rule ? toCard(limit) : c)));
      },
      error: (error: unknown) => this.patch(rule, { saving: false, error: saveError(error) }),
    });
  }

  private patch(rule: Rule, changes: Partial<LimitCard>): void {
    this.cards.update((cards) => cards.map((c) => (c.limit.rule === rule ? { ...c, ...changes } : c)));
  }
}

function toCard(limit: RiskLimitResponse): LimitCard {
  return {
    limit,
    draft: limit.thresholdPct == null ? '' : String(Number(limit.thresholdPct)),
    saving: false,
    error: null,
  };
}

function saveError(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 400) {
    const message = error.error?.message;
    if (typeof message === 'string' && message.trim()) return message;
  }
  return "Couldn't save. Try again.";
}
