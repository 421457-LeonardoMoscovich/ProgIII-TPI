import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'decks',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/deck-builder/deck-lobby/deck-lobby.component').then(
        (m) => m.DeckLobbyComponent,
      ),
  },
  {
    path: 'decks/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/deck-builder/deck-editor/deck-editor.component').then(
        (m) => m.DeckEditorComponent,
      ),
  },
  {
    path: 'lobby',
    canActivate: [authGuard],
    loadComponent: () => import('./features/lobby/lobby.component').then((m) => m.LobbyComponent),
  },
  {
    path: 'match/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./features/match/match.component').then((m) => m.MatchComponent),
  },
  {
    path: 'ranking',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/ranking/ranking.component').then((m) => m.RankingComponent),
  },
  { path: '', redirectTo: '/lobby', pathMatch: 'full' },
  { path: '**', redirectTo: '/login' },
];
