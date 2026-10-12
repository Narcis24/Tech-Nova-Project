import { Routes } from '@angular/router';

import { NotFoundComponent } from './features/not-found/not-found.component';

export const routes: Routes = [
    {path: '', pathMatch: 'full', redirectTo: 'dashboard'},
    {
        path: 'login',
        loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent),
    },
    {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
    },
    { path: '**', component: NotFoundComponent },
];
