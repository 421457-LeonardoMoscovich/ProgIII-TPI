package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.model.PokemonInPlay;
import java.util.List;

public class DamageCalculator {

    public int calculate(int baseDamage, PokemonInPlay attacker, PokemonInPlay defender,
                         List<String> attackerTypes) {
        int dmg = baseDamage;

        String weakness = defender.getCard().weaknessType();
        if (weakness != null && attackerTypes.contains(weakness)) {
            dmg *= 2;
        }

        String resistance = defender.getCard().resistanceType();
        if (resistance != null && attackerTypes.contains(resistance)) {
            dmg = Math.max(0, dmg - 20);
        }

        // round to nearest 10
        dmg = (int) (Math.round(dmg / 10.0) * 10);

        return dmg;
    }
}
