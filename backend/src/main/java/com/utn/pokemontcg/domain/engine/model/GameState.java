package com.utn.pokemontcg.domain.engine.model;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    private final String matchId;
    private MatchPhase matchPhase;
    private TurnPhase turnPhase;
    private final PlayerState player1;
    private final PlayerState player2;
    private PlayerState currentPlayer;
    private PlayerState waitingPlayer;
    private int globalTurn;
    private final TurnFlags turnFlags;
    private final List<Object> eventLog;

    public GameState(String matchId, PlayerState player1, PlayerState player2) {
        this.matchId = matchId;
        this.matchPhase = MatchPhase.WAITING;
        this.turnPhase = TurnPhase.DRAW;
        this.player1 = player1;
        this.player2 = player2;
        this.currentPlayer = player1;
        this.waitingPlayer = player2;
        this.globalTurn = 0;
        this.turnFlags = new TurnFlags();
        this.eventLog = new ArrayList<>();
    }

    public String getMatchId() { return matchId; }
    public MatchPhase getMatchPhase() { return matchPhase; }
    public void setMatchPhase(MatchPhase p) { this.matchPhase = p; }
    public TurnPhase getTurnPhase() { return turnPhase; }
    public void setTurnPhase(TurnPhase p) { this.turnPhase = p; }
    public PlayerState getPlayer1() { return player1; }
    public PlayerState getPlayer2() { return player2; }
    public PlayerState getCurrentPlayer() { return currentPlayer; }
    public PlayerState getWaitingPlayer() { return waitingPlayer; }
    public int getGlobalTurn() { return globalTurn; }
    public TurnFlags getTurnFlags() { return turnFlags; }
    public List<Object> getEventLog() { return eventLog; }

    public void swapTurn() {
        PlayerState tmp = currentPlayer;
        currentPlayer = waitingPlayer;
        waitingPlayer = tmp;
        globalTurn++;
        turnFlags.reset();
        turnPhase = TurnPhase.DRAW;
    }

    public boolean isFirstTurn() { return globalTurn == 0; }
}
