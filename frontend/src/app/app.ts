import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';
import { CardService } from './core/services/card.service';
import { Card } from './core/models/card.model';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private cards = inject(CardService);

  protected readonly title = signal('Pokémon TCG — xy1 smoke');
  protected readonly error = signal<string | null>(null);

  protected readonly sample = toSignal(
    this.cards.list('xy1').pipe(
      map((cs) => cs.slice(0, 5)),
      catchError((e) => {
        this.error.set(e?.message ?? 'Error loading cards');
        return of<Card[]>([]);
      }),
    ),
    { initialValue: [] as Card[] },
  );
}
