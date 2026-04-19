package com.utn.pokemontcg.api.controller;

import com.utn.pokemontcg.api.dto.CardDto;
import com.utn.pokemontcg.application.service.CardCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
@Tag(name = "Cards", description = "Catálogo local de cartas (set XY1)")
public class CardController {

    private final CardCatalogService service;

    public CardController(CardCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista las cartas del set solicitado desde el caché local")
    public List<CardDto> list(@RequestParam(defaultValue = "xy1") String set) {
        return service.findBySet(set).stream().map(CardDto::from).toList();
    }
}
