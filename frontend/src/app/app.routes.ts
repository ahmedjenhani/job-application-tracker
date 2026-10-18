import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/login/login-page').then((m) => m.LoginPage),
    title: 'Sign in - Job Tracker',
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/register/register-page').then((m) => m.RegisterPage),
    title: 'Create account - Job Tracker',
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'board' },
      {
        path: 'board',
        loadComponent: () => import('./pages/board/board-page').then((m) => m.BoardPage),
        title: 'Board - Job Tracker',
      },
      {
        path: 'list',
        loadComponent: () => import('./pages/list/list-page').then((m) => m.ListPage),
        title: 'List - Job Tracker',
      },
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard/dashboard-page').then((m) => m.DashboardPage),
        title: 'Dashboard - Job Tracker',
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
