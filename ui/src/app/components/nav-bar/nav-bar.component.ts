import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../services/auth.service';
import { AccountService } from '../../services/account.service';

interface NavTab {
  label: string;
  path: string;
}

@Component({
  selector: 'app-nav-bar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './nav-bar.component.html',
  styleUrl: './nav-bar.component.css'
})
export class NavBarComponent {
  readonly tabs: readonly NavTab[] = [
    { label: 'Portfolio', path: '/dashboard' },
    { label: 'Trade', path: '/trade' },
    { label: 'Orders', path: '/orders' },
  ];

  private readonly auth = inject(AuthService);
  
  readonly username = this.auth.username;
  readonly initial = computed(() => this.username()?.charAt(0).toUpperCase() ?? '');

  readonly accountId = inject(AccountService).currentAccountId;
  signOut(): void {
    this.auth.logout();
  }
}
