import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { environment } from '../../../environments/environment';

interface RankingEntryDto {
  rank: number;
  username: string;
  wins: number;
  losses: number;
  totalGames: number;
}

@Component({
  selector: 'app-ranking',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './ranking.component.html',
})
export class RankingComponent implements OnInit {
  private http = inject(HttpClient);
  private destroyRef = inject(DestroyRef);

  ranking = signal<RankingEntryDto[]>([]);
  loading = signal(true);
  error = signal('');

  ngOnInit(): void {
    this.http.get<RankingEntryDto[]>(`${environment.apiBaseUrl}/ranking`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: data => { this.ranking.set(data); this.loading.set(false); },
        error: () => { this.error.set('Error al cargar el ranking'); this.loading.set(false); },
      });
  }
}
