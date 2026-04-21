package com.utn.pokemontcg.api.dto;

import java.util.List;

public record ValidationResultDto(boolean valid, List<String> errors) {}
