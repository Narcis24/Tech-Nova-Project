import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Account, AccountService } from '../../services/account.service';
import { AccountsComponent } from './accounts.component';

const ACCOUNTS = [
  { accountId: 'ACC001', holderName: 'Trader One' },
  { accountId: 'ACC002', holderName: 'Trader One' },
] as Account[];

describe('AccountsComponent', () => {
  const accounts = signal<Account[]>(ACCOUNTS);
  const currentAccountId = signal<string | null>('ACC001');
  let accountService: { accounts: typeof accounts; currentAccountId: typeof currentAccountId; selectAccount: jasmine.Spy };
  let fixture: ComponentFixture<AccountsComponent>;

  beforeEach(async () => {
    accounts.set(ACCOUNTS);
    currentAccountId.set('ACC001');
    accountService = { accounts, currentAccountId, selectAccount: jasmine.createSpy('selectAccount') };

    await TestBed.configureTestingModule({
      imports: [AccountsComponent],
      providers: [{ provide: AccountService, useValue: accountService }],
    })
    .compileComponents();

    fixture = TestBed.createComponent(AccountsComponent);
    fixture.detectChanges();
  });

  function options(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.account'));
  }

  it('renders its heading', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Accounts');
  });

  it("lists every account, marking the current one", () => {
    expect(options().map(option => option.querySelector('.account-id')?.textContent)).toEqual(['ACC001', 'ACC002']);
    expect(options()[0].getAttribute('aria-current')).toBe('true');
    expect(options()[1].hasAttribute('aria-current')).toBeFalse();
  });

  it('selects the tapped account', () => {
    options()[1].click();

    expect(accountService.selectAccount).toHaveBeenCalledWith('ACC002');
  });

  it('says so when there are no accounts', () => {
    accounts.set([]);
    fixture.detectChanges();

    expect(options().length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain("You don't have any accounts yet.");
  });
});
