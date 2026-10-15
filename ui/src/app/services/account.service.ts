import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';

import { AccountControllerService, AccountResponse } from '../generated/trading-api-client';

// Generated from the trading API's live spec; run `npm run fetch:trading-spec && npm run generate:trading-client` after it changes
export type Account = AccountResponse;

/** The user's accounts, and which one the UI is showing. The first account is selected after loading. */
@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private readonly accountApi = inject(AccountControllerService);

  readonly accounts = signal<Account[]>([]);
  readonly currentAccountId = signal<string | null>(null);

  /** Loads the user's accounts and selects the first. Never errors: a failed load leaves no accounts. */
  loadAccounts(): Observable<void> {
    return this.accountApi.getMyAccounts().pipe(
      tap(accounts => {
        this.accounts.set(accounts);
        this.currentAccountId.set(accounts[0]?.accountId ?? null);
      }),
      map(() => undefined),
      catchError(() => {
        this.accounts.set([]);
        this.currentAccountId.set(null);
        return of(undefined);
      }),
    );
  }

  selectAccount(accountId: string): void {
    this.currentAccountId.set(accountId);
  }

  /** Opens a new, empty account for the logged-in user, adds it to the list and selects it. Errors reach the caller. */
  openAccount(holderName: string): Observable<Account> {
    return this.accountApi.openAccount({ holderName }).pipe(
      tap(account => {
        this.accounts.update(accounts => [...accounts, account]);
        this.currentAccountId.set(account.accountId);
      }),
    );
  }
}
