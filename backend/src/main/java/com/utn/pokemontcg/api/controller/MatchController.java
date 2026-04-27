package com.utn.pokemontcg.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.api.dto.CreateMatchRequest;
import com.utn.pokemontcg.api.dto.FilteredGameStateDto;
import com.utn.pokemontcg.api.dto.JoinMatchRequest;
import com.utn.pokemontcg.api.dto.MatchDto;
import com.utn.pokemontcg.api.dto.MatchReconnectDto;
import com.utn.pokemontcg.api.dto.MatchSummaryDto;
import com.utn.pokemontcg.api.ws.GameEventPublisher;
import com.utn.pokemontcg.application.service.MatchSessionService;
import com.utn.pokemontcg.domain.engine.model.GameCard;
import com.utn.pokemontcg.domain.engine.model.GameState;
import com.utn.pokemontcg.domain.engine.model.PlayerState;
import com.utn.pokemontcg.domain.engine.model.PokemonInPlay;
import com.utn.pokemontcg.domain.engine.model.StatusCondition;
import com.utn.pokemontcg.domain.model.Card;
import com.utn.pokemontcg.domain.model.Deck;
import com.utn.pokemontcg.domain.model.User;
import com.utn.pokemontcg.infrastructure.persistence.DeckRepository;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matches")
@Tag(name = "Matches", description = "Lobby, match lifecycle, and filtered game state")
@SecurityRequirement(name = "bearerAuth")
public class MatchController {

    private static final TypeReference<List<Map<String, Object>>> ATTACKS_TYPE = new TypeReference<>() {};

    private final MatchSessionService matchSessionService;
    private final GameEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final DeckRepository deckRepository;
    private final ObjectMapper objectMapper;

    public MatchController(MatchSessionService matchSessionService,
                           GameEventPublisher eventPublisher,
                           UserRepository userRepository,
                           DeckRepository deckRepository,
                           ObjectMapper objectMapper) {
        this.matchSessionService = matchSessionService;
        this.eventPublisher = eventPublisher;
        this.userRepository = userRepository;
        this.deckRepository = deckRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<MatchSummaryDto> list() {
        return matchSessionService.listSummaries().stream()
                .map(m -> new MatchSummaryDto(
                        m.id(),
                        m.status(),
                        m.player1Username(),
                        m.player2Username(),
                        m.createdAt(),
                        m.turnNumber()))
                .toList();
    }

    @PostMapping
    public MatchDto create(@AuthenticationPrincipal String username,
                           @RequestBody CreateMatchRequest request) {
        requireOwnedValidDeck(username, request.deckId());
        Long id = matchSessionService.createMatch(username, request.deckId());
        return new MatchDto(id, "WAITING");
    }

    @PostMapping("/{id}/join")
    @Transactional
    public void join(@AuthenticationPrincipal String username,
                     @PathVariable Long id,
                     @RequestBody JoinMatchRequest request) {
        MatchSessionService.MatchMeta meta = matchSessionService.getMeta(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida no encontrada"));
        if (meta.player1Username().equals(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No podés unirte a tu propia partida");
        }

        Deck player1Deck = requireOwnedValidDeck(meta.player1Username(), meta.player1DeckId());
        Deck player2Deck = requireOwnedValidDeck(username, request.deckId());
        User player1 = requireUser(meta.player1Username());
        User player2 = requireUser(username);

        boolean joined = matchSessionService.joinMatch(id, username, request.deckId());
        if (!joined) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La partida ya no está disponible");
        }

        matchSessionService.startSession(
                id.toString(),
                player1.getId(),
                player2.getId(),
                toGameDeck(player1Deck),
                toGameDeck(player2Deck),
                System.nanoTime());
    }

    @GetMapping("/{id}/state")
    @Transactional(readOnly = true)
    public FilteredGameStateDto state(@AuthenticationPrincipal String username,
                                      @PathVariable Long id) {
        MatchSessionService.MatchMeta meta = matchSessionService.getMeta(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida no encontrada"));
        ensureParticipant(username, meta);

        return matchSessionService.getSession(id.toString())
                .map(state -> toFilteredState(id, username, state))
                .orElseGet(() -> waitingState(id, username));
    }

    @GetMapping("/{id}/reconnect")
    @Transactional(readOnly = true)
    public MatchReconnectDto reconnect(@AuthenticationPrincipal String username,
                                       @PathVariable Long id,
                                       @RequestParam(defaultValue = "0") long afterSequence) {
        MatchSessionService.MatchMeta meta = matchSessionService.getMeta(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida no encontrada"));
        ensureParticipant(username, meta);

        User user = requireUser(username);
        FilteredGameStateDto snapshot = matchSessionService.getSession(id.toString())
                .map(state -> toFilteredState(id, username, state))
                .orElseGet(() -> waitingState(id, username));
        String matchId = id.toString();
        return new MatchReconnectDto(
                snapshot,
                eventPublisher.recentEvents(matchId, user.getId(), afterSequence),
                eventPublisher.currentSequence(matchId));
    }

    private FilteredGameStateDto waitingState(Long matchId, String username) {
        User user = requireUser(username);
        return new FilteredGameStateDto(
                matchId,
                user.getId(),
                0,
                user.getId(),
                "DRAW",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null);
    }

    private FilteredGameStateDto toFilteredState(Long matchId, String username, GameState state) {
        User user = requireUser(username);
        PlayerState me = state.getPlayer1().getUserId().equals(user.getId())
                ? state.getPlayer1()
                : state.getPlayer2();
        PlayerState opponent = me == state.getPlayer1() ? state.getPlayer2() : state.getPlayer1();

        return new FilteredGameStateDto(
                matchId,
                user.getId(),
                state.getGlobalTurn(),
                state.getCurrentPlayer().getUserId(),
                state.getTurnPhase().name(),
                me.getPrizes().size(),
                opponent.getPrizes().size(),
                me.getHand().size(),
                opponent.getHand().size(),
                me.getDeck().size(),
                opponent.getDeck().size(),
                me.getDiscardPile().size(),
                opponent.getDiscardPile().size(),
                state.getTurnFlags().isEnergyAttachedThisTurn(),
                state.getTurnFlags().isRetreatedThisTurn(),
                state.getTurnFlags().isAttackDoneThisTurn(),
                state.getTurnFlags().isSupporterPlayedThisTurn(),
                toFieldPokemon(me.getActivePokemon()),
                toFieldPokemon(opponent.getActivePokemon()),
                me.getBench().stream().map(this::toFieldPokemon).toList(),
                opponent.getBench().stream().map(this::toFieldPokemon).toList(),
                me.getHand().stream().map(this::toHandCard).toList(),
                null);
    }

    private void ensureParticipant(String username, MatchSessionService.MatchMeta meta) {
        if (!username.equals(meta.player1Username()) && !username.equals(meta.player2Username())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No participás en esta partida");
        }
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }

    private Deck requireOwnedValidDeck(String username, Long deckId) {
        if (deckId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deckId es obligatorio");
        }
        User user = requireUser(username);
        Deck deck = deckRepository.findByIdWithCards(deckId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mazo no encontrado"));
        if (!deck.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El mazo no pertenece al usuario");
        }
        if (!deck.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mazo debe ser válido para jugar");
        }
        return deck;
    }

    private List<GameCard> toGameDeck(Deck deck) {
        List<GameCard> cards = new ArrayList<>();
        deck.getCards().forEach(deckCard -> {
            GameCard gameCard = toGameCard(deckCard.getCard());
            for (int i = 0; i < deckCard.getQuantity(); i++) {
                cards.add(gameCard);
            }
        });
        return cards;
    }

    private GameCard toGameCard(Card card) {
        return new GameCard(
                card.getId(),
                card.getName(),
                card.getSupertype(),
                card.getSubtypes(),
                card.getHp() != null ? card.getHp() : 0,
                attacks(card.getAttacks()),
                firstType(card.getWeaknesses()),
                firstType(card.getResistances()),
                card.getRetreatCost().size());
    }

    private List<Map<String, Object>> attacks(JsonNode attacks) {
        if (attacks == null || !attacks.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(attacks, ATTACKS_TYPE);
    }

    private String firstType(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }
        JsonNode type = node.get(0).path("type");
        return type.isMissingNode() || type.isNull() ? null : type.asText();
    }

    private FilteredGameStateDto.FieldPokemonDto toFieldPokemon(PokemonInPlay pokemon) {
        if (pokemon == null) {
            return null;
        }

        GameCard card = pokemon.getCard();
        return new FilteredGameStateDto.FieldPokemonDto(
                card.id(),
                card.name(),
                Math.max(card.hp() - pokemon.getDamage(), 0),
                card.hp(),
                pokemon.getDamage(),
                card.retreatCost(),
                energyCounts(pokemon),
                pokemon.getAttachedTools().stream().map(GameCard::name).toList(),
                status(pokemon),
                card.attacks().stream()
                        .map(a -> new FilteredGameStateDto.AttackDto(
                                String.valueOf(a.getOrDefault("name", "")),
                                stringList(a.get("cost")),
                                String.valueOf(a.getOrDefault("damage", "")),
                                String.valueOf(a.getOrDefault("text", ""))))
                        .toList());
    }

    private FilteredGameStateDto.HandCardDto toHandCard(GameCard card) {
        String type = card.subtypes().isEmpty() ? card.supertype() : card.subtypes().get(0);
        return new FilteredGameStateDto.HandCardDto(card.id(), card.name(), type, card.supertype());
    }

    private Map<String, Integer> energyCounts(PokemonInPlay pokemon) {
        Map<String, Integer> counts = new HashMap<>();
        pokemon.getAttachedEnergyIds().forEach(type -> counts.merge(type, 1, Integer::sum));
        return counts;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String status(PokemonInPlay pokemon) {
        if (pokemon.getPrimaryStatus() != StatusCondition.NONE) {
            return pokemon.getPrimaryStatus().name();
        }
        if (pokemon.isBurned()) {
            return "BURNED";
        }
        if (pokemon.isPoisoned()) {
            return "POISONED";
        }
        return null;
    }
}
