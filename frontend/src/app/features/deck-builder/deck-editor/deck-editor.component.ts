import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { DeckService, ValidationResult } from '../../../core/services/deck.service';
import { CardService } from '../../../core/services/card.service';
import { Card } from '../../../core/models/card.model';
import { Deck } from '../../../core/models/deck.model';
import { CardPreviewComponent } from '../../../shared/components/card-preview/card-preview.component';

@Component({
  selector: 'app-deck-editor',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, CardPreviewComponent],
  templateUrl: './deck-editor.component.html',
})
export class DeckEditorComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private fb = inject(FormBuilder);
  private deckService = inject(DeckService);
  private cardService = inject(CardService);

  deck = signal<Deck | null>(null);
  allCards = signal<Card[]>([]);
  deckCards = signal<Record<string, number>>({});
  validation = signal<ValidationResult | null>(null);
  loading = signal(true);
  saving = signal(false);
  error = signal('');

  filterForm = this.fb.group({
    name: [''],
    supertype: [''],
    subtype: [''],
  });

  totalCards = computed(() => Object.values(this.deckCards()).reduce((s, n) => s + n, 0));

  filteredCards = computed(() => {
    const { name, supertype, subtype } = this.filterForm.value;
    return this.allCards().filter(c => {
      if (name && !c.name.toLowerCase().includes(name.toLowerCase())) return false;
      if (supertype && c.supertype !== supertype) return false;
      if (subtype && !c.subtypes?.includes(subtype)) return false;
      return true;
    });
  });

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loadDeck(id);
    this.loadCards();

    this.filterForm.valueChanges
      .pipe(debounceTime(200), distinctUntilChanged())
      .subscribe(() => {});
  }

  private loadDeck(id: number) {
    this.deckService.list().subscribe({
      next: (decks) => {
        const deck = decks.find(d => d.id === id);
        if (!deck) { this.router.navigate(['/decks']); return; }
        this.deck.set(deck);
        const map: Record<string, number> = {};
        deck.cards?.forEach(c => { map[c.cardId] = c.quantity; });
        this.deckCards.set(map);
        this.loading.set(false);
      },
      error: () => { this.error.set('Error al cargar mazo'); this.loading.set(false); },
    });
  }

  private loadCards() {
    this.cardService.list('xy1').subscribe({
      next: (cards) => this.allCards.set(cards),
      error: () => this.error.set('Error al cargar cartas'),
    });
  }

  quantityOf(cardId: string): number {
    return this.deckCards()[cardId] ?? 0;
  }

  add(card: Card) {
    const qty = this.quantityOf(card.id);
    const isBasicEnergy = card.supertype === 'Energy' && !card.subtypes?.includes('Special');
    if (!isBasicEnergy && qty >= 4) return;
    this.deckCards.update(m => ({ ...m, [card.id]: qty + 1 }));
    this.validation.set(null);
  }

  remove(cardId: string) {
    const qty = this.quantityOf(cardId);
    if (qty <= 0) return;
    this.deckCards.update(m => {
      const updated = { ...m };
      if (qty === 1) delete updated[cardId];
      else updated[cardId] = qty - 1;
      return updated;
    });
    this.validation.set(null);
  }

  save() {
    const deck = this.deck();
    if (!deck) return;
    this.saving.set(true);
    this.deckService.updateCards(deck.id, this.deckCards()).subscribe({
      next: (updated) => {
        this.deck.set(updated);
        this.saving.set(false);
        this.validate();
      },
      error: () => { this.error.set('Error al guardar'); this.saving.set(false); },
    });
  }

  validate() {
    const deck = this.deck();
    if (!deck) return;
    this.deckService.validate(deck.id).subscribe({
      next: (result) => this.validation.set(result),
      error: () => this.error.set('Error al validar'),
    });
  }

  back() { this.router.navigate(['/decks']); }

  deckCardList() {
    const cards = this.allCards();
    return Object.entries(this.deckCards())
      .map(([cardId, qty]) => ({ card: cards.find(c => c.id === cardId), qty }))
      .filter(e => e.card != null) as { card: Card; qty: number }[];
  }

  supertypes = ['Pokémon', 'Trainer', 'Energy'];
}
