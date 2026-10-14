import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, tap } from 'rxjs';

import { API_BASE } from '../core/api-urls';

/** One account, as returned by GET /api/v1/accounts (see app/openapi.yml). */
export interface Account {
  accountId: string;
  holderName: string;
  cashBalance: number;
  accountStatus: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
  lastUpdated: string;
}

/**
 * Tracks which of the user's accounts the UI is showing.
 * For now it is always the first account the API returns; switching accounts comes in a later story.
 */
@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private readonly http = inject(HttpClient);

  readonly currentAccountId = signal<string | null>(null);
  /** Loads the user's accounts and selects the first. Never errors: a failed load leaves no account selected. */
  loadAccounts(): Observable<void> {
    return this.http.get<Account[]>(`${API_BASE}/v1/accounts`).pipe(
      tap(accounts => this.currentAccountId.set(accounts[0]?.accountId ?? null)),
      map(() => undefined),
      catchError(() => {
        this.currentAccountId.set(null);
        return of(undefined);
      }),
    );
  }

}
