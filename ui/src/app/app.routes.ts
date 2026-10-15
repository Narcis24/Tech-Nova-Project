import { Routes } from '@angular/router';

import { NotFoundComponent } from './features/not-found/not-found.component';
import { authGuard } from './guards/auth.guard';
import { guestGuard } from './guards/guest.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'forgot-password',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent),
  },
  {
    // No guard: the link from the email must work even if someone is signed in on this browser
    path: 'reset-password',
    loadComponent: () =>
      import('./features/reset-password/reset-password.component').then(m => m.ResetPasswordComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell.component').then(m => m.ShellComponent),
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
      },
      {
        path: 'trade',
        loadComponent: () => import('./features/trade/trade.component').then(m => m.TradeComponent),
      },
      {
        path: 'orders',
        loadComponent: () => import('./features/orders/orders.component').then(m => m.OrdersComponent),
      },
      {
        path: 'accounts',
        loadComponent: () => import('./features/accounts/accounts.component').then(m => m.AccountsComponent),
      },
    ]
  },
  { path: '**', component: NotFoundComponent },
];
