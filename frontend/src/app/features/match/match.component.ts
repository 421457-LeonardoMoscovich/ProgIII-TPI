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
