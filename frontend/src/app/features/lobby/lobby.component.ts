import { Component, inject, signal, OnInit, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
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
  imports: [CommonModule, RouterLink],
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
