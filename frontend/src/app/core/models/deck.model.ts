export interface DeckCard {
  cardId: string;
  cardName: string;
  imageSmall: string;
  quantity: number;
}

export interface Deck {
  id: number;
  name: string;
  isValid: boolean;
  cards: DeckCard[];
  createdAt?: string;
  updatedAt?: string;
}
