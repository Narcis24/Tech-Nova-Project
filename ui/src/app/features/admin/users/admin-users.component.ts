import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';

import { AdminUserControllerService, AdminUserResponse } from '../../../generated/trading-api-client';

/** Lets admins see every user, change their roles and create new users. */
@Component({
  selector: 'app-admin-users',
  imports: [DatePipe],
  templateUrl: './admin-users.component.html',
  styleUrls: ['../../../shared/admin-page.css', './admin-users.component.css'],
})
export class AdminUsersComponent {
  private readonly adminUsers = inject(AdminUserControllerService);

  /** null until the first load finishes, so "loading" and "no users" look different */
  readonly users = signal<AdminUserResponse[] | null>(null);
  readonly error = signal(false);

  constructor() {
    this.load();
  }

  /** Also used by "Try again" */
  load(): void {
    this.error.set(false);
    this.adminUsers.listUsers().subscribe({
      next: (users) => this.users.set(users),
      error: () => this.error.set(true),
    });
  }
}
