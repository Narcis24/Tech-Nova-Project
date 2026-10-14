import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, tap } from 'rxjs';

import type { components } from '../api/trading-api';
import { API_BASE } from '../core/api-urls';

// Generated from contracts/trading-api.json; run `npm run generate:api` after the contract changes
/**
 * One account, as returned by GET /api/v1/accounts. The backend always fills every field, but the spec doesn't say
 * so yet (no `@Schema(requiredMode = REQUIRED)`), so the generated fields are all optional; `Required` restores that.
 */
export type Account = Required<components['schemas']['AccountResponse']>;
type OpenAccountRequest = components['schemas']['OpenAccountRequest'];

/** The user's accounts, and which one the UI is showing. The first account is selected after loading. */
@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private readonly http = inject(HttpClient);

  readonly accounts = signal<Account[]>([]);
  readonly currentAccountId = signal<string | null>(null);

  /** Loads the user's accounts and selects the first. Never errors: a failed load leaves no accounts. */
  loadAccounts(): Observable<void> {
    return this.http.get<Account[]>(`${API_BASE}/v1/accounts`).pipe(
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
    const body: OpenAccountRequest = { holderName };
    return this.http.post<Account>(`${API_BASE}/v1/accounts`, body).pipe(
      tap(account => {
        this.accounts.update(accounts => [...accounts, account]);
        this.currentAccountId.set(account.accountId);
      }),
    );
  }
}
