package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.action.GameAction;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GameActionTest {
    @Test
    void playBasicPokemon_holdsBenchSlot() {
        var action = new GameAction.PlayBasicPokemon(1L, "xy1-1", true);
        assertThat(action.toBench()).isTrue();
    }

    @Test
    void attack_holdsAttackIndex() {
        var action = new GameAction.Attack(1L, 0);
        assertThat(action.attackIndex()).isEqualTo(0);
    }
}
