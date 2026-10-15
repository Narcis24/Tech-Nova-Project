import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { Account, AccountService } from '../../services/account.service';
import { AccountsComponent } from './accounts.component';

const ACCOUNTS = [
  { accountId: 'ACC001', holderName: 'Trader One' },
  { accountId: 'ACC002', holderName: 'Trader One' },
] as Account[];

describe('AccountsComponent', () => {
  const accounts = signal<Account[]>(ACCOUNTS);
  const currentAccountId = signal<string | null>('ACC001');
  let accountService: {
    accounts: typeof accounts;
    currentAccountId: typeof currentAccountId;
    selectAccount: jasmine.Spy;
    openAccount: jasmine.Spy;
  };
  let fixture: ComponentFixture<AccountsComponent>;

  beforeEach(async () => {
    accounts.set(ACCOUNTS);
    currentAccountId.set('ACC001');
    accountService = {
      accounts,
      currentAccountId,
      selectAccount: jasmine.createSpy('selectAccount'),
      openAccount: jasmine.createSpy('openAccount'),
    };

    await TestBed.configureTestingModule({
      imports: [AccountsComponent],
      providers: [{ provide: AccountService, useValue: accountService }],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountsComponent);
    fixture.detectChanges();
  });

  function options(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.account'));
  }

  it('renders its heading', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Accounts');
  });

  it('lists every account, marking the current one', () => {
    expect(options().map((option) => option.querySelector('.account-id')?.textContent)).toEqual(['ACC001', 'ACC002']);
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

  describe('opening a new account', () => {
    function find<T extends Element>(selector: string): T | null {
      return fixture.nativeElement.querySelector(selector);
    }

    function openForm(): void {
      find<HTMLButtonElement>('.open-button')!.click();
      fixture.detectChanges();
    }

    function typeHolderName(name: string): void {
      const input = find<HTMLInputElement>('#holder-name')!;
      input.value = name;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    function pressOpen(): void {
      find<HTMLButtonElement>('button[type="submit"]')!.click();
      fixture.detectChanges();
    }

    it('shows the form only after the button is tapped', () => {
      expect(find('.open-form')).toBeNull();

      openForm();

      expect(find('.open-form')).not.toBeNull();
      expect(find('.open-button')).toBeNull();
    });

    it('asks for a holder name instead of sending an empty one', () => {
      openForm();

      pressOpen();

      expect(fixture.nativeElement.textContent).toContain('Enter the holder name.');
      expect(accountService.openAccount).not.toHaveBeenCalled();
    });

    it('opens the account with the trimmed name and closes the form', () => {
      accountService.openAccount.and.returnValue(of({ accountId: 'ACC-NEW' } as Account));
      openForm();

      typeHolderName('  Test Trader  ');
      pressOpen();

      expect(accountService.openAccount).toHaveBeenCalledWith('Test Trader');
      expect(find('.open-form')).toBeNull();
    });

    it('disables the button while the request is in flight', () => {
      const pending = new Subject<Account>();
      accountService.openAccount.and.returnValue(pending);
      openForm();
      typeHolderName('Test Trader');

      pressOpen();

      const button = find<HTMLButtonElement>('button[type="submit"]')!;
      expect(button.disabled).toBeTrue();
      expect(button.textContent).toContain('Opening…');
    });

    it('shows an error and keeps the form open when the request fails', () => {
      accountService.openAccount.and.returnValue(throwError(() => new Error('400')));
      openForm();
      typeHolderName('Test Trader');

      pressOpen();

      expect(find('[role="alert"]')?.textContent).toContain("Couldn't open the account. Please try again.");
      expect(find('.open-form')).not.toBeNull();
      expect(find<HTMLButtonElement>('button[type="submit"]')!.disabled).toBeFalse();
    });

    it('closes and clears the form on Cancel', () => {
      openForm();
      typeHolderName('Half typed');

      find<HTMLButtonElement>('.secondary')!.click();
      fixture.detectChanges();
      openForm();

      expect(find<HTMLInputElement>('#holder-name')!.value).toBe('');
    });
  });
});
