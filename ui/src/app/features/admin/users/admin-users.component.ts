import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  AdminUserControllerService,
  AdminUserResponse,
  CreateUserRequest,
  CreateUserResponse,
} from '../../../generated/trading-api-client';

type Role = CreateUserRequest.RolesEnum;

// Something@domain.tld: Angular's Validators.email also accepts 'john@example', which the register page refuses too
const EMAIL_PATTERN = /^[^\s@]+@[^\s@.]+\.[^\s@]+$/;

/** Lets admins see every user, change their roles and create new users. */
@Component({
  selector: 'app-admin-users',
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './admin-users.component.html',
  styleUrls: ['../../../shared/admin-page.css', './admin-users.component.css'],
})
export class AdminUsersComponent {
  private readonly adminUsers = inject(AdminUserControllerService);
  private readonly formBuilder = inject(FormBuilder).nonNullable;

  /** Every role the API knows, so a new role shows up here once the client is regenerated */
  readonly allRoles = Object.values(CreateUserRequest.RolesEnum);

  /** null until the first load finishes, so "loading" and "no users" look different */
  readonly users = signal<AdminUserResponse[] | null>(null);
  readonly error = signal(false);

  /** What the admin typed in the search box */
  readonly query = signal('');

  readonly saving = signal(false);
  readonly createError = signal('');
  /** The account just created, kept after the form clears so the confirmation stays on screen */
  readonly created = signal<CreateUserResponse | null>(null);

  // Same rules as CreateUserRequest.java; new accounts start as admins
  readonly createForm = this.formBuilder.group({
    username: [
      '',
      [Validators.required, Validators.minLength(8), Validators.maxLength(16), Validators.pattern(/^\w+$/)],
    ],
    email: ['', [Validators.required, Validators.pattern(EMAIL_PATTERN), Validators.maxLength(254)]],
    // One role per account for now; the API takes a list, so it is sent as a list of one
    roles: [[CreateUserRequest.RolesEnum.Admin] as Role[], Validators.required],
  });

  /** Users whose username or email contains the search text, ignoring case; everyone when it is empty */
  readonly visibleUsers = computed(() => {
    const users = this.users() ?? [];
    const query = this.query().trim().toLowerCase();
    if (!query) {
      return users;
    }

    return users.filter(
      (user) => user.username.toLowerCase().includes(query) || user.email?.toLowerCase().includes(query),
    );
  });

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

  search(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  get f() {
    return this.createForm.controls;
  }

  showFieldErrors(field: 'username' | 'email'): boolean {
    return this.f[field].dirty || this.f[field].touched;
  }

  hasRole(role: Role): boolean {
    return this.f.roles.value.includes(role);
  }

  selectRole(role: Role): void {
    this.f.roles.setValue([role]);
    this.f.roles.markAsDirty();
  }

  create(): void {
    // A disabled submit button doesn't stop Enter in a field from submitting the form again
    if (this.saving()) {
      return;
    }
    this.createError.set('');
    this.created.set(null);
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.adminUsers.createUser(this.createForm.getRawValue()).subscribe({
      next: (response) => {
        this.saving.set(false);
        this.created.set(response);
        // Back to blank with ADMIN picked, ready for the next account
        this.createForm.reset();
        // Slot the new user in by username, the order the list arrives in
        this.users.update((users) =>
          [...(users ?? []), response.user].sort((a, b) => a.username.localeCompare(b.username)),
        );
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.createError.set(this.describeCreateError(err));
      },
    });
  }

  /** 409 says which of username or email is taken and 400 names the bad field; anything else is generic */
  private describeCreateError(err: HttpErrorResponse): string {
    if (err.status === 409 || err.status === 400) {
      return err.error?.message || 'Check the details and try again.';
    }
    return "Couldn't create the account. Please try again.";
  }
}
