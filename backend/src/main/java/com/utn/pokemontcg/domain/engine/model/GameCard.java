package com.utn.pokemontcg.domain.engine.model;

import java.util.List;
import java.util.Map;

public record GameCard(
    String id,
    String name,
    String supertype,
    List<String> subtypes,
    int hp,
    List<Map<String, Object>> attacks,
    String weaknessType,
    String resistanceType,
    int retreatCost
) {}
