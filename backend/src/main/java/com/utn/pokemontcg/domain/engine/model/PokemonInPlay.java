package com.utn.pokemontcg.domain.engine.model;

import java.util.ArrayList;
import java.util.List;

public class PokemonInPlay {
    private final GameCard card;
    private int damage;
    private StatusCondition primaryStatus;
    private boolean burned;
    private boolean poisoned;
    private final List<String> attachedEnergyIds;
    private final List<GameCard> attachedTools;
    private boolean justPlaced;

    public PokemonInPlay(GameCard card) {
        this.card = card;
        this.damage = 0;
        this.primaryStatus = StatusCondition.NONE;
        this.burned = false;
        this.poisoned = false;
        this.attachedEnergyIds = new ArrayList<>();
        this.attachedTools = new ArrayList<>();
        this.justPlaced = true;
    }

    public GameCard getCard() { return card; }
    public int getDamage() { return damage; }
    public void addDamage(int amount) { this.damage += amount; }
    public void setDamage(int damage) { this.damage = damage; }
    public boolean isKnockedOut() { return damage >= card.hp(); }
    public StatusCondition getPrimaryStatus() { return primaryStatus; }
    public void setPrimaryStatus(StatusCondition s) { this.primaryStatus = s; }
    public boolean isBurned() { return burned; }
    public void setBurned(boolean burned) { this.burned = burned; }
    public boolean isPoisoned() { return poisoned; }
    public void setPoisoned(boolean poisoned) { this.poisoned = poisoned; }
    public List<String> getAttachedEnergyIds() { return attachedEnergyIds; }
    public List<GameCard> getAttachedTools() { return attachedTools; }
    public boolean isJustPlaced() { return justPlaced; }
    public void clearJustPlaced() { this.justPlaced = false; }
    public int countAttachedTool(String name) {
        return (int) attachedTools.stream().filter(t -> t.name().equals(name)).count();
    }
}
