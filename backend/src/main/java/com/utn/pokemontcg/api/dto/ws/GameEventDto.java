package com.utn.pokemontcg.api.dto.ws;

public record GameEventDto(String type, Object payload, long sequence) {}
