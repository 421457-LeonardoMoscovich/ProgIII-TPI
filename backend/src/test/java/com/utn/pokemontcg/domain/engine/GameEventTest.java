package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.event.GameEvent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GameEventTest {
    @Test
    void cardDrawnEvent_holdsCorrectData() {
        var evt = new GameEvent.CardDrawn("match1", 1L, "xy1-5");
        assertThat(evt.matchId()).isEqualTo("match1");
        assertThat(evt.userId()).isEqualTo(1L);
        assertThat(evt.cardId()).isEqualTo("xy1-5");
    }

    @Test
    void damageDealtEvent_holdsCorrectData() {
        var evt = new GameEvent.DamageDealt("match1", "xy1-1", "xy1-2", 60);
        assertThat(evt.amount()).isEqualTo(60);
    }
}
