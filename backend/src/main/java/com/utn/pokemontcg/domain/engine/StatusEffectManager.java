package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.model.PokemonInPlay;
import com.utn.pokemontcg.domain.engine.model.StatusCondition;

import java.util.Random;

/**
 * Manages status conditions (special conditions) for Pokémon in play.
 *
 * <p>Rules implemented:
 * <ul>
 *   <li>Mutual exclusion: only one primary status (DORMIDO, CONFUNDIDO, PARALIZADO) at a time.
 *       Applying a new status replaces the existing one.</li>
 *   <li>QUEMADO and ENVENENADO are tracked via dedicated boolean flags on PokemonInPlay
 *       alongside the primaryStatus field.</li>
 *   <li>Between-turns processing: PARALIZADO auto-clears; QUEMADO/ENVENENADO deal 10 damage;
 *       DORMIDO flips a coin to wake up; CONFUNDIDO is handled at attack time by RuleValidator.</li>
 * </ul>
 */
public class StatusEffectManager {

    /**
     * Applies a status condition to the given Pokémon, replacing any existing primary status.
     *
     * @param pokemon   the Pokémon to affect
     * @param condition the new condition to apply
     */
    public void apply(PokemonInPlay pokemon, StatusCondition condition) {
        switch (condition) {
            case DORMIDO, CONFUNDIDO, PARALIZADO -> {
                // Primary status: mutually exclusive — clear any previous primary and flags
                pokemon.setPrimaryStatus(condition);
                // Applying a primary status does not clear burn/poison (they can coexist
                // per the official rules, but mutual exclusion applies only within primary statuses)
            }
            case QUEMADO -> {
                // Replace any existing primary status with NONE, set burn flag
                pokemon.setPrimaryStatus(StatusCondition.NONE);
                pokemon.setBurned(true);
                pokemon.setPoisoned(false);
            }
            case ENVENENADO -> {
                // Replace any existing primary status with NONE, set poison flag
                pokemon.setPrimaryStatus(StatusCondition.NONE);
                pokemon.setPoisoned(true);
                pokemon.setBurned(false);
            }
            case NONE -> {
                pokemon.setPrimaryStatus(StatusCondition.NONE);
                pokemon.setBurned(false);
                pokemon.setPoisoned(false);
            }
        }
    }

    /**
     * Processes between-turns effects for the given Pokémon.
     *
     * <p>Order of processing:
     * <ol>
     *   <li>DORMIDO: flip coin — heads wakes the Pokémon, tails keeps it asleep (no damage).</li>
     *   <li>CONFUDIDO: no between-turns damage — effect handled by RuleValidator during attack.</li>
     *   <li>PARALIZADO: auto-clears to NONE.</li>
     *   <li>QUEMADO: deal 10 damage.</li>
     *   <li>ENVENENADO: deal 10 damage.</li>
     *   <li>NONE: no effect.</li>
     * </ol>
     *
     * @param pokemon the Pokémon to process
     * @param random  source of randomness for coin flips (injected for testability)
     */
    public void processBetweenTurns(PokemonInPlay pokemon, Random random) {
        // Process primary status first
        switch (pokemon.getPrimaryStatus()) {
            case DORMIDO -> {
                boolean heads = random.nextBoolean();
                if (heads) {
                    pokemon.setPrimaryStatus(StatusCondition.NONE);
                }
                // tails: stays DORMIDO, no damage
            }
            case CONFUNDIDO -> {
                // Confusion self-damage happens when the player tries to attack (RuleValidator).
                // No between-turns effect.
            }
            case PARALIZADO -> {
                // Auto-clear after the turn ends
                pokemon.setPrimaryStatus(StatusCondition.NONE);
            }
            case NONE -> { /* no-op */ }
            default -> { /* QUEMADO/ENVENENADO won't appear here — handled below */ }
        }

        // Process burn
        if (pokemon.isBurned()) {
            pokemon.addDamage(10);
        }

        // Process poison
        if (pokemon.isPoisoned()) {
            pokemon.addDamage(10);
        }
    }
}
