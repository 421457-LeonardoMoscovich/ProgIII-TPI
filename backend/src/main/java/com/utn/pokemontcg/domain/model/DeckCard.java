package com.utn.pokemontcg.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "deck_cards")
public class DeckCard {

    @EmbeddedId
    private DeckCardId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("deckId")
    @JoinColumn(name = "deck_id")
    private Deck deck;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("cardId")
    @JoinColumn(name = "card_id")
    private Card card;

    @Column(nullable = false)
    private int quantity;

    protected DeckCard() {}

    public DeckCard(Deck deck, Card card, int quantity) {
        this.id = new DeckCardId(deck.getId(), card.getId());
        this.deck = deck;
        this.card = card;
        this.quantity = quantity;
    }

    public DeckCardId getId() { return id; }
    public Deck getDeck() { return deck; }
    public Card getCard() { return card; }
    public int getQuantity() { return quantity; }

    public void setQuantity(int quantity) { this.quantity = quantity; }
}
