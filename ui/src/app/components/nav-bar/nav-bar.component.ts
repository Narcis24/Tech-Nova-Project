import { Component, ElementRef, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../services/auth.service';
import { AccountService } from '../../services/account.service';

interface NavTab {
  label: string;
  path: string;
}

/**
 * Top bar of the signed-in app. The account pill opens a list for switching accounts.
 * Under 768px the tabs move to a bottom bar, and the account list and Sign out move into the avatar menu.
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
  readonly tabs: readonly NavTab[] = [
    { label: 'Portfolio', path: '/dashboard' },
    { label: 'Trade', path: '/trade' },
    { label: 'Orders', path: '/orders' },
  ];

  private readonly auth = inject(AuthService);
  private readonly accountService = inject(AccountService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly username = computed(() => this.auth.username() ?? 'testtrader'); // TEMP: preview only. Remove before committing.
  readonly initial = computed(() => this.username()?.charAt(0).toUpperCase() ?? '');

  readonly accounts = this.accountService.accounts;
  readonly accountId = this.accountService.currentAccountId;

  /** The account list under the pill */
  readonly accountMenuOpen = signal(false);
  /** The phone-only avatar menu */
  readonly menuOpen = signal(false);

  toggleAccountMenu(): void {
    this.accountMenuOpen.update(open => !open);
  }

  toggleMenu(): void {
    this.menuOpen.update(open => !open);
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
}
