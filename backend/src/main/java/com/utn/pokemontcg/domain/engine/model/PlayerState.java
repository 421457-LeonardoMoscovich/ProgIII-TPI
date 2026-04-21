package com.utn.pokemontcg.domain.engine.model;

import java.util.ArrayList;
import java.util.List;

public class PlayerState {
    private final Long userId;
    private final List<GameCard> deck;
    private final List<GameCard> hand;
    private final List<GameCard> discardPile;
    private final List<GameCard> prizes;
    private PokemonInPlay activePokemon;
    private final List<PokemonInPlay> bench;
    private int turnNumber;

    public PlayerState(Long userId) {
        this.userId = userId;
        this.deck = new ArrayList<>();
        this.hand = new ArrayList<>();
        this.discardPile = new ArrayList<>();
        this.prizes = new ArrayList<>();
        this.bench = new ArrayList<>();
        this.turnNumber = 0;
    }

    public Long getUserId() { return userId; }
    public List<GameCard> getDeck() { return deck; }
    public List<GameCard> getHand() { return hand; }
    public List<GameCard> getDiscardPile() { return discardPile; }
    public List<GameCard> getPrizes() { return prizes; }
    public PokemonInPlay getActivePokemon() { return activePokemon; }
    public void setActivePokemon(PokemonInPlay p) { this.activePokemon = p; }
    public List<PokemonInPlay> getBench() { return bench; }
    public int getTurnNumber() { return turnNumber; }
    public void incrementTurnNumber() { this.turnNumber++; }
    public boolean hasNoPokemon() {
        return activePokemon == null && bench.isEmpty();
    }
}
