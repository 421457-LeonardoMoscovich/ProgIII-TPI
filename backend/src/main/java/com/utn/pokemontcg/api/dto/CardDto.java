package com.utn.pokemontcg.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.utn.pokemontcg.domain.model.Card;

import java.util.List;

public record CardDto(
        String id,
        String setCode,
        String number,
        String name,
        String supertype,
        List<String> subtypes,
        Integer hp,
        List<String> types,
        List<String> retreatCost,
        JsonNode weaknesses,
        JsonNode resistances,
        JsonNode attacks,
        String evolvesFrom,
        String imageSmall,
        String imageLarge
) {
    public static CardDto from(Card c) {
        return new CardDto(
                c.getId(), c.getSetCode(), c.getNumber(), c.getName(), c.getSupertype(),
                c.getSubtypes(), c.getHp(), c.getTypes(), c.getRetreatCost(),
                c.getWeaknesses(), c.getResistances(), c.getAttacks(),
                c.getEvolvesFrom(), c.getImageSmall(), c.getImageLarge());
    }
}
