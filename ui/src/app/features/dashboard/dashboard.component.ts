import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe, TitleCasePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PositionResponse } from '../../generated/trading-api-client';
import { AccountService } from '../../services/account.service';
import { CashActionsComponent } from './cash-actions.component';
import { BalanceCardComponent } from './balance-card.component';
import { PositionCardComponent } from './position-card.component';
import { PositionSummaryComponent } from './position-summary.component';
import { MarketOverviewComponent } from './market-overview.component';
import { ClosePositionComponent } from './close-position.component';
import { DashboardStore } from './dashboard.store';
import { AccountPieSummaryComponent } from '../pies/account-pie-summary.component';
import { SymbolIconComponent } from '../../components/symbol-icon/symbol-icon.component';
@Component({
  selector: 'app-dashboard',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    PercentPipe,
    TitleCasePipe,
    RouterLink,
    CashActionsComponent,
    BalanceCardComponent,
    PositionCardComponent,
    PositionSummaryComponent,
    MarketOverviewComponent,
    ClosePositionComponent,
    AccountPieSummaryComponent,
    SymbolIconComponent,
  ],
  providers: [DashboardStore],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent {
  readonly store = inject(DashboardStore);
  private readonly accounts = inject(AccountService);
  readonly account = computed(() =>
    this.accounts.accounts().find((a) => a.accountId === this.accounts.currentAccountId()),
  );
  readonly pendingSells = computed(
    () =>
      new Set(
        this.store
          .state()
          .orders.filter(
            (o) =>
              o.side?.toUpperCase() === 'SELL' && ['PENDING', 'PUBLISHED', 'PARTIALLY_FILLED'].includes(o.status ?? ''),
          )
          .map((o) => o.symbol ?? ''),
      ),
  );
  readonly closing = signal<PositionResponse | null>(null);
  readonly feedback = signal<string | null>(null);
  readonly Math = Math;
  constructor() {
    effect(() => {
      this.accounts.currentAccountId();
      this.closing.set(null);
      this.feedback.set(null);
    });
  }
  closed(message: string): void {
    this.closing.set(null);
    this.feedback.set(message);
    this.store.refresh();
  }
}
