package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.event.GameEvent;
import com.utn.pokemontcg.domain.engine.model.GameState;
import com.utn.pokemontcg.domain.engine.model.PlayerState;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable context object passed through the AttackResolver chain of responsibility.
 * Each handler reads and/or mutates this object as it processes its step.
 */
public class AttackContext {

    private final GameState state;
    private final PlayerState attacker;
    private final PlayerState defender;
    private final String attackName;
    private final int baseAttackDamage;
    /** The primary type of the attacking Pokémon (e.g. "Fire"). "Colorless" if null/absent. */
    private final String attackerType;
    private boolean cancelled;
    private final List<GameEvent> events;

    public AttackContext(GameState state,
                         PlayerState attacker,
                         PlayerState defender,
                         String attackName,
                         int baseAttackDamage,
                         String attackerType) {
        this.state            = state;
        this.attacker         = attacker;
        this.defender         = defender;
        this.attackName       = attackName;
        this.baseAttackDamage = baseAttackDamage;
        this.attackerType     = (attackerType != null) ? attackerType : "Colorless";
        this.cancelled        = false;
        this.events           = new ArrayList<>();
    }

    // ---- Accessors --------------------------------------------------------

    public GameState getState()           { return state; }
    public PlayerState getAttacker()      { return attacker; }
    public PlayerState getDefender()      { return defender; }
    public String getAttackName()         { return attackName; }
    public int getBaseAttackDamage()      { return baseAttackDamage; }
    public String getAttackerType()       { return attackerType; }
    public boolean isCancelled()          { return cancelled; }
    public List<GameEvent> getEvents()    { return events; }

    /** Short-circuits the chain — no further handlers will execute after the current one. */
    public void cancel() {
        this.cancelled = true;
    }

    /** Appends a {@link GameEvent} to the accumulated event list. */
    public void addEvent(GameEvent event) {
        events.add(event);
    }
}
