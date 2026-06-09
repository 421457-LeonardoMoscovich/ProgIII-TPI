package com.utn.pokemontcg.application.service;

import com.utn.pokemontcg.domain.engine.GameEngineFacade;
import com.utn.pokemontcg.domain.engine.model.GameCard;
import com.utn.pokemontcg.domain.engine.model.GameState;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MatchSessionService {

    private record Session(GameState state, GameEngineFacade engine) {}

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    // In-memory match metadata for lobby list
    public record MatchMeta(Long id, String status, String player1Username,
                            String player2Username, String createdAt, int turnNumber,
                            Long player1DeckId, Long player2DeckId) {}

    private final Map<Long, MatchMeta> matchMeta = new ConcurrentHashMap<>();
    private final Map<Long, String> matchWinners = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public GameState startSession(String matchId, Long p1Id, Long p2Id,
                                  List<GameCard> deck1, List<GameCard> deck2,
                                  long seed) {
        GameEngineFacade engine = new GameEngineFacade();
        GameState state = engine.startMatch(p1Id, deck1, p2Id, deck2, seed);
        sessions.put(matchId, new Session(state, engine));
        return state;
    }

    public Optional<GameState> getSession(String matchId) {
        return Optional.ofNullable(sessions.get(matchId)).map(Session::state);
    }

    public Optional<GameEngineFacade> getEngine(String matchId) {
        return Optional.ofNullable(sessions.get(matchId)).map(Session::engine);
    }

    public void removeSession(String matchId) {
        sessions.remove(matchId);
    }

    public Long createMatch(String player1Username, Long deckId) {
        Long id = idSeq.getAndIncrement();
        matchMeta.put(id, new MatchMeta(id, "WAITING", player1Username, null,
            Instant.now().toString(), 0, deckId, null));
        return id;
    }

    public List<MatchMeta> listSummaries() {
        return List.copyOf(matchMeta.values());
    }

    public Optional<MatchMeta> getMeta(Long matchId) {
        return Optional.ofNullable(matchMeta.get(matchId));
    }

    public boolean isParticipant(Long matchId, String username) {
        return getMeta(matchId)
                .map(meta -> isParticipant(username, meta))
                .orElse(false);
    }

    public boolean isParticipant(String matchId, String username) {
        try {
            return isParticipant(Long.valueOf(matchId), username);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public boolean isParticipant(String username, MatchMeta meta) {
        return username != null
                && (username.equals(meta.player1Username()) || username.equals(meta.player2Username()));
    }

    public boolean joinMatch(Long matchId, String player2Username, Long deckId) {
        MatchMeta m = matchMeta.get(matchId);
        if (m == null || !"WAITING".equals(m.status())) return false;
        matchMeta.put(matchId, new MatchMeta(m.id(), "ACTIVE", m.player1Username(),
            player2Username, m.createdAt(), m.turnNumber(), m.player1DeckId(), deckId));
        return true;
    }

    public void recordMatchResult(Long matchId, String winnerUsername) {
        matchWinners.put(matchId, winnerUsername);
        MatchMeta m = matchMeta.get(matchId);
        if (m != null) {
            matchMeta.put(matchId, new MatchMeta(m.id(), "FINISHED", m.player1Username(),
                m.player2Username(), m.createdAt(), m.turnNumber(), m.player1DeckId(), m.player2DeckId()));
        }
    }

    public java.util.Optional<String> getWinnerUsername(Long matchId) {
        return java.util.Optional.ofNullable(matchWinners.get(matchId));
    }
}
