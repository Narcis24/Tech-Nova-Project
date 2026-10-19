import { Routes } from '@angular/router';

import { NotFoundComponent } from './features/not-found/not-found.component';
import { authGuard } from './guards/auth.guard';
import { guestGuard } from './guards/guest.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
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
        path: 'pies/new',
        loadComponent: () => import('./features/pies/pie-editor.component').then((m) => m.PieEditorComponent),
      },
      {
        path: 'pies/:pieId/edit',
        loadComponent: () => import('./features/pies/pie-editor.component').then((m) => m.PieEditorComponent),
      },
      {
        path: 'pies/:pieId',
        loadComponent: () => import('./features/pies/pie-detail.component').then((m) => m.PieDetailComponent),
      },
      {
        path: 'pies',
        loadComponent: () => import('./features/pies/pies.component').then((m) => m.PiesComponent),
      },
      {
        path: 'discover/:pieId',
        loadComponent: () => import('./features/pies/public-pie.component').then((m) => m.PublicPieComponent),
      },
      {
        path: 'discover',
        loadComponent: () => import('./features/pies/discover.component').then((m) => m.DiscoverComponent),
      },
      {
        path: 'risk-limits',
        loadComponent: () => import('./features/risk-limits/risk-limits.component').then((m) => m.RiskLimitsComponent),
      },
      {
        path: 'rebalance-runs/:runId',
        loadComponent: () => import('./features/pies/rebalance-run.component').then((m) => m.RebalanceRunComponent),
      },
      {
        path: 'accounts/:accountId/rebalance',
        loadComponent: () =>
          import('./features/pies/rebalance-preview.component').then((m) => m.RebalancePreviewComponent),
      },
      {
        path: 'accounts',
        loadComponent: () => import('./features/accounts/accounts.component').then((m) => m.AccountsComponent),
      },
    ],
  },
  { path: '**', component: NotFoundComponent },
];
