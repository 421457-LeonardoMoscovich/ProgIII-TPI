package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TurnManagerTest {

    private TurnManager turnManager;
    private GameState state;

    @BeforeEach
    void setUp() {
        turnManager = new TurnManager();
        PlayerState p1 = new PlayerState(1L);
        PlayerState p2 = new PlayerState(2L);
        state = new GameState("match-1", p1, p2);
    }

    // ---------------------------------------------------------------
    // Phase transition tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("advancePhase: DRAW → MAIN")
    void advancePhase_drawToMain() {
        state.setTurnPhase(TurnPhase.DRAW);
        turnManager.advancePhase(state);
        assertEquals(TurnPhase.MAIN, state.getTurnPhase());
    }

    @Test
    @DisplayName("advancePhase: MAIN → ATTACK")
    void advancePhase_mainToAttack() {
        state.setTurnPhase(TurnPhase.MAIN);
        turnManager.advancePhase(state);
        assertEquals(TurnPhase.ATTACK, state.getTurnPhase());
    }

    @Test
    @DisplayName("advancePhase: ATTACK → BETWEEN_TURNS")
    void advancePhase_attackToBetweenTurns() {
        state.setTurnPhase(TurnPhase.ATTACK);
        turnManager.advancePhase(state);
        assertEquals(TurnPhase.BETWEEN_TURNS, state.getTurnPhase());
    }

    @Test
    @DisplayName("advancePhase: BETWEEN_TURNS → DRAW (next player's turn)")
    void advancePhase_betweenTurnsToNextDraw() {
        state.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        // globalTurn is 0, but BETWEEN_TURNS → next player should get DRAW
        // We need to be past turn 0 so the second player starts with DRAW
        // Manually put into a "mid-game" state by calling swapTurn first
        state.swapTurn(); // now globalTurn == 1, currentPlayer == player2
        state.setTurnPhase(TurnPhase.BETWEEN_TURNS);

        turnManager.advancePhase(state);

        assertEquals(TurnPhase.DRAW, state.getTurnPhase());
    }

    // ---------------------------------------------------------------
    // endTurn tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("endTurn switches active player and increments turnNumber")
    void endTurn_switchesPlayerAndIncrementsTurn() {
        PlayerState initialCurrent = state.getCurrentPlayer();
        PlayerState initialWaiting = state.getWaitingPlayer();
        int turnBefore = state.getGlobalTurn();

        turnManager.endTurn(state);

        // Active player should now be the previously waiting player
        assertEquals(initialWaiting, state.getCurrentPlayer());
        assertEquals(initialCurrent, state.getWaitingPlayer());
        assertEquals(turnBefore + 1, state.getGlobalTurn());
    }

    @Test
    @DisplayName("endTurn resets TurnFlags")
    void endTurn_resetsTurnFlags() {
        // Dirty up the flags
        state.getTurnFlags().setSupporterPlayedThisTurn(true);
        state.getTurnFlags().setAttackDoneThisTurn(true);
        state.getTurnFlags().setEnergyAttachedThisTurn(true);
        state.getTurnFlags().setRetreatedThisTurn(true);

        turnManager.endTurn(state);

        TurnFlags flags = state.getTurnFlags();
        assertFalse(flags.isSupporterPlayedThisTurn());
        assertFalse(flags.isAttackDoneThisTurn());
        assertFalse(flags.isEnergyAttachedThisTurn());
        assertFalse(flags.isRetreatedThisTurn());
    }

    // ---------------------------------------------------------------
    // Rule 3: first player skips DRAW on turn 0
    // ---------------------------------------------------------------

    @Test
    @DisplayName("First player on turn 0: initial phase must be MAIN, not DRAW")
    void firstPlayerTurnZero_initialPhaseIsMain() {
        // Fresh state: globalTurn == 0, currentPlayer == player1
        assertTrue(state.isFirstTurn(), "Precondition: must be first turn");
        assertEquals(state.getPlayer1(), state.getCurrentPlayer(), "Precondition: player1 is current");

        TurnPhase firstPhase = turnManager.getInitialPhaseFor(state);

        assertEquals(TurnPhase.MAIN, firstPhase,
                "Player 1 should skip DRAW on turn 0 and start with MAIN");
    }

    @Test
    @DisplayName("Second player on turn 1 starts with DRAW (no skip)")
    void secondPlayerTurnOne_initialPhaseIsDraw() {
        // Simulate player1 finished their turn
        turnManager.endTurn(state); // globalTurn becomes 1, currentPlayer == player2

        assertFalse(state.isFirstTurn(), "Precondition: must NOT be first turn");
        assertEquals(state.getPlayer2(), state.getCurrentPlayer(), "Precondition: player2 is current");

        TurnPhase firstPhase = turnManager.getInitialPhaseFor(state);

        assertEquals(TurnPhase.DRAW, firstPhase,
                "Player 2 should start their first turn with DRAW");
    }

    @Test
    @DisplayName("advancePhase on BETWEEN_TURNS calls endTurn logic and sets correct initial phase")
    void advancePhase_betweenTurns_appliesSkipRuleForNextPlayer() {
        // Start: globalTurn == 0, player1 is current. Set BETWEEN_TURNS for player1.
        state.setTurnPhase(TurnPhase.BETWEEN_TURNS);

        // After advancing from BETWEEN_TURNS: player2 becomes current (globalTurn == 1)
        // player2 is NOT on turn 0, so they get DRAW
        turnManager.advancePhase(state);

        assertEquals(TurnPhase.DRAW, state.getTurnPhase());
        assertEquals(state.getPlayer2(), state.getCurrentPlayer());
    }
}
