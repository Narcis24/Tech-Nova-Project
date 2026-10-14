import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { Account, AccountService } from './account.service';

const ACCOUNTS_URL = '/api/v1/accounts';

describe('AccountService', () => {
  let service: AccountService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AccountService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('keeps every account and selects the first', () => {
    const accounts = [{ accountId: 'ACC001' }, { accountId: 'ACC002' }] as Account[];
    service.loadAccounts().subscribe();

    http.expectOne(ACCOUNTS_URL).flush(accounts);

    expect(service.accounts()).toEqual(accounts);
    expect(service.currentAccountId()).toBe('ACC001');
  });

  it('switches to another account', () => {
    service.selectAccount('ACC002');

    expect(service.currentAccountId()).toBe('ACC002');
  });

  it('selects nothing when the user has no accounts', () => {
    service.loadAccounts().subscribe();

    http.expectOne(ACCOUNTS_URL).flush([]);

    expect(service.currentAccountId()).toBeNull();
  });

  it('selects nothing when the request fails, without erroring', () => {
    let failed = false;
    let done = false;
    service.loadAccounts().subscribe({ error: () => (failed = true), complete: () => (done = true) });

    http.expectOne(ACCOUNTS_URL).flush(null, { status: 500, statusText: 'Server Error' });

    expect(service.accounts()).toEqual([]);
    expect(service.currentAccountId()).toBeNull();
    expect(failed).toBeFalse();
    expect(done).toBeTrue();
  });
});
