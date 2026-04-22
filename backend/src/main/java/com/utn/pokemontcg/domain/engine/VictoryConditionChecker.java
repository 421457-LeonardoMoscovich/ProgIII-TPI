package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.model.GameState;
import com.utn.pokemontcg.domain.engine.model.PlayerState;

import java.util.Optional;

/**
 * Checks the four win conditions for the Pokémon TCG (XY1 ruleset) in priority order:
 * <ol>
 *   <li>All prize cards taken — the player whose prize list is empty wins.</li>
 *   <li>No Pokémon in play — a player with no active Pokémon and an empty bench loses.</li>
 *   <li>Draw from empty deck — a player whose deck is empty cannot draw and loses.</li>
 *   <li>Sudden Death — both players satisfy a loss condition simultaneously.</li>
 * </ol>
 *
 * <p>Zero Spring dependencies; pure domain logic.</p>
 */
public class VictoryConditionChecker {

    /**
     * Immutable result of a victory check.
     *
     * @param winnerId the winner's userId, or {@code null} in Sudden Death
     * @param loserId  the loser's userId, or {@code null} in Sudden Death
     * @param reason   human-readable reason string
     */
    public record VictoryResult(Long winnerId, Long loserId, String reason) {}

    /**
     * Evaluates all win conditions against the current {@link GameState}.
     *
     * @param state the current game state
     * @return an {@link Optional} containing the {@link VictoryResult}, or empty if the
     *         game is still in progress
     */
    public Optional<VictoryResult> check(GameState state) {
        PlayerState p1 = state.getPlayer1();
        PlayerState p2 = state.getPlayer2();

        boolean p1LosesPrize  = p1.getPrizes().isEmpty();
        boolean p2LosesPrize  = p2.getPrizes().isEmpty();
        boolean p1LosesNoPoke = p1.hasNoPokemon();
        boolean p2LosesNoPoke = p2.hasNoPokemon();
        boolean p1LosesDeck   = p1.getDeck().isEmpty();
        boolean p2LosesDeck   = p2.getDeck().isEmpty();

        // A player "loses" if any individual loss condition is true
        boolean p1Loses = p1LosesNoPoke || p1LosesDeck;
        boolean p2Loses = p2LosesNoPoke || p2LosesDeck;

        // --- Condition 1: All prizes taken ---
        // Check before the others; taking all prizes is an immediate win even if the
        // opponent also has a simultaneous loss condition (e.g. prize-taking KO).
        if (p1LosesPrize && p2LosesPrize) {
            // Both took their last prize at the same moment → Sudden Death
            return Optional.of(new VictoryResult(null, null, "sudden death"));
        }
        if (p1LosesPrize) {
            // p1's prize list is empty → p1 wins
            return Optional.of(new VictoryResult(p1.getUserId(), p2.getUserId(), "all prizes taken"));
        }
        if (p2LosesPrize) {
            // p2's prize list is empty → p2 wins
            return Optional.of(new VictoryResult(p2.getUserId(), p1.getUserId(), "all prizes taken"));
        }

        // --- Conditions 2 & 3 combined, with Sudden Death check ---
        if (p1Loses && p2Loses) {
            return Optional.of(new VictoryResult(null, null, "sudden death"));
        }
        if (p2Loses) {
            String reason = p2LosesNoPoke ? "no pokemon in play" : "drew from empty deck";
            return Optional.of(new VictoryResult(p1.getUserId(), p2.getUserId(), reason));
        }
        if (p1Loses) {
            String reason = p1LosesNoPoke ? "no pokemon in play" : "drew from empty deck";
            return Optional.of(new VictoryResult(p2.getUserId(), p1.getUserId(), reason));
        }

        // Game is still active
        return Optional.empty();
    }
}
