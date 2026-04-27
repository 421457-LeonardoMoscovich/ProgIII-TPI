package com.utn.pokemontcg.api.dto.ws;

import java.util.Map;

public record GameActionDto(String type, Map<String, Object> payload) {}
