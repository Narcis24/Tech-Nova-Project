import { Component, computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Account, AccountService } from '../../services/account.service';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';
import { NavBarComponent } from './nav-bar.component';

// An empty page for the tab links to point at
@Component({ template: '' })
class BlankComponent {}

const ACCOUNTS = [{ accountId: 'ACC001' }, { accountId: 'ACC002' }, { accountId: 'ACC003' }] as Account[];

describe('NavBarComponent', () => {
  const username = signal<string | null>('trader01');
  const accounts = signal<Account[]>(ACCOUNTS);
  const currentAccountId = signal<string | null>('ACC001');
  const isAdmin = signal(false);
  const homeUrl = computed(() => (isAdmin() ? '/admin' : '/dashboard'));
  let auth: { username: typeof username; isAdmin: typeof isAdmin; homeUrl: typeof homeUrl; logout: jasmine.Spy };
  let accountService: {
    accounts: typeof accounts;
    currentAccountId: typeof currentAccountId;
    selectAccount: jasmine.Spy;
  };
  let theme: jasmine.SpyObj<ThemeService>;
  let component: NavBarComponent;
  let fixture: ComponentFixture<NavBarComponent>;

  beforeEach(async () => {
    username.set('trader01');
    accounts.set(ACCOUNTS);
    currentAccountId.set('ACC001');
    isAdmin.set(false);
    auth = { username, isAdmin, homeUrl, logout: jasmine.createSpy('logout') };
    accountService = { accounts, currentAccountId, selectAccount: jasmine.createSpy('selectAccount') };
    theme = jasmine.createSpyObj<ThemeService>('ThemeService', ['toggle'], {
      isDark: signal(false),
    });

    await TestBed.configureTestingModule({
      imports: [NavBarComponent],
      providers: [
        provideRouter([
          { path: 'dashboard', component: BlankComponent },
          { path: 'trade', component: BlankComponent },
          { path: 'orders', component: BlankComponent },
          { path: 'admin', component: BlankComponent },
          { path: 'admin/users', component: BlankComponent },
        ]),
        { provide: AuthService, useValue: auth },
        { provide: AccountService, useValue: accountService },
        { provide: ThemeService, useValue: theme },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NavBarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('marks only the current tab with aria-current', async () => {
    await TestBed.inject(Router).navigateByUrl('/trade');
    fixture.detectChanges();

    const current: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('[aria-current="page"]'));
    expect(current.map((tab) => tab.textContent?.trim())).toEqual(['Trade']);
  });

  describe('for an admin', () => {
    const tabs = (): (string | undefined)[] =>
      Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.tab')).map((tab) => tab.textContent?.trim());

    it('shows the trading tabs to traders', () => {
      expect(tabs()).toEqual(['Portfolio', 'Trade', 'Orders']);
    });

    it('shows only the admin tabs, no trading ones', () => {
      isAdmin.set(true);
      fixture.detectChanges();

      expect(tabs()).toEqual(['Overview', 'Users', 'Journal']);
    });

    it('marks only Users as current on /admin/users, not Overview too', async () => {
      isAdmin.set(true);
      fixture.detectChanges();
      await TestBed.inject(Router).navigateByUrl('/admin/users');
      fixture.detectChanges();

      const current: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('[aria-current="page"]'));
      expect(current.map((tab) => tab.textContent?.trim())).toEqual(['Users']);
    });

    it('points the logo at the home page', () => {
      expect(fixture.nativeElement.querySelector('.brand').getAttribute('href')).toBe('/dashboard');

      isAdmin.set(true);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.brand').getAttribute('href')).toBe('/admin');
    });

    it('hides the account pill and the trading links in the avatar menu', () => {
      isAdmin.set(true);
      fixture.detectChanges();
      fixture.nativeElement.querySelector('.menu-button').click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.account')).toBeNull();
      expect(fixture.nativeElement.querySelector('.menu-account')).toBeNull();
      expect(fixture.nativeElement.querySelector('.menu-change-account')).toBeNull();
      expect(fixture.nativeElement.querySelector('.menu-sign-out')).not.toBeNull();
    });
  });

  it("shows the user's initial and name", () => {
    const text: string = fixture.nativeElement.querySelector('.user').textContent;

    expect(text).toContain('T');
    expect(text).toContain('trader01');
  });

  it('hides the user badge when logged out', () => {
    username.set(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.user')).toBeNull();
  });

  it('shows the current account', () => {
    const pill: HTMLElement = fixture.nativeElement.querySelector('.account');

    expect(pill.textContent).toContain('ACC001');
  });

  it('hides the account pill when no account is selected', () => {
    currentAccountId.set(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.account')).toBeNull();
  });

  it('toggles the theme from the accessible theme button', () => {
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.theme-toggle');

    expect(button.getAttribute('aria-label')).toBe('Switch to dark theme');
    expect(button.getAttribute('aria-pressed')).toBe('false');

    button.click();

    expect(theme.toggle).toHaveBeenCalled();
  });

  describe('account switcher', () => {
    function pill(): HTMLButtonElement {
      return fixture.nativeElement.querySelector('.account');
    }

    function options(): HTMLButtonElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll('.account-menu .account-option'));
    }

    it('starts closed', () => {
      expect(pill().getAttribute('aria-expanded')).toBe('false');
      expect(options().length).toBe(0);
    });

    it('lists every account when the pill is clicked, marking the current one', () => {
      pill().click();
      fixture.detectChanges();

      expect(pill().getAttribute('aria-expanded')).toBe('true');
      expect(options().map((option) => option.textContent?.trim())).toEqual(['ACC001', 'ACC002', 'ACC003']);
      expect(options()[0].getAttribute('aria-current')).toBe('true');
      expect(options()[1].hasAttribute('aria-current')).toBeFalse();
    });

    it('selects the clicked account and closes the list', () => {
      pill().click();
      fixture.detectChanges();

      options()[1].click();
      fixture.detectChanges();

      expect(accountService.selectAccount).toHaveBeenCalledWith('ACC002');
      expect(options().length).toBe(0);
    });

    it('closes on Escape', () => {
      pill().click();
      fixture.detectChanges();

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();

      expect(options().length).toBe(0);
    });
  });

  describe('phone menu', () => {
    function openMenu(): void {
      fixture.nativeElement.querySelector('.menu-button').click();
      fixture.detectChanges();
    }

    it('hides identity details and shows a link to change the account', () => {
      openMenu();

      expect(fixture.nativeElement.querySelector('.menu-name')).toBeNull();
      expect(fixture.nativeElement.querySelector('.menu-account')).toBeNull();
      const link: HTMLAnchorElement = fixture.nativeElement.querySelector('.menu-change-account');
      expect(link.getAttribute('href')).toBe('/accounts');
    });

    it('links to the risk limits page', () => {
      openMenu();

      expect(fixture.nativeElement.querySelector('.menu-risk-limits').getAttribute('href')).toBe('/risk-limits');
    });

    it('closes when Change account is clicked', () => {
      openMenu();

      fixture.nativeElement.querySelector('.menu-change-account').click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.menu')).toBeNull();
    });
  });

  // The menu's Sign out is only shown on phones (by CSS); the click still works whatever the screen size
  it('signs out from the avatar menu', () => {
    fixture.nativeElement.querySelector('.menu-button').click();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.menu-sign-out').click();

    expect(auth.logout).toHaveBeenCalled();
  });
});
