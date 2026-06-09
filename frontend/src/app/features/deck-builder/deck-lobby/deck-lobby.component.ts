import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DeckService } from '../../../core/services/deck.service';
import { Deck } from '../../../core/models/deck.model';

@Component({
  selector: 'app-deck-lobby',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './deck-lobby.component.html',
})
export class DeckLobbyComponent implements OnInit {
  private deckService = inject(DeckService);
  private router = inject(Router);

  decks = signal<Deck[]>([]);
  loading = signal(true);
  error = signal('');
  creatingName = signal('');
  showCreateForm = signal(false);

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.deckService.list().subscribe({
      next: (decks) => { this.decks.set(decks); this.loading.set(false); },
      error: (e: HttpErrorResponse) => {
        this.error.set(this.describeError(e, '/api/decks'));
        this.loading.set(false);
      },
    });
  }

  openCreate() { this.showCreateForm.set(true); this.creatingName.set(''); }
  cancelCreate() { this.showCreateForm.set(false); }

  create() {
    const name = this.creatingName().trim();
    if (!name) return;
    this.deckService.create(name).subscribe({
      next: (deck) => this.router.navigate(['/decks', deck.id]),
      error: (e: HttpErrorResponse) => this.error.set(this.describeError(e, '/api/decks')),
    });
  }

  edit(id: number) { this.router.navigate(['/decks', id]); }

  delete(id: number) {
    if (!confirm('¿Eliminar este mazo?')) return;
    this.deckService.delete(id).subscribe({
      next: () => this.decks.update(ds => ds.filter(d => d.id !== id)),
      error: () => this.error.set('Error al eliminar mazo'),
    });
  }

  cardCount(deck: Deck): number {
    return deck.cards?.reduce((s, c) => s + c.quantity, 0) ?? 0;
  }

  updateName(event: Event) {
    this.creatingName.set((event.target as HTMLInputElement).value);
  }

  private describeError(error: HttpErrorResponse, endpoint: string) {
    const details = (error.error && typeof error.error === 'object')
      ? error.error.message ?? error.error.error
      : null;
    const suffix = details ? `: ${details}` : '';
    return `Error ${error.status || 'HTTP'} en ${endpoint}${suffix}`;
  }
}
