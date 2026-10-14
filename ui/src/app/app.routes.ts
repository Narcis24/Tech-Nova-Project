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
    path: 'dashboard',
    canMatch: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
  },
  { path: '**', component: NotFoundComponent },
];
