import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AdminUserControllerService, AdminUserResponse } from '../../generated/trading-api-client';
import { AuthService } from '../../services/auth.service';
import { AnalyticsFiltersComponent } from './analytics/analytics-filters/analytics-filters.component';
import { AnalyticsOverviewComponent } from './analytics/analytics-overview/analytics-overview.component';
import { AnalyticsStore } from './analytics/analytics.store';
import { AssetMixComponent } from './analytics/asset-mix/asset-mix.component';
import { LivePanelComponent } from './analytics/live-panel/live-panel.component';
import { TopTradedInstrumentsComponent } from './analytics/top-traded-instruments/top-traded-instruments.component';

// Remembers, in this browser, whether the admin made the chart narrower
const CHART_WIDTH_KEY = 'technova-admin-chart-width';

/**
 * The admin's home page: user counts from GET /v1/admin/users, then the trading activity widgets,
 * which share one AnalyticsStore provided here. The full user list is on the users page.
 */
@Component({
  selector: 'app-admin-dashboard',
  imports: [
    RouterLink,
    AnalyticsFiltersComponent,
    AnalyticsOverviewComponent,
    AssetMixComponent,
    LivePanelComponent,
    TopTradedInstrumentsComponent,
  ],
  providers: [AnalyticsStore],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['../../shared/admin-page.css', './admin-dashboard.component.css'],
})
export class AdminDashboardComponent {
  private readonly adminUsers = inject(AdminUserControllerService);
  private readonly document = inject(DOCUMENT);

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

  // Small (the default) puts Live beside the chart; full width puts Live in its own row below
  readonly chartFullWidth = signal(this.readChartWidth() === 'full');

  constructor() {
    this.load();
  }

  toggleChartWidth(): void {
    this.chartFullWidth.update((full) => !full);
    try {
      this.document.defaultView?.localStorage.setItem(CHART_WIDTH_KEY, this.chartFullWidth() ? 'full' : 'narrow');
    } catch {
      // Storage may be unavailable in privacy-restricted browser contexts; the choice then lasts the visit
    }
  }

  private readChartWidth(): string | null {
    try {
      return this.document.defaultView?.localStorage.getItem(CHART_WIDTH_KEY) ?? null;
    } catch {
      return null;
    }
  }

  load(): void {
    this.error.set(false);
    this.adminUsers.listUsers().subscribe({
      next: (users) => this.users.set(users),
      error: () => this.error.set(true),
    });
  }
}
