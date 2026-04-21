package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.model.GameState;
import com.utn.pokemontcg.domain.engine.model.TurnPhase;

/**
 * Manages turn-phase transitions for the Pokémon TCG match engine.
 *
 * <p>State pattern: each {@link TurnPhase} value defines the next phase.
 * Phase order: DRAW → MAIN → ATTACK → BETWEEN_TURNS → (swap, then next player's DRAW or MAIN).
 *
 * <p>Special rule — first player, turn 0:
 * According to official Pokémon TCG rules, the first player skips their Draw phase
 * on the very first turn of the game ({@code globalTurn == 0}).
 */
public class TurnManager {

    /**
     * Advances the current {@link TurnPhase} of the given {@link GameState} by one step.
     *
     * <p>When the current phase is {@code BETWEEN_TURNS}, this method performs the
     * full end-of-turn sequence (via {@link #endTurn(GameState)}) and then sets the
     * initial phase for the next player (respecting the turn-0 DRAW skip rule).
     *
     * @param state the current game state (mutated in place)
     */
    public void advancePhase(GameState state) {
        switch (state.getTurnPhase()) {
            case DRAW         -> state.setTurnPhase(TurnPhase.MAIN);
            case MAIN         -> state.setTurnPhase(TurnPhase.ATTACK);
            case ATTACK       -> state.setTurnPhase(TurnPhase.BETWEEN_TURNS);
            case BETWEEN_TURNS -> {
                endTurn(state);
                // After the swap globalTurn has already been incremented by swapTurn(),
                // so isFirstTurn() is false unless we just came back to turn 0 — which
                // never happens.  The next player always gets DRAW here, but we route
                // through getInitialPhaseFor() to honour any future edge-cases.
                state.setTurnPhase(getInitialPhaseFor(state));
            }
        }
    }

    /**
     * Ends the current player's turn: delegates to {@link GameState#swapTurn()} which
     * switches active/waiting players, increments {@code globalTurn}, and resets
     * {@link com.utn.pokemontcg.domain.engine.model.TurnFlags}.
     *
     * @param state the current game state (mutated in place)
     */
    public void endTurn(GameState state) {
        state.swapTurn();
        // swapTurn() already: swaps currentPlayer/waitingPlayer, increments globalTurn,
        // resets TurnFlags, and sets turnPhase to DRAW.
        // We then apply the skip rule for the newly active player.
        state.setTurnPhase(getInitialPhaseFor(state));
    }

    /**
     * Returns the correct initial {@link TurnPhase} for the player who is about to start
     * their turn.
     *
     * <p>Rule: the first player (player1) skips the DRAW phase on turn 0
     * ({@code globalTurn == 0}).  All other turns/players begin with DRAW.
     *
     * @param state the current game state after the player swap has already occurred
     * @return {@link TurnPhase#MAIN} if the first player is about to play turn 0,
     *         {@link TurnPhase#DRAW} otherwise
     */
    public TurnPhase getInitialPhaseFor(GameState state) {
        boolean isPlayer1 = state.getCurrentPlayer().equals(state.getPlayer1());
        if (state.isFirstTurn() && isPlayer1) {
            return TurnPhase.MAIN;
        }
        return TurnPhase.DRAW;
    }
}
