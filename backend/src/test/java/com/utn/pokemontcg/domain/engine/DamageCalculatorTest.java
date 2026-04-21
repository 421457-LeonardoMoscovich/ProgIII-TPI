package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.model.GameCard;
import com.utn.pokemontcg.domain.engine.model.PokemonInPlay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class DamageCalculatorTest {
    private DamageCalculator calc;

    @BeforeEach
    void setUp() { calc = new DamageCalculator(); }

    private PokemonInPlay pokemon(String weaknessType, String resistanceType) {
        var card = new GameCard("id", "Pikachu", "Pokémon", List.of("Basic"),
            60, List.of(), weaknessType, resistanceType, 1);
        return new PokemonInPlay(card);
    }

    @Test
    void noWeaknessNoResistance_returnsBaseDamage() {
        var attacker = pokemon(null, null);
        var defender = pokemon(null, null);
        assertThat(calc.calculate(50, attacker, defender, List.of())).isEqualTo(50);
    }

    @Test
    void weakness_doublesBaseDamage() {
        var attacker = new PokemonInPlay(new GameCard("id", "Char", "Pokémon", List.of("Basic"),
            100, List.of(), null, null, 1));
        var defender = pokemon("Fire", null);
        assertThat(calc.calculate(60, attacker, defender, List.of("Fire"))).isEqualTo(120);
    }

    @Test
    void resistance_subtractsTwenty() {
        var attacker = pokemon(null, null);
        var defender = pokemon(null, "Fire");
        assertThat(calc.calculate(60, attacker, defender, List.of("Fire"))).isEqualTo(40);
    }

    @Test
    void weaknessAndResistance_weaknessFirstThenResistance() {
        // base 60, weakness ×2 = 120, resistance -20 = 100
        var defender = pokemon("Fire", "Fire");
        var attacker = pokemon(null, null);
        assertThat(calc.calculate(60, attacker, defender, List.of("Fire"))).isEqualTo(100);
    }

    @Test
    void resistanceCantGoBelowZero() {
        var attacker = pokemon(null, null);
        var defender = pokemon(null, "Fire");
        assertThat(calc.calculate(10, attacker, defender, List.of("Fire"))).isEqualTo(0);
    }

    @Test
    void damageRoundedToNearestTen() {
        // base 55 — no modifier — rounds to 60
        var attacker = pokemon(null, null);
        var defender = pokemon(null, null);
        assertThat(calc.calculate(55, attacker, defender, List.of())).isEqualTo(60);
    }

    @Test
    void zeroDamage_staysZero() {
        var attacker = pokemon(null, null);
        var defender = pokemon(null, null);
        assertThat(calc.calculate(0, attacker, defender, List.of())).isEqualTo(0);
    }
}
