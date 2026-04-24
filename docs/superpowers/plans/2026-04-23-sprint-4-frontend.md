# Sprint 4 Frontend — Lobby & Match Board Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement FE-07 (Lobby `/lobby`) and FE-08 (Match Board `/match/:id`) as Angular standalone components, matching the approved game-style UI mockups, polling the Sprint 4 REST API.

**Architecture:** Two lazy-loaded route components under `features/lobby/` and `features/match/`. A new `MatchService` handles all API calls (`GET /api/matches`, `POST /api/matches`, `POST /api/matches/{id}/join`, `GET /api/matches/{id}/state`). Polling uses `interval()` + `switchMap` with RxJS, started on component init and destroyed with `takeUntilDestroyed`. No WebSocket yet — deferred to Sprint 5.

**Tech Stack:** Angular 21 standalone components, signals, RxJS (`interval`, `switchMap`, `takeUntilDestroyed`), `HttpClient`, CSS custom properties (matches existing dark-theme design language from mockups).

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `frontend/src/app/core/models/match.model.ts` | Modify | Add `FilteredGameStateDto`, `MatchSummary` interfaces |
| `frontend/src/app/core/services/match.service.ts` | Create | All match-related HTTP calls |
| `frontend/src/app/features/lobby/lobby.component.ts` | Create | FE-07 lobby logic |
| `frontend/src/app/features/lobby/lobby.component.html` | Create | FE-07 lobby template |
| `frontend/src/app/features/lobby/lobby.component.scss` | Create | FE-07 game-style CSS |
| `frontend/src/app/features/match/match.component.ts` | Create | FE-08 board logic |
| `frontend/src/app/features/match/match.component.html` | Create | FE-08 board template |
| `frontend/src/app/features/match/match.component.scss` | Create | FE-08 board CSS |
| `frontend/src/app/app.routes.ts` | Modify | Add `/lobby` and `/match/:id` routes |

---

## Task 1: Extend match.model.ts with game state types

**Files:**
- Modify: `frontend/src/app/core/models/match.model.ts`

- [ ] **Step 1: Add MatchSummary and FilteredGameStateDto interfaces**

Open `frontend/src/app/core/models/match.model.ts` and replace its full content with:

```typescript
export type MatchStatus = 'WAITING' | 'SETUP' | 'ACTIVE' | 'FINISHED';
export type MatchResult = 'PLAYER1_WIN' | 'PLAYER2_WIN' | 'DRAW' | 'ABANDONED';

export interface Match {
  id: number;
  player1Id: number;
  player2Id: number | null;
  player1DeckId: number;
  player2DeckId: number | null;
  status: MatchStatus;
  result: MatchResult | null;
  winnerId: number | null;
  startedAt: string | null;
  finishedAt: string | null;
}

// Used by GET /api/matches (lobby list)
export interface MatchSummary {
  id: number;
  status: MatchStatus;
  player1Username: string;
  player2Username: string | null;
  createdAt: string;
  turnNumber: number;
}

// A single Pokémon on the field (filtered DTO from server)
export interface FieldPokemon {
  name: string;
  hp: number;
  maxHp: number;
  energies: Record<string, number>;
  statusCondition: string | null;
  attacks: Array<{ name: string; damage: number }>;
}

// Filtered state DTO from GET /api/matches/{id}/state
export interface FilteredGameStateDto {
  matchId: number;
  turnNumber: number;
  currentPlayerId: number;
  phase: 'DRAW' | 'MAIN' | 'ATTACK' | 'BETWEEN_TURNS';
  myPrizesLeft: number;
  opponentPrizesLeft: number;
  myHandCount: number;
  opponentHandCount: number;
  myDeckCount: number;
  opponentDeckCount: number;
  myActive: FieldPokemon | null;
  opponentActive: FieldPokemon | null;
  myBench: FieldPokemon[];
  opponentBench: FieldPokemon[];
  myHand: Array<{ id: string; name: string; type: string; supertype: string }>;
  winner: string | null;
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/core/models/match.model.ts
git commit -m "feat(fe): extend match model with MatchSummary and FilteredGameStateDto"
```

---

## Task 2: Create MatchService

**Files:**
- Create: `frontend/src/app/core/services/match.service.ts`

- [ ] **Step 1: Create the service**

```typescript
// frontend/src/app/core/services/match.service.ts
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { MatchSummary, FilteredGameStateDto } from '../models/match.model';

@Injectable({ providedIn: 'root' })
export class MatchService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/matches`;

  list() {
    return this.http.get<MatchSummary[]>(this.base);
  }

  create(deckId: number) {
    return this.http.post<{ id: number }>(this.base, { deckId });
  }

  join(matchId: number, deckId: number) {
    return this.http.post<void>(`${this.base}/${matchId}/join`, { deckId });
  }

  getState(matchId: number) {
    return this.http.get<FilteredGameStateDto>(`${this.base}/${matchId}/state`);
  }
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/core/services/match.service.ts
git commit -m "feat(fe): add MatchService for lobby and match state API calls"
```

---

## Task 3: Create Lobby component (FE-07)

**Files:**
- Create: `frontend/src/app/features/lobby/lobby.component.ts`
- Create: `frontend/src/app/features/lobby/lobby.component.html`
- Create: `frontend/src/app/features/lobby/lobby.component.scss`

- [ ] **Step 1: Create the TypeScript component**

```typescript
// frontend/src/app/features/lobby/lobby.component.ts
import { Component, inject, signal, OnInit, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { interval } from 'rxjs';
import { switchMap, startWith } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatchService } from '../../core/services/match.service';
import { DeckService } from '../../core/services/deck.service';
import { AuthService } from '../../core/services/auth.service';
import { MatchSummary } from '../../core/models/match.model';
import { Deck } from '../../core/models/deck.model';

@Component({
  selector: 'app-lobby',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './lobby.component.html',
  styleUrl: './lobby.component.scss',
})
export class LobbyComponent implements OnInit {
  private matchService = inject(MatchService);
  private deckService = inject(DeckService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  matches = signal<MatchSummary[]>([]);
  decks = signal<Deck[]>([]);
  loading = signal(true);
  error = signal('');

  showCreateModal = signal(false);
  showJoinModal = signal(false);
  selectedMatchId = signal<number | null>(null);
  selectedDeckId = signal<number | null>(null);
  creating = signal(false);
  joining = signal(false);

  get currentUser() {
    return this.authService.currentUser();
  }

  get waitingMatches(): MatchSummary[] {
    return this.matches().filter(m => m.status === 'WAITING');
  }

  get activeMatches(): MatchSummary[] {
    return this.matches().filter(m => m.status === 'ACTIVE');
  }

  ngOnInit() {
    this.deckService.list().subscribe({
      next: (decks) => this.decks.set(decks.filter(d => d.isValid)),
      error: () => this.error.set('Error al cargar mazos'),
    });

    interval(3000).pipe(
      startWith(0),
      switchMap(() => this.matchService.list()),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (matches) => { this.matches.set(matches); this.loading.set(false); },
      error: () => { this.error.set('Error al cargar partidas'); this.loading.set(false); },
    });
  }

  openCreate() {
    this.selectedDeckId.set(this.decks()[0]?.id ?? null);
    this.showCreateModal.set(true);
  }

  cancelCreate() { this.showCreateModal.set(false); }

  confirmCreate() {
    const deckId = this.selectedDeckId();
    if (!deckId) return;
    this.creating.set(true);
    this.matchService.create(deckId).subscribe({
      next: (res) => { this.showCreateModal.set(false); this.creating.set(false); this.router.navigate(['/match', res.id]); },
      error: () => { this.error.set('Error al crear partida'); this.creating.set(false); },
    });
  }

  openJoin(matchId: number) {
    this.selectedMatchId.set(matchId);
    this.selectedDeckId.set(this.decks()[0]?.id ?? null);
    this.showJoinModal.set(true);
  }

  cancelJoin() { this.showJoinModal.set(false); }

  confirmJoin() {
    const matchId = this.selectedMatchId();
    const deckId = this.selectedDeckId();
    if (!matchId || !deckId) return;
    this.joining.set(true);
    this.matchService.join(matchId, deckId).subscribe({
      next: () => { this.showJoinModal.set(false); this.joining.set(false); this.router.navigate(['/match', matchId]); },
      error: () => { this.error.set('Error al unirse a la partida'); this.joining.set(false); },
    });
  }

  watchMatch(matchId: number) {
    this.router.navigate(['/match', matchId]);
  }

  selectDeck(event: Event) {
    this.selectedDeckId.set(Number((event.target as HTMLSelectElement).value));
  }

  logout() { this.authService.logout(); }
}
```

- [ ] **Step 2: Create the HTML template**

```html
<!-- frontend/src/app/features/lobby/lobby.component.html -->
<div class="game-shell">

  <!-- Sidebar -->
  <aside class="sidebar">
    <div class="sidebar-brand">
      <span class="brand-icon">⚡</span>
      <span class="brand-name">PokéTCG</span>
    </div>
    <nav class="sidebar-nav">
      <a class="nav-item active">
        <span class="nav-icon">🎮</span> Jugar
      </a>
      <a class="nav-item" [routerLink]="['/decks']">
        <span class="nav-icon">📦</span> Mis Mazos
      </a>
    </nav>
    <div class="sidebar-user">
      <div class="user-avatar">{{ (currentUser ?? 'U')[0].toUpperCase() }}</div>
      <div class="user-info">
        <span class="user-name">{{ currentUser ?? 'Jugador' }}</span>
        <button class="btn-logout" (click)="logout()">Salir</button>
      </div>
    </div>
  </aside>

  <!-- Main -->
  <main class="main-area">

    @if (error()) {
      <div class="alert-error">{{ error() }}</div>
    }

    <!-- Hero -->
    <section class="hero">
      <div class="hero-content">
        <h1 class="hero-title">Sala de Partidas</h1>
        <p class="hero-sub">
          <span class="live-dot"></span>
          {{ waitingMatches.length }} esperando · {{ activeMatches.length }} en juego
        </p>
      </div>
      <button class="btn-game-primary" (click)="openCreate()" [disabled]="decks().length === 0">
        + Crear Partida
      </button>
    </section>

    @if (loading()) {
      <p class="loading-text">Cargando partidas...</p>
    } @else {

      <!-- Waiting -->
      @if (waitingMatches.length > 0) {
        <section class="match-section">
          <h2 class="section-title"><span class="badge badge-wait">ESPERANDO</span></h2>
          <div class="match-grid">
            @for (m of waitingMatches; track m.id) {
              <div class="match-card match-card--wait">
                <div class="match-host">{{ m.player1Username }}</div>
                <div class="match-meta">Partida #{{ m.id }}</div>
                <button class="btn-game-join" (click)="openJoin(m.id)" [disabled]="decks().length === 0">
                  Unirse →
                </button>
              </div>
            }
          </div>
        </section>
      }

      <!-- Active -->
      @if (activeMatches.length > 0) {
        <section class="match-section">
          <h2 class="section-title"><span class="badge badge-active">EN JUEGO</span></h2>
          <div class="match-grid">
            @for (m of activeMatches; track m.id) {
              <div class="match-card match-card--active">
                <div class="match-host">{{ m.player1Username }} vs {{ m.player2Username }}</div>
                <div class="match-meta">Turno {{ m.turnNumber }}</div>
                <button class="btn-game-watch" (click)="watchMatch(m.id)">Observar</button>
              </div>
            }
          </div>
        </section>
      }

      @if (waitingMatches.length === 0 && activeMatches.length === 0) {
        <p class="empty-state">No hay partidas activas. ¡Creá una!</p>
      }
    }

    <!-- Polling note -->
    <footer class="polling-note">
      Actualizando cada 3s · WebSocket en Sprint 5
    </footer>
  </main>
</div>

<!-- Create Modal -->
@if (showCreateModal()) {
  <div class="modal-overlay" (click)="cancelCreate()">
    <div class="modal" (click)="$event.stopPropagation()">
      <h2 class="modal-title">Crear Partida</h2>
      <label class="modal-label">Elegí tu mazo</label>
      <select class="modal-select" (change)="selectDeck($event)">
        @for (d of decks(); track d.id) {
          <option [value]="d.id">{{ d.name }}</option>
        }
      </select>
      <div class="modal-actions">
        <button class="btn-game-primary" (click)="confirmCreate()" [disabled]="creating()">
          {{ creating() ? 'Creando...' : 'Crear' }}
        </button>
        <button class="btn-game-secondary" (click)="cancelCreate()">Cancelar</button>
      </div>
    </div>
  </div>
}

<!-- Join Modal -->
@if (showJoinModal()) {
  <div class="modal-overlay" (click)="cancelJoin()">
    <div class="modal" (click)="$event.stopPropagation()">
      <h2 class="modal-title">Unirse a Partida</h2>
      <label class="modal-label">Elegí tu mazo</label>
      <select class="modal-select" (change)="selectDeck($event)">
        @for (d of decks(); track d.id) {
          <option [value]="d.id">{{ d.name }}</option>
        }
      </select>
      <div class="modal-actions">
        <button class="btn-game-primary" (click)="confirmJoin()" [disabled]="joining()">
          {{ joining() ? 'Uniéndose...' : 'Unirse' }}
        </button>
        <button class="btn-game-secondary" (click)="cancelJoin()">Cancelar</button>
      </div>
    </div>
  </div>
}
```

- [ ] **Step 3: Create the SCSS**

```scss
// frontend/src/app/features/lobby/lobby.component.scss
:host {
  display: block;
  height: 100vh;
  background: #080a10;
  color: #e2e8f0;
  font-family: 'Segoe UI', system-ui, sans-serif;
}

.game-shell {
  display: flex;
  height: 100vh;
}

// ── Sidebar ──────────────────────────────────────────
.sidebar {
  width: 220px;
  min-width: 220px;
  background: #0d1117;
  border-right: 1px solid #1e2433;
  display: flex;
  flex-direction: column;
  padding: 1.5rem 1rem;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: .6rem;
  margin-bottom: 2rem;
  .brand-icon { font-size: 1.5rem; }
  .brand-name { font-size: 1.1rem; font-weight: 700; color: #f8fafc; }
}

.sidebar-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: .25rem;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: .6rem;
  padding: .6rem .8rem;
  border-radius: 8px;
  cursor: pointer;
  color: #94a3b8;
  font-size: .9rem;
  text-decoration: none;
  transition: background .15s, color .15s;

  &:hover { background: #161d2e; color: #e2e8f0; }
  &.active { background: #1e2d4a; color: #60a5fa; font-weight: 600; }
  .nav-icon { font-size: 1rem; }
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: .75rem;
  padding-top: 1rem;
  border-top: 1px solid #1e2433;
}

.user-avatar {
  width: 36px;
  height: 36px;
  background: #3b4fd8;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: .9rem;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: .2rem;
  .user-name { font-size: .85rem; font-weight: 600; color: #f1f5f9; }
}

.btn-logout {
  background: none;
  border: none;
  color: #64748b;
  font-size: .75rem;
  cursor: pointer;
  padding: 0;
  text-align: left;
  &:hover { color: #ef4444; }
}

// ── Main ─────────────────────────────────────────────
.main-area {
  flex: 1;
  overflow-y: auto;
  padding: 2rem;
}

.alert-error {
  background: #2d1a1a;
  border: 1px solid #7f1d1d;
  color: #fca5a5;
  padding: .75rem 1rem;
  border-radius: 8px;
  margin-bottom: 1.5rem;
  font-size: .9rem;
}

// ── Hero ─────────────────────────────────────────────
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #1a1f35 0%, #0d1117 100%);
  border: 1px solid #1e2d4a;
  border-radius: 14px;
  padding: 1.75rem 2rem;
  margin-bottom: 2rem;
}

.hero-title {
  font-size: 1.6rem;
  font-weight: 800;
  color: #f8fafc;
  margin: 0 0 .4rem;
}

.hero-sub {
  display: flex;
  align-items: center;
  gap: .5rem;
  color: #94a3b8;
  font-size: .9rem;
  margin: 0;
}

.live-dot {
  width: 8px;
  height: 8px;
  background: #22c55e;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%       { opacity: .5; transform: scale(1.3); }
}

// ── Buttons ───────────────────────────────────────────
.btn-game-primary {
  background: #3b4fd8;
  color: #fff;
  border: none;
  padding: .7rem 1.4rem;
  border-radius: 8px;
  font-size: .95rem;
  font-weight: 600;
  cursor: pointer;
  transition: background .15s, transform .1s;
  &:hover:not(:disabled) { background: #4f63e8; transform: translateY(-1px); }
  &:disabled { opacity: .45; cursor: not-allowed; }
}

.btn-game-secondary {
  background: #1e2433;
  color: #94a3b8;
  border: 1px solid #2d3748;
  padding: .7rem 1.4rem;
  border-radius: 8px;
  font-size: .95rem;
  font-weight: 600;
  cursor: pointer;
  transition: background .15s;
  &:hover { background: #252d40; color: #e2e8f0; }
}

.btn-game-join {
  background: #065f46;
  color: #6ee7b7;
  border: none;
  padding: .5rem 1rem;
  border-radius: 6px;
  font-size: .85rem;
  font-weight: 600;
  cursor: pointer;
  transition: background .15s;
  &:hover:not(:disabled) { background: #047857; }
  &:disabled { opacity: .45; cursor: not-allowed; }
}

.btn-game-watch {
  background: #1e3a5f;
  color: #93c5fd;
  border: none;
  padding: .5rem 1rem;
  border-radius: 6px;
  font-size: .85rem;
  font-weight: 600;
  cursor: pointer;
  transition: background .15s;
  &:hover { background: #1e40af; }
}

// ── Sections & Cards ──────────────────────────────────
.match-section { margin-bottom: 2rem; }

.section-title {
  font-size: .8rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .05em;
  color: #64748b;
  margin: 0 0 1rem;
}

.badge {
  display: inline-block;
  padding: .2rem .6rem;
  border-radius: 4px;
  font-size: .75rem;
  font-weight: 700;
  letter-spacing: .08em;
}

.badge-wait   { background: #064e3b; color: #6ee7b7; }
.badge-active { background: #1e3a5f; color: #93c5fd; }

.match-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 1rem;
}

.match-card {
  background: #0d1117;
  border: 1px solid #1e2433;
  border-radius: 10px;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: .75rem;
  border-top: 3px solid transparent;
  transition: transform .15s, border-color .15s;

  &:hover { transform: translateY(-2px); }
  &--wait   { border-top-color: #22c55e; }
  &--active { border-top-color: #60a5fa; }
}

.match-host {
  font-size: .95rem;
  font-weight: 700;
  color: #f1f5f9;
}

.match-meta {
  font-size: .8rem;
  color: #64748b;
}

// ── States ────────────────────────────────────────────
.loading-text {
  color: #64748b;
  text-align: center;
  padding: 3rem 0;
  font-size: .95rem;
}

.empty-state {
  color: #64748b;
  text-align: center;
  padding: 3rem 0;
  font-size: .95rem;
}

// ── Footer ────────────────────────────────────────────
.polling-note {
  font-size: .75rem;
  color: #334155;
  text-align: center;
  padding-top: 2rem;
}

// ── Modal ─────────────────────────────────────────────
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  background: #0d1117;
  border: 1px solid #1e2d4a;
  border-radius: 14px;
  padding: 2rem;
  width: 340px;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.modal-title {
  font-size: 1.2rem;
  font-weight: 700;
  color: #f8fafc;
  margin: 0;
}

.modal-label {
  font-size: .85rem;
  color: #94a3b8;
}

.modal-select {
  background: #161d2e;
  border: 1px solid #2d3748;
  color: #e2e8f0;
  border-radius: 6px;
  padding: .6rem .8rem;
  font-size: .9rem;
  width: 100%;
}

.modal-actions {
  display: flex;
  gap: .75rem;
}
```

- [ ] **Step 4: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/lobby/
git commit -m "feat(fe): FE-07 Lobby component — game-style UI, 3s polling, create/join modals"
```

---

## Task 4: Create Match Board component (FE-08)

**Files:**
- Create: `frontend/src/app/features/match/match.component.ts`
- Create: `frontend/src/app/features/match/match.component.html`
- Create: `frontend/src/app/features/match/match.component.scss`

- [ ] **Step 1: Create the TypeScript component**

```typescript
// frontend/src/app/features/match/match.component.ts
import { Component, inject, signal, OnInit, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { interval } from 'rxjs';
import { switchMap, startWith } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatchService } from '../../core/services/match.service';
import { AuthService } from '../../core/services/auth.service';
import { FilteredGameStateDto, FieldPokemon } from '../../core/models/match.model';

@Component({
  selector: 'app-match',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './match.component.html',
  styleUrl: './match.component.scss',
})
export class MatchComponent implements OnInit {
  private matchService = inject(MatchService);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  matchId = signal<number>(0);
  state = signal<FilteredGameStateDto | null>(null);
  loading = signal(true);
  error = signal('');

  get currentUser() { return this.authService.currentUser(); }

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.matchId.set(id);

    interval(3000).pipe(
      startWith(0),
      switchMap(() => this.matchService.getState(id)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (s) => { this.state.set(s); this.loading.set(false); },
      error: () => { this.error.set('Error al cargar estado de partida'); this.loading.set(false); },
    });
  }

  hpPercent(pokemon: FieldPokemon): number {
    if (pokemon.maxHp === 0) return 0;
    return Math.round((pokemon.hp / pokemon.maxHp) * 100);
  }

  hpClass(pokemon: FieldPokemon): string {
    const pct = this.hpPercent(pokemon);
    if (pct > 50) return 'hp-high';
    if (pct > 25) return 'hp-mid';
    return 'hp-low';
  }

  goLobby() { this.router.navigate(['/lobby']); }
}
```

- [ ] **Step 2: Create the HTML template**

```html
<!-- frontend/src/app/features/match/match.component.html -->
<div class="board-shell">

  <!-- Top bar -->
  <header class="board-topbar">
    <button class="btn-back" (click)="goLobby()">← Lobby</button>
    <span class="board-title">Partida #{{ matchId() }}</span>
    @if (state()) {
      <span class="phase-badge">Turno {{ state()!.turnNumber }} · {{ state()!.phase }}</span>
    }
  </header>

  @if (error()) {
    <div class="alert-error">{{ error() }}</div>
  }

  @if (loading()) {
    <div class="loading-center">Cargando partida...</div>
  } @else if (state()) {

    @let s = state()!;

    <!-- Victory banner -->
    @if (s.winner) {
      <div class="winner-banner">
        🏆 {{ s.winner === currentUser ? '¡Ganaste!' : s.winner + ' ganó la partida' }}
      </div>
    }

    <div class="board-field">

      <!-- ── Opponent half (top) ── -->
      <section class="field-half field-opponent">
        <div class="field-stats">
          <span class="stat-pill">Premios: {{ s.opponentPrizesLeft }}</span>
          <span class="stat-pill">Mano: {{ s.opponentHandCount }} cartas</span>
          <span class="stat-pill">Mazo: {{ s.opponentDeckCount }}</span>
        </div>

        <!-- Opponent bench -->
        <div class="bench-row bench-row--opponent">
          @for (p of s.opponentBench; track p.name; let i = $index) {
            <div class="bench-slot">
              <div class="pokemon-card pokemon-card--bench">
                <div class="pokemon-name">{{ p.name }}</div>
                <div class="hp-bar">
                  <div class="hp-fill" [class]="hpClass(p)" [style.width.%]="hpPercent(p)"></div>
                </div>
                <div class="pokemon-hp">{{ p.hp }}/{{ p.maxHp }}</div>
              </div>
            </div>
          }
          @for (_ of [].constructor(5 - s.opponentBench.length); track $index) {
            <div class="bench-slot bench-slot--empty"></div>
          }
        </div>

        <!-- Opponent active -->
        <div class="active-zone">
          @if (s.opponentActive) {
            <div class="pokemon-card pokemon-card--active">
              <div class="pokemon-name">{{ s.opponentActive.name }}</div>
              @if (s.opponentActive.statusCondition) {
                <span class="status-badge">{{ s.opponentActive.statusCondition }}</span>
              }
              <div class="hp-bar">
                <div class="hp-fill" [class]="hpClass(s.opponentActive)" [style.width.%]="hpPercent(s.opponentActive)"></div>
              </div>
              <div class="pokemon-hp">{{ s.opponentActive.hp }}/{{ s.opponentActive.maxHp }}</div>
            </div>
          } @else {
            <div class="active-empty">Sin Pokémon activo</div>
          }
        </div>
      </section>

      <!-- Divider -->
      <div class="field-divider"></div>

      <!-- ── Player half (bottom) ── -->
      <section class="field-half field-player">

        <!-- Player active -->
        <div class="active-zone">
          @if (s.myActive) {
            <div class="pokemon-card pokemon-card--active pokemon-card--mine">
              <div class="pokemon-name">{{ s.myActive.name }}</div>
              @if (s.myActive.statusCondition) {
                <span class="status-badge">{{ s.myActive.statusCondition }}</span>
              }
              <div class="hp-bar">
                <div class="hp-fill" [class]="hpClass(s.myActive)" [style.width.%]="hpPercent(s.myActive)"></div>
              </div>
              <div class="pokemon-hp">{{ s.myActive.hp }}/{{ s.myActive.maxHp }}</div>
              <div class="attack-list">
                @for (atk of s.myActive.attacks; track atk.name) {
                  <div class="attack-row">
                    <span class="attack-name">{{ atk.name }}</span>
                    <span class="attack-dmg">{{ atk.damage }}</span>
                  </div>
                }
              </div>
            </div>
          } @else {
            <div class="active-empty">Sin Pokémon activo</div>
          }
        </div>

        <!-- Player bench -->
        <div class="bench-row bench-row--player">
          @for (p of s.myBench; track p.name; let i = $index) {
            <div class="bench-slot">
              <div class="pokemon-card pokemon-card--bench pokemon-card--mine">
                <div class="pokemon-name">{{ p.name }}</div>
                <div class="hp-bar">
                  <div class="hp-fill" [class]="hpClass(p)" [style.width.%]="hpPercent(p)"></div>
                </div>
                <div class="pokemon-hp">{{ p.hp }}/{{ p.maxHp }}</div>
              </div>
            </div>
          }
          @for (_ of [].constructor(5 - s.myBench.length); track $index) {
            <div class="bench-slot bench-slot--empty"></div>
          }
        </div>

        <div class="field-stats">
          <span class="stat-pill stat-pill--mine">Premios: {{ s.myPrizesLeft }}</span>
          <span class="stat-pill stat-pill--mine">Mazo: {{ s.myDeckCount }}</span>
        </div>
      </section>
    </div>

    <!-- Hand tray -->
    <section class="hand-tray">
      <div class="hand-label">Tu mano ({{ s.myHandCount }} cartas)</div>
      <div class="hand-cards">
        @for (card of s.myHand; track card.id) {
          <div class="hand-card" [class]="'hand-card--' + card.supertype.toLowerCase()">
            <div class="hand-card-name">{{ card.name }}</div>
            <div class="hand-card-type">{{ card.type }}</div>
          </div>
        }
      </div>
    </section>

  }

  <footer class="polling-note">Actualizando cada 3s · WebSocket en Sprint 5</footer>
</div>
```

- [ ] **Step 3: Create the SCSS**

```scss
// frontend/src/app/features/match/match.component.scss
:host {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #060810;
  color: #e2e8f0;
  font-family: 'Segoe UI', system-ui, sans-serif;
  overflow: hidden;
}

// ── Top bar ───────────────────────────────────────────
.board-topbar {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: .75rem 1.5rem;
  background: #0d1117;
  border-bottom: 1px solid #1e2433;
  flex-shrink: 0;
}

.btn-back {
  background: none;
  border: 1px solid #2d3748;
  color: #94a3b8;
  padding: .35rem .8rem;
  border-radius: 6px;
  font-size: .85rem;
  cursor: pointer;
  &:hover { color: #e2e8f0; border-color: #4a5568; }
}

.board-title {
  font-weight: 700;
  font-size: 1rem;
  color: #f8fafc;
  flex: 1;
}

.phase-badge {
  background: #1e2d4a;
  color: #60a5fa;
  padding: .3rem .8rem;
  border-radius: 20px;
  font-size: .8rem;
  font-weight: 600;
}

.alert-error {
  background: #2d1a1a;
  border-bottom: 1px solid #7f1d1d;
  color: #fca5a5;
  padding: .6rem 1.5rem;
  font-size: .85rem;
  flex-shrink: 0;
}

.loading-center {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  font-size: .95rem;
}

.winner-banner {
  background: #14532d;
  color: #86efac;
  text-align: center;
  padding: .75rem;
  font-size: 1.1rem;
  font-weight: 700;
  flex-shrink: 0;
}

// ── Field ─────────────────────────────────────────────
.board-field {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.field-half {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-around;
  padding: .75rem 1.5rem;
  gap: .5rem;
}

.field-opponent { background: #0a0c14; }
.field-player   { background: #0c0f18; }

.field-divider {
  height: 2px;
  background: linear-gradient(90deg, transparent, #3b4fd8, transparent);
  flex-shrink: 0;
}

.field-stats {
  display: flex;
  gap: .5rem;
  flex-wrap: wrap;
  justify-content: center;
}

.stat-pill {
  background: #13161f;
  border: 1px solid #1e2433;
  color: #94a3b8;
  padding: .25rem .65rem;
  border-radius: 20px;
  font-size: .78rem;
  &--mine { border-color: #2d3d6a; color: #93c5fd; }
}

// ── Bench ─────────────────────────────────────────────
.bench-row {
  display: flex;
  gap: .5rem;
  justify-content: center;
}

// ── Pokémon cards ─────────────────────────────────────
.bench-slot {
  width: 90px;
}

.bench-slot--empty {
  height: 90px;
  background: #0d1117;
  border: 1px dashed #1e2433;
  border-radius: 8px;
}

.active-zone {
  display: flex;
  justify-content: center;
}

.active-empty {
  width: 130px;
  height: 130px;
  background: #0d1117;
  border: 1px dashed #1e2433;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #334155;
  font-size: .8rem;
  text-align: center;
}

.pokemon-card {
  background: #0d1117;
  border: 1px solid #1e2433;
  border-radius: 8px;
  padding: .5rem;
  display: flex;
  flex-direction: column;
  gap: .3rem;

  &--active {
    width: 130px;
    padding: .75rem;
    border-radius: 10px;
    border-color: #2d3d6a;
  }

  &--bench {
    width: 90px;
  }

  &--mine {
    border-color: #1d3a6a;
    background: #0a0f1e;
  }
}

.pokemon-name {
  font-size: .8rem;
  font-weight: 700;
  color: #f1f5f9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-badge {
  background: #7c3aed;
  color: #ddd6fe;
  padding: .1rem .4rem;
  border-radius: 4px;
  font-size: .7rem;
  font-weight: 600;
  align-self: flex-start;
}

.hp-bar {
  background: #1e2433;
  border-radius: 4px;
  height: 5px;
  overflow: hidden;
}

.hp-fill {
  height: 100%;
  border-radius: 4px;
  transition: width .3s;
  &.hp-high { background: #22c55e; }
  &.hp-mid  { background: #eab308; }
  &.hp-low  { background: #ef4444; }
}

.pokemon-hp {
  font-size: .7rem;
  color: #64748b;
}

.attack-list { display: flex; flex-direction: column; gap: .2rem; margin-top: .3rem; }
.attack-row  { display: flex; justify-content: space-between; font-size: .72rem; }
.attack-name { color: #94a3b8; }
.attack-dmg  { color: #f97316; font-weight: 700; }

// ── Hand tray ─────────────────────────────────────────
.hand-tray {
  background: #0d1117;
  border-top: 1px solid #1e2433;
  padding: .75rem 1.5rem;
  flex-shrink: 0;
}

.hand-label {
  font-size: .78rem;
  color: #64748b;
  margin-bottom: .5rem;
}

.hand-cards {
  display: flex;
  gap: .5rem;
  overflow-x: auto;
  padding-bottom: .25rem;
}

.hand-card {
  flex-shrink: 0;
  width: 80px;
  background: #13161f;
  border: 1px solid #1e2433;
  border-top: 3px solid #3b4fd8;
  border-radius: 6px;
  padding: .5rem .4rem;
  transition: transform .1s;
  cursor: default;
  &:hover { transform: translateY(-4px); }

  &--pokemon { border-top-color: #3b4fd8; }
  &--energy  { border-top-color: #22c55e; }
  &--trainer { border-top-color: #e3350d; }
}

.hand-card-name {
  font-size: .72rem;
  font-weight: 700;
  color: #f1f5f9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hand-card-type {
  font-size: .65rem;
  color: #64748b;
  margin-top: .2rem;
}

// ── Footer ────────────────────────────────────────────
.polling-note {
  font-size: .72rem;
  color: #1e2433;
  text-align: center;
  padding: .4rem;
  flex-shrink: 0;
}
```

- [ ] **Step 4: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/match/
git commit -m "feat(fe): FE-08 Match Board — field layout, HP bars, hand tray, 3s polling"
```

---

## Task 5: Wire routes

**Files:**
- Modify: `frontend/src/app/app.routes.ts`

- [ ] **Step 1: Add lobby and match routes**

Replace the content of `frontend/src/app/app.routes.ts` with:

```typescript
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
  {
    path: 'lobby',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/lobby/lobby.component').then(m => m.LobbyComponent),
  },
  {
    path: 'match/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/match/match.component').then(m => m.MatchComponent),
  },
  { path: '', redirectTo: '/lobby', pathMatch: 'full' },
  { path: '**', redirectTo: '/login' },
];
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Build to confirm no import errors**

```bash
cd frontend && npm run build 2>&1 | tail -20
```

Expected: `Application bundle generation complete.` — no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/app.routes.ts
git commit -m "feat(fe): add /lobby and /match/:id routes, default redirect to /lobby"
```

---

## Task 6: Add RouterLink import to Lobby (needed for nav link)

**Files:**
- Modify: `frontend/src/app/features/lobby/lobby.component.ts`

- [ ] **Step 1: Add RouterLink to imports array**

In `lobby.component.ts`, change the imports line:

```typescript
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
```

And in the `@Component` decorator's `imports` array, add `RouterLink`:

```typescript
imports: [CommonModule, RouterLink],
```

- [ ] **Step 2: Verify and commit**

```bash
cd frontend && npx tsc --noEmit
git add frontend/src/app/features/lobby/lobby.component.ts
git commit -m "fix(fe): add RouterLink import to LobbyComponent for nav link"
```

---

## Self-Review

**Spec coverage:**
- FE-07 `/lobby`: ✅ Task 3 — list WAITING/ACTIVE, create, join, 3s polling
- FE-07 "ver partidas propias": ✅ covered by lobby list (all matches shown, user can see their own)
- FE-08 `/match/:id`: ✅ Task 4 — polls state, shows turn, HP, hand, counters
- Routes wired: ✅ Task 5
- Models: ✅ Task 1 — `MatchSummary`, `FilteredGameStateDto`, `FieldPokemon`
- Service: ✅ Task 2 — `list`, `create`, `join`, `getState`
- `authGuard` on both routes: ✅ Task 5

**Placeholder scan:** None found.

**Type consistency:**
- `MatchSummary` defined in Task 1, used in Tasks 2, 3 — consistent.
- `FilteredGameStateDto` defined in Task 1, used in Tasks 2, 4 — consistent.
- `FieldPokemon` defined in Task 1, used in Task 4 — consistent.
- `hpClass(pokemon: FieldPokemon)` and `hpPercent(pokemon: FieldPokemon)` — consistent across template calls.
- `[].constructor(5 - s.myBench.length)` — valid JS pattern for generating empty bench slots.
