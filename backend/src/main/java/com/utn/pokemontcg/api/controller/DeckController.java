package com.utn.pokemontcg.api.controller;

import com.utn.pokemontcg.api.dto.DeckDto;
import com.utn.pokemontcg.api.dto.ValidationResultDto;
import com.utn.pokemontcg.application.service.DeckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/decks")
@Tag(name = "Decks", description = "CRUD de mazos y validación XY1")
@SecurityRequirement(name = "bearerAuth")
public class DeckController {

    private final DeckService deckService;

    public DeckController(DeckService deckService) {
        this.deckService = deckService;
    }

    @GetMapping
    @Operation(summary = "Lista los mazos del usuario autenticado",
               description = "Devuelve todos los mazos del usuario ordenados por fecha de creación descendente.")
    public List<DeckDto> list(@AuthenticationPrincipal String username) {
        return deckService.listForUser(username);
    }

    @PostMapping
    @Operation(summary = "Crea un mazo vacío",
               description = "Crea un nuevo mazo con nombre dado. Las cartas se agregan con PUT /{id}/cards.")
    public DeckDto create(@AuthenticationPrincipal String username,
                          @Parameter(description = "Nombre del mazo (max 100 caracteres)", required = true)
                          @RequestParam String name) {
        return deckService.create(username, name);
    }

    @PutMapping("/{id}/cards")
    @Operation(summary = "Reemplaza las cartas del mazo",
               description = "Recibe un mapa cardId→cantidad y reemplaza completamente las cartas del mazo. " +
                             "Actualiza el flag isValid automáticamente. Solo el dueño puede modificarlo.")
    public DeckDto updateCards(@AuthenticationPrincipal String username,
                               @Parameter(description = "ID del mazo") @PathVariable Long id,
                               @RequestBody Map<String, Integer> cardQuantities) {
        return deckService.updateCards(username, id, cardQuantities);
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Valida el mazo contra las reglas XY1",
               description = "Verifica: 60 cartas exactas, máx 4 copias por nombre (excepto Energía Básica), " +
                             "máx 1 AS TÁCTICO, al menos 1 Pokémon Básico. Devuelve errores accionables.")
    public ValidationResultDto validate(@AuthenticationPrincipal String username,
                                        @Parameter(description = "ID del mazo") @PathVariable Long id) {
        return deckService.validate(username, id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Elimina un mazo",
               description = "Elimina el mazo y todas sus cartas. Solo el dueño puede eliminarlo.")
    public void delete(@AuthenticationPrincipal String username,
                       @Parameter(description = "ID del mazo") @PathVariable Long id) {
        deckService.delete(username, id);
    }
}
