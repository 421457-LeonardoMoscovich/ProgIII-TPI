package com.utn.pokemontcg.api.ws;

import com.utn.pokemontcg.api.dto.ws.GameEventDto;
import com.utn.pokemontcg.domain.engine.event.GameEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GameEventPublisher {

    private static final int MAX_RECENT_EVENTS_PER_VIEWER = 100;

    private final SimpMessagingTemplate broker;
    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();
    private final Map<HistoryKey, Deque<GameEventDto>> recentEvents = new ConcurrentHashMap<>();

    public GameEventPublisher(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    public long publish(String matchId, Long player1Id, Long player2Id, List<GameEvent> events) {
        long lastSequence = currentSequence(matchId);
        for (GameEvent event : events) {
            long sequence = sequences.computeIfAbsent(matchId, ignored -> new AtomicLong()).incrementAndGet();
            GameEventDto player1Event = toDto(event, payloadFor(event, player1Id), sequence);
            GameEventDto player2Event = toDto(event, payloadFor(event, player2Id), sequence);
            remember(matchId, player1Id, player1Event);
            remember(matchId, player2Id, player2Event);
            broker.convertAndSend("/topic/match/" + matchId + "/" + player1Id, player1Event);
            broker.convertAndSend("/topic/match/" + matchId + "/" + player2Id, player2Event);
            lastSequence = sequence;
        }
        return lastSequence;
    }

    public long currentSequence(String matchId) {
        AtomicLong sequence = sequences.get(matchId);
        return sequence == null ? 0L : sequence.get();
    }

    public List<GameEventDto> recentEvents(String matchId, Long viewerId, long afterSequence) {
        Deque<GameEventDto> history = recentEvents.get(new HistoryKey(matchId, viewerId));
        if (history == null) {
            return List.of();
        }
        synchronized (history) {
            return history.stream()
                    .filter(event -> event.sequence() > afterSequence)
                    .toList();
        }
    }

    private GameEventDto toDto(GameEvent event, Object payload, long sequence) {
        return new GameEventDto(event.getClass().getSimpleName(), payload, sequence);
    }

    private Object payloadFor(GameEvent event, Long viewerId) {
        if (event instanceof GameEvent.CardDrawn drawn && !drawn.userId().equals(viewerId)) {
            return new GameEvent.CardDrawn(drawn.matchId(), drawn.userId(), null);
        }
        return event;
    }

    private void remember(String matchId, Long viewerId, GameEventDto event) {
        Deque<GameEventDto> history = recentEvents.computeIfAbsent(
                new HistoryKey(matchId, viewerId),
                ignored -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(event);
            while (history.size() > MAX_RECENT_EVENTS_PER_VIEWER) {
                history.removeFirst();
            }
        }
    }

    private record HistoryKey(String matchId, Long viewerId) {}
}
