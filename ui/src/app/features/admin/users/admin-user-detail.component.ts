import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import {
  AccountResponse,
  AdminUserControllerService,
  AdminUserDetailResponse,
} from '../../../generated/trading-api-client';

@Component({
  selector: 'app-admin-user-detail',
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './admin-user-detail.component.html',
  styleUrls: ['./admin-user-detail.component.css'],
})
export class AdminUserDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly adminUsers = inject(AdminUserControllerService);
  readonly user = signal<AdminUserDetailResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly updatingAccountId = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    const username = this.route.snapshot.paramMap.get('username');
    if (!username) {
      this.error.set(true);
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set(false);
    this.adminUsers.getUser(username).subscribe({
      next: (user) => {
        this.user.set(user);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  changeStatus(account: AccountResponse): void {
    const user = this.user();
    if (!user || this.updatingAccountId()) {
      return;
    }

    const nextStatus =
      account.status === AccountResponse.StatusEnum.Active
        ? AccountResponse.StatusEnum.Suspended
        : AccountResponse.StatusEnum.Active;
    if (
      nextStatus === AccountResponse.StatusEnum.Suspended &&
      !window.confirm(`Suspend account ${account.accountId}?`)
    ) {
      return;
    }

    this.updatingAccountId.set(account.accountId);
    this.adminUsers.updateAccountStatus(user.username, account.accountId, { status: nextStatus }).subscribe({
      next: () => {
        this.updatingAccountId.set(null);
        this.load();
      },
      error: () => this.updatingAccountId.set(null),
    });
  }
}
