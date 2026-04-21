import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Card } from '../models/card.model';

@Injectable({ providedIn: 'root' })
export class CardService {
  private http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/cards`;

  list(set = 'xy1'): Observable<Card[]> {
    return this.http.get<Card[]>(this.base, { params: { set } });
  }

  search(params: { name?: string; supertype?: string; subtype?: string }): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.base}/search`, { params: params as Record<string, string> });
  }
}
