package com.utn.pokemontcg.api.dto;

import com.utn.pokemontcg.api.dto.ws.GameEventDto;

import java.util.List;

public record MatchReconnectDto(
        FilteredGameStateDto snapshot,
        List<GameEventDto> recentEvents,
        long currentSequence
) {}
