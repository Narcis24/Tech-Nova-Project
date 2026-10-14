import { Component, inject } from '@angular/core';

import { AccountService } from '../../services/account.service';

/** Lists the user's accounts and lets them pick which one the app shows. Reached from the phone avatar menu. */
@Component({
  selector: 'app-accounts',
  imports: [],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.css',
})
export class AccountsComponent {
  private readonly accountService = inject(AccountService);

  readonly accounts = this.accountService.accounts;
  readonly currentAccountId = this.accountService.currentAccountId;

  selectAccount(accountId: string): void {
    this.accountService.selectAccount(accountId);
  }
}
