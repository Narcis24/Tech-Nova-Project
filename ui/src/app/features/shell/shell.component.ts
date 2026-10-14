import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { NavBarComponent } from '../../components/nav-bar/nav-bar.component';
import { Account, AccountService } from '../../services/account.service';

// TEMP: preview data for a test user, used when the backend returns nothing. Remove before committing.
const PREVIEW_ACCOUNTS: Account[] = ['ACC001', 'ACC002', 'ACC003'].map(accountId => ({
  accountId,
  holderName: 'Test Trader',
  cashBalance: 50000,
  accountStatus: 'ACTIVE',
  lastUpdated: '',
}));

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, NavBarComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css'
})
export class ShellComponent {
  constructor() {
    const accounts = inject(AccountService);
    accounts.loadAccounts().subscribe(() => {
      // TEMP: preview only. Remove before committing.
      if (!accounts.accounts().length) {
        accounts.accounts.set(PREVIEW_ACCOUNTS);
        accounts.selectAccount(PREVIEW_ACCOUNTS[0].accountId);
      }
    });
  }
}
