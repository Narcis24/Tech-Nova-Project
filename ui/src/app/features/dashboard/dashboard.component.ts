import { CurrencyPipe, DecimalPipe, PercentPipe, TitleCasePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AccountService } from '../../services/account.service';
import { CashActionsComponent } from './cash-actions.component';
import { DashboardStore } from './dashboard.store';

@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, DecimalPipe, PercentPipe, TitleCasePipe, RouterLink, CashActionsComponent],
  providers: [DashboardStore],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent {
  readonly store = inject(DashboardStore);
  private readonly accountService = inject(AccountService);
  readonly account = computed(() => this.accountService.accounts().find(a => a.accountId === this.accountService.currentAccountId()));
  readonly Math = Math;
}
