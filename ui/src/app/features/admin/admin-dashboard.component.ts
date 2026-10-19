import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';

import { AdminUserControllerService, AdminUserResponse } from '../../generated/trading-api-client';
import { AuthService } from '../../services/auth.service';

const RECENT_USERS = 5;

/** The admin's home page: who uses TechNova, at a glance. Data from GET /v1/admin/users. */
@Component({
  selector: 'app-admin-dashboard',
  imports: [DatePipe],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.css',
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

  /** Newest first; ISO timestamps sort correctly as text */
  readonly recentUsers = computed(() =>
    [...(this.users() ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, RECENT_USERS),
  );

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
