package com.utn.pokemontcg.domain.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class DeckCardId implements Serializable {

    private Long deckId;
    private String cardId;

    protected DeckCardId() {}

    public DeckCardId(Long deckId, String cardId) {
        this.deckId = deckId;
        this.cardId = cardId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeckCardId that)) return false;
        return Objects.equals(deckId, that.deckId) && Objects.equals(cardId, that.cardId);
    }

    @Override
    public int hashCode() { return Objects.hash(deckId, cardId); }
}
