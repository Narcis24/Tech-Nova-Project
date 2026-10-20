import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AdminUserControllerService, AdminUserResponse } from '../../generated/trading-api-client';
import { AuthService } from '../../services/auth.service';
import { AnalyticsFiltersComponent } from './analytics/analytics-filters/analytics-filters.component';
import { AnalyticsOverviewComponent } from './analytics/analytics-overview/analytics-overview.component';
import { AnalyticsStore } from './analytics/analytics.store';

/**
 * The admin's home page: user counts from GET /v1/admin/users, then the trading activity widgets,
 * which share one AnalyticsStore provided here. The full user list is on the users page.
 */
@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink, AnalyticsFiltersComponent, AnalyticsOverviewComponent],
  providers: [AnalyticsStore],
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
