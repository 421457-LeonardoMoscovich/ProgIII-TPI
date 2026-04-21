package com.utn.pokemontcg.api.dto;

import java.util.List;

public record DeckDto(Long id, String name, boolean valid, List<DeckCardDto> cards,
                      String createdAt, String updatedAt) {}
