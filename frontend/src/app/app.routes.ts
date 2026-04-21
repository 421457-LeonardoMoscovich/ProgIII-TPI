import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then(m => m.RegisterComponent),
  },
  {
    path: 'decks',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/deck-builder/deck-lobby/deck-lobby.component').then(m => m.DeckLobbyComponent),
  },
  {
    path: 'decks/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/deck-builder/deck-editor/deck-editor.component').then(m => m.DeckEditorComponent),
  },
  { path: '', redirectTo: '/decks', pathMatch: 'full' },
  { path: '**', redirectTo: '/login' },
];
