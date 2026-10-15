import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { API_BASE } from '../core/api-urls';
import { provideApi as provideTradingApi } from '../generated/trading-api-client';
import { Account, AccountService } from './account.service';

const ACCOUNTS_URL = '/api/v1/accounts';

describe('AccountService', () => {
  let service: AccountService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      // Same base path as app.config.ts, so requests go to /api/... instead of the spec's localhost URL
      providers: [provideHttpClient(), provideHttpClientTesting(), provideTradingApi(API_BASE)],
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

  describe('openAccount', () => {
    beforeEach(() => {
      service.accounts.set([{ accountId: 'ACC001' }] as Account[]);
      service.selectAccount('ACC001');
    });

    it('posts the holder name', () => {
      service.openAccount('Test Trader').subscribe();

      const req = http.expectOne(ACCOUNTS_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ holderName: 'Test Trader' });
      req.flush({ accountId: 'ACC-NEW' } as Account);
    });

    it('adds the new account and selects it', () => {
      let opened: Account | undefined;
      service.openAccount('Test Trader').subscribe(account => (opened = account));

      http.expectOne(ACCOUNTS_URL).flush({ accountId: 'ACC-NEW' } as Account);

      expect(opened?.accountId).toBe('ACC-NEW');
      expect(service.accounts().map(account => account.accountId)).toEqual(['ACC001', 'ACC-NEW']);
      expect(service.currentAccountId()).toBe('ACC-NEW');
    });

    it('passes failures to the caller and changes nothing', () => {
      let failed = false;
      service.openAccount('Test Trader').subscribe({ error: () => (failed = true) });

      http.expectOne(ACCOUNTS_URL).flush({ message: 'Holder name is required' }, { status: 400, statusText: 'Bad Request' });

      expect(failed).toBeTrue();
      expect(service.accounts().map(account => account.accountId)).toEqual(['ACC001']);
      expect(service.currentAccountId()).toBe('ACC001');
    });
  });
});
