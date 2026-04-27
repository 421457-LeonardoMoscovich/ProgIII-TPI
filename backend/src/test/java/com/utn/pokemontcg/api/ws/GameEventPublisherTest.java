package com.utn.pokemontcg.api.ws;

import com.utn.pokemontcg.api.dto.ws.GameEventDto;
import com.utn.pokemontcg.domain.engine.event.GameEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameEventPublisherTest {

    private final SimpMessagingTemplate broker = mock(SimpMessagingTemplate.class);
    private final GameEventPublisher publisher = new GameEventPublisher(broker);

    @Test
    void publish_sendsToPersonalMatchTopicsOnly() {
        long sequence = publisher.publish("match-1", 1L, 2L, List.of(
                new GameEvent.TurnEnded("match-1", 1L, 5)
        ));

        assertThat(sequence).isEqualTo(1L);
        assertThat(publisher.currentSequence("match-1")).isEqualTo(1L);
        verify(broker).convertAndSend(eq("/topic/match/match-1/1"), org.mockito.ArgumentMatchers.any(GameEventDto.class));
        verify(broker).convertAndSend(eq("/topic/match/match-1/2"), org.mockito.ArgumentMatchers.any(GameEventDto.class));
    }

    @Test
    void publish_cardDrawnMasksCardIdForOpponent() {
        publisher.publish("m1", 1L, 2L, List.of(new GameEvent.CardDrawn("m1", 1L, "card-42")));

        ArgumentCaptor<GameEventDto> playerCaptor = ArgumentCaptor.forClass(GameEventDto.class);
        ArgumentCaptor<GameEventDto> opponentCaptor = ArgumentCaptor.forClass(GameEventDto.class);
        verify(broker).convertAndSend(eq("/topic/match/m1/1"), playerCaptor.capture());
        verify(broker).convertAndSend(eq("/topic/match/m1/2"), opponentCaptor.capture());

        GameEvent.CardDrawn playerPayload = (GameEvent.CardDrawn) playerCaptor.getValue().payload();
        GameEvent.CardDrawn opponentPayload = (GameEvent.CardDrawn) opponentCaptor.getValue().payload();
        assertThat(playerPayload.cardId()).isEqualTo("card-42");
        assertThat(opponentPayload.cardId()).isNull();
    }

    @Test
    void recentEvents_returnsViewerMaskedEventsAfterSequence() {
        publisher.publish("m1", 1L, 2L, List.of(
                new GameEvent.TurnEnded("m1", 1L, 0),
                new GameEvent.CardDrawn("m1", 1L, "hidden-card")
        ));

        List<GameEventDto> playerEvents = publisher.recentEvents("m1", 1L, 1L);
        List<GameEventDto> opponentEvents = publisher.recentEvents("m1", 2L, 1L);

        assertThat(playerEvents).hasSize(1);
        assertThat(opponentEvents).hasSize(1);
        assertThat(playerEvents.getFirst().sequence()).isEqualTo(2L);
        assertThat(((GameEvent.CardDrawn) playerEvents.getFirst().payload()).cardId()).isEqualTo("hidden-card");
        assertThat(((GameEvent.CardDrawn) opponentEvents.getFirst().payload()).cardId()).isNull();
    }
}
