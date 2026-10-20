import { Component, ElementRef, computed, inject, input, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../services/auth.service';
import { AccountService } from '../../services/account.service';
import { ThemeService } from '../../services/theme.service';

interface NavTab {
  label: string;
  path: string;
  /** Only active on this exact URL, so /admin isn't also highlighted on /admin/users */
  exact?: boolean;
}

const TRADER_TABS: readonly NavTab[] = [
  { label: 'Portfolio', path: '/dashboard' },
  { label: 'Trade', path: '/trade' },
  { label: 'Orders', path: '/orders' },
];

const ADMIN_TABS: readonly NavTab[] = [
  { label: 'Overview', path: '/admin', exact: true },
  { label: 'Users', path: '/admin/users' },
];

/**
 * Top bar of the signed-in app. The account pill opens a list for switching accounts.
 * The avatar opens a menu with the account; Sign out sits beside it. Under 768px the tabs move to a bottom bar
 * and the header shrinks to just the avatar, whose menu then holds Sign out too.
 */
@Component({
  selector: 'app-nav-bar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './nav-bar.component.html',
  styleUrl: './nav-bar.component.css',
  host: {
    '(document:keydown.escape)': 'closeMenus()',
    '(document:click)': 'closeMenusOnOutsideClick($event)',
  },
})
export class NavBarComponent {
  readonly compactTrade = input(false);
  private readonly auth = inject(AuthService);

  /** Admins see only the admin pages and traders only the trading ones; the guards and the API still decide */
  readonly isAdmin = this.auth.isAdmin;
  readonly tabs = computed(() => (this.isAdmin() ? ADMIN_TABS : TRADER_TABS));
  readonly homeUrl = this.auth.homeUrl;

  private readonly accountService = inject(AccountService);
  readonly theme = inject(ThemeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly username = this.auth.username;
  readonly initial = computed(() => this.username()?.charAt(0).toUpperCase() ?? '');

  readonly accounts = this.accountService.accounts;
  readonly accountId = this.accountService.currentAccountId;

  /** The account list under the pill */
  readonly accountMenuOpen = signal(false);
  /** The menu under the avatar */
  readonly menuOpen = signal(false);

  toggleAccountMenu(): void {
    this.accountMenuOpen.update((open) => !open);
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  selectAccount(accountId: string): void {
    this.accountService.selectAccount(accountId);
    this.closeMenus();
  }

  closeMenus(): void {
    this.accountMenuOpen.set(false);
    this.menuOpen.set(false);
  }

  closeMenusOnOutsideClick(event: Event): void {
    const target = event.target as Node;
    if (!this.host.nativeElement.querySelector('.account-switcher')?.contains(target)) {
      this.accountMenuOpen.set(false);
    }
    if (!this.host.nativeElement.querySelector('.user')?.contains(target)) {
      this.menuOpen.set(false);
    }
  }

  signOut(): void {
    this.closeMenus();
    this.auth.logout();
  }

  toggleTheme(): void {
    this.theme.toggle();
  }
}
