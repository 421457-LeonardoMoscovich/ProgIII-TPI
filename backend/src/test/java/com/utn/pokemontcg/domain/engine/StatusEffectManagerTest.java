package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.model.GameCard;
import com.utn.pokemontcg.domain.engine.model.PokemonInPlay;
import com.utn.pokemontcg.domain.engine.model.StatusCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class StatusEffectManagerTest {

    private StatusEffectManager manager;
    private PokemonInPlay pokemon;

    @BeforeEach
    void setUp() {
        manager = new StatusEffectManager();
        GameCard card = new GameCard(
                "xy1-1", "Venusaur-EX", "Pokémon",
                List.of("EX", "Basic"), 180,
                List.of(), null, null, 4
        );
        pokemon = new PokemonInPlay(card);
    }

    // ─── Test 1: apply sets DORMIDO ───────────────────────────────────────────

    @Test
    void apply_setsDormido() {
        manager.apply(pokemon, StatusCondition.DORMIDO);
        assertEquals(StatusCondition.DORMIDO, pokemon.getPrimaryStatus());
    }

    // ─── Test 2: apply replaces existing status (mutual exclusion) ────────────

    @Test
    void apply_replacesExistingStatus_mutualExclusion() {
        manager.apply(pokemon, StatusCondition.PARALIZADO);
        assertEquals(StatusCondition.PARALIZADO, pokemon.getPrimaryStatus());

        manager.apply(pokemon, StatusCondition.CONFUNDIDO);
        assertEquals(StatusCondition.CONFUNDIDO, pokemon.getPrimaryStatus());
    }

    // ─── Test 3: processBetweenTurns DORMIDO heads → wakes up ────────────────

    @Test
    void processBetweenTurns_dormido_heads_wakesUp() {
        manager.apply(pokemon, StatusCondition.DORMIDO);
        // heads = true → wake up
        Random alwaysHeads = new Random() {
            @Override public boolean nextBoolean() { return true; }
        };
        manager.processBetweenTurns(pokemon, alwaysHeads);
        assertEquals(StatusCondition.NONE, pokemon.getPrimaryStatus());
        assertEquals(0, pokemon.getDamage()); // no damage on wake
    }

    // ─── Test 4: processBetweenTurns DORMIDO tails → stays DORMIDO ───────────

    @Test
    void processBetweenTurns_dormido_tails_staysDormido() {
        manager.apply(pokemon, StatusCondition.DORMIDO);
        // tails = false → stays asleep
        Random alwaysTails = new Random() {
            @Override public boolean nextBoolean() { return false; }
        };
        manager.processBetweenTurns(pokemon, alwaysTails);
        assertEquals(StatusCondition.DORMIDO, pokemon.getPrimaryStatus());
        assertEquals(0, pokemon.getDamage()); // no damage from sleeping
    }

    // ─── Test 5: processBetweenTurns PARALIZADO → auto-clears to NONE ─────────

    @Test
    void processBetweenTurns_paralizado_autoClears() {
        manager.apply(pokemon, StatusCondition.PARALIZADO);
        manager.processBetweenTurns(pokemon, new Random());
        assertEquals(StatusCondition.NONE, pokemon.getPrimaryStatus());
        assertEquals(0, pokemon.getDamage()); // no damage from paralysis
    }

    // ─── Test 6: processBetweenTurns QUEMADO → 10 damage ────────────────────

    @Test
    void processBetweenTurns_quemado_deals10Damage() {
        manager.apply(pokemon, StatusCondition.QUEMADO);
        manager.processBetweenTurns(pokemon, new Random());
        assertEquals(10, pokemon.getDamage());
        // burn condition remains
        assertTrue(pokemon.isBurned());
    }

    // ─── Test 7: processBetweenTurns ENVENENADO → 10 damage ─────────────────

    @Test
    void processBetweenTurns_envenenado_deals10Damage() {
        manager.apply(pokemon, StatusCondition.ENVENENADO);
        manager.processBetweenTurns(pokemon, new Random());
        assertEquals(10, pokemon.getDamage());
        // poison condition remains
        assertTrue(pokemon.isPoisoned());
    }

    // ─── Test 8: processBetweenTurns NONE → no change ────────────────────────

    @Test
    void processBetweenTurns_none_noEffect() {
        int damageBefore = pokemon.getDamage();
        StatusCondition statusBefore = pokemon.getPrimaryStatus();
        manager.processBetweenTurns(pokemon, new Random());
        assertEquals(damageBefore, pokemon.getDamage());
        assertEquals(statusBefore, pokemon.getPrimaryStatus());
    }

    // ─── Test 9: apply CONFUNDIDO sets condition ──────────────────────────────

    @Test
    void apply_confundido_setsCondition() {
        manager.apply(pokemon, StatusCondition.CONFUNDIDO);
        assertEquals(StatusCondition.CONFUNDIDO, pokemon.getPrimaryStatus());
    }

    // ─── Test 10: apply primary status clears burned flag (mutual exclusion) ──

    @Test
    void apply_dormido_whenAlreadyBurned_clearsBurnedFlag() {
        // Pre-condition: Pokémon is burned
        pokemon.setBurned(true);
        assertTrue(pokemon.isBurned());

        // Applying a primary status must clear burned (mutual exclusion)
        manager.apply(pokemon, StatusCondition.DORMIDO);

        assertEquals(StatusCondition.DORMIDO, pokemon.getPrimaryStatus());
        assertFalse(pokemon.isBurned(), "burned must be cleared when a primary status is applied");
        assertFalse(pokemon.isPoisoned(), "poisoned must also be cleared when a primary status is applied");
    }
}
