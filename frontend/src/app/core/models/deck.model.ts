export interface DeckCard {
  cardId: string;
  quantity: number;
}

export interface Deck {
  id: number;
  userId: number;
  name: string;
  isValid: boolean;
  cards: DeckCard[];
  createdAt: string;
  updatedAt: string;
}
