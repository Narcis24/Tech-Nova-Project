import { Component, computed, inject } from '@angular/core';

import { AccountService } from '../../services/account.service';

@Component({
  selector: 'app-dashboard',
  imports: [],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent {
  private readonly accountService = inject(AccountService);

  /** The selected account, or undefined while the accounts are loading or if there are none */
  readonly account = computed(() =>
    this.accountService.accounts().find(account => account.accountId === this.accountService.currentAccountId()),
  );
}
