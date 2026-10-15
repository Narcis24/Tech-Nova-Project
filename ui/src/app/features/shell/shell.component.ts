import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { NavBarComponent } from '../../components/nav-bar/nav-bar.component';
import { AccountService } from '../../services/account.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, NavBarComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css'
})
export class ShellComponent {
  constructor() {
    inject(AccountService).loadAccounts().subscribe();
  }
}
