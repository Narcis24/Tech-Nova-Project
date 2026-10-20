import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AdminUserControllerService, AdminUserResponse } from '../../generated/trading-api-client';
import { AuthService } from '../../services/auth.service';
import { AnalyticsCardComponent } from './analytics/analytics-card.component';

/**
 * The admin's home page: user counts from GET /v1/admin/users, then trading activity in the
 * analytics card. The full user list is on the users page.
 */
@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink, AnalyticsCardComponent],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['../../shared/admin-page.css', './admin-dashboard.component.css'],
})
export class AdminDashboardComponent {
  private readonly adminUsers = inject(AdminUserControllerService);

  readonly username = inject(AuthService).username;
  readonly users = signal<AdminUserResponse[] | null>(null);
  readonly error = signal(false);

  readonly stats = computed(() => {
    const users = this.users() ?? [];
    return [
      { label: 'Users', value: users.length },
      { label: 'Admins', value: users.filter((user) => user.roles.includes('ADMIN')).length },
      { label: 'Traders', value: users.filter((user) => user.roles.includes('TRADER')).length },
      { label: 'Created by admins', value: users.filter((user) => user.createdBy).length },
    ];
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.error.set(false);
    this.adminUsers.listUsers().subscribe({
      next: (users) => this.users.set(users),
      error: () => this.error.set(true),
    });
  }
}
