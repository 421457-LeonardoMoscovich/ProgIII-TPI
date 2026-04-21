import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Deck } from '../models/deck.model';

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}

@Injectable({ providedIn: 'root' })
export class DeckService {
  private http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/decks`;

  list(): Observable<Deck[]> {
    return this.http.get<Deck[]>(this.base);
  }

  create(name: string): Observable<Deck> {
    return this.http.post<Deck>(this.base, null, { params: { name } });
  }

  updateCards(deckId: number, cards: Record<string, number>): Observable<Deck> {
    return this.http.put<Deck>(`${this.base}/${deckId}/cards`, cards);
  }

  validate(deckId: number): Observable<ValidationResult> {
    return this.http.post<ValidationResult>(`${this.base}/${deckId}/validate`, null);
  }

  delete(deckId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${deckId}`);
  }
}
