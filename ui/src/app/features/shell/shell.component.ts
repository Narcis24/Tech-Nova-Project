import { Component, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs';

import { NavBarComponent } from '../../components/nav-bar/nav-bar.component';
import { AccountService } from '../../services/account.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, NavBarComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  private readonly router = inject(Router);
  readonly tradePage = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url.split('?')[0] === '/trade'),
      startWith(this.router.url.split('?')[0] === '/trade'),
    ),
  );
  constructor() {
    inject(AccountService).loadAccounts().subscribe();
  }
}
