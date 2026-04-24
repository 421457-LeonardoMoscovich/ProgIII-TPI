import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MatchSummary, FilteredGameStateDto } from '../models/match.model';

@Injectable({ providedIn: 'root' })
export class MatchService {
  private http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/matches`;

  list(): Observable<MatchSummary[]> {
    return this.http.get<MatchSummary[]>(this.base);
  }

  create(deckId: number): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(this.base, { deckId });
  }

  join(matchId: number, deckId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${matchId}/join`, { deckId });
  }

  getState(matchId: number): Observable<FilteredGameStateDto> {
    return this.http.get<FilteredGameStateDto>(`${this.base}/${matchId}/state`);
  }
}
