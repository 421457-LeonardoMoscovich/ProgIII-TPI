package com.utn.pokemontcg.api.controller;

import com.utn.pokemontcg.api.dto.DeckDto;
import com.utn.pokemontcg.api.dto.ValidationResultDto;
import com.utn.pokemontcg.application.service.DeckService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/decks")
@Tag(name = "Decks", description = "CRUD de mazos y validación")
public class DeckController {

    private final DeckService deckService;

    public DeckController(DeckService deckService) {
        this.deckService = deckService;
    }

    @GetMapping
    public List<DeckDto> list(@AuthenticationPrincipal String username) {
        return deckService.listForUser(username);
    }

    @PostMapping
    public DeckDto create(@AuthenticationPrincipal String username,
                          @RequestParam String name) {
        return deckService.create(username, name);
    }

    @PutMapping("/{id}/cards")
    public DeckDto updateCards(@AuthenticationPrincipal String username,
                               @PathVariable Long id,
                               @RequestBody Map<String, Integer> cardQuantities) {
        return deckService.updateCards(username, id, cardQuantities);
    }

    @PostMapping("/{id}/validate")
    public ValidationResultDto validate(@AuthenticationPrincipal String username,
                                        @PathVariable Long id) {
        return deckService.validate(username, id);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal String username,
                       @PathVariable Long id) {
        deckService.delete(username, id);
    }
}
