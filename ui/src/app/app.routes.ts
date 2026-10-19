import { inject } from '@angular/core';
import { Routes } from '@angular/router';

import { NotFoundComponent } from './features/not-found/not-found.component';
import { adminGuard } from './guards/admin.guard';
import { authGuard } from './guards/auth.guard';
import { guestGuard } from './guards/guest.guard';
import { AuthService } from './services/auth.service';

export const routes: Routes = [
  // Each user starts on their home page. Logged out: straight to /login with no returnUrl, so after
  // logging in an admin lands on /admin rather than the /dashboard a returnUrl would have pinned.
  {
    path: '',
    pathMatch: 'full',
    redirectTo: () => {
      const auth = inject(AuthService);
      return auth.isAuthenticated() ? auth.homeUrl() : '/login';
    },
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'forgot-password',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/forgot-password/forgot-password.component').then((m) => m.ForgotPasswordComponent),
  },
  {
    // No guard: the link from the email must work even if someone is signed in on this browser
    path: 'reset-password',
    loadComponent: () =>
      import('./features/reset-password/reset-password.component').then((m) => m.ResetPasswordComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'trade',
        loadComponent: () => import('./pages/order-ticket/order-ticket.component').then((m) => m.OrderTicketComponent),
      },
      {
        path: 'orders/:orderId',
        loadComponent: () => import('./pages/order-result/order-result.component').then((m) => m.OrderResultComponent),
      },
      {
        path: 'orders',
        loadComponent: () => import('./features/orders/orders.component').then((m) => m.OrdersComponent),
      },
      {
        path: 'risk-limits',
        loadComponent: () => import('./features/risk-limits/risk-limits.component').then((m) => m.RiskLimitsComponent),
      },
      {
        path: 'accounts',
        loadComponent: () => import('./features/accounts/accounts.component').then((m) => m.AccountsComponent),
      },
      {
        path: 'admin',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/admin/admin-dashboard.component').then((m) => m.AdminDashboardComponent),
      },
      {
        path: 'admin/users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/admin/users/admin-users.component').then((m) => m.AdminUsersComponent),
      },
    ],
  },
  { path: '**', component: NotFoundComponent },
];
