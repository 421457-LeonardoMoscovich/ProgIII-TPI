package com.utn.pokemontcg.api.ws;

import com.utn.pokemontcg.api.dto.ws.AckDto;
import com.utn.pokemontcg.api.dto.ws.GameActionDto;
import com.utn.pokemontcg.application.service.MatchSessionService;
import com.utn.pokemontcg.domain.engine.ActionResult;
import com.utn.pokemontcg.domain.engine.action.GameAction;
import com.utn.pokemontcg.domain.engine.model.GameState;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
public class MatchActionController {

    private final MatchSessionService matchSessionService;
    private final GameEventPublisher eventPublisher;
    private final SimpMessagingTemplate broker;
    private final UserRepository userRepository;

    public MatchActionController(MatchSessionService matchSessionService,
                                 GameEventPublisher eventPublisher,
                                 SimpMessagingTemplate broker,
                                 UserRepository userRepository) {
        this.matchSessionService = matchSessionService;
        this.eventPublisher = eventPublisher;
        this.broker = broker;
        this.userRepository = userRepository;
    }

    @MessageMapping("/match/{matchId}/action")
    public void action(@DestinationVariable String matchId,
                       GameActionDto dto,
                       Principal principal) {
        if (principal == null) {
            return;
        }

        Long userId = userRepository.findByUsername(principal.getName())
                .map(user -> user.getId())
                .orElse(null);
        if (userId == null) {
            ack(principal, AckDto.nack("Usuario no encontrado"));
            return;
        }

        if (!matchSessionService.isParticipant(matchId, principal.getName())) {
            ack(principal, AckDto.nack("No participás en esta partida"));
            return;
        }

        GameState state = matchSessionService.getSession(matchId).orElse(null);
        var engine = matchSessionService.getEngine(matchId).orElse(null);
        if (state == null || engine == null) {
            ack(principal, AckDto.nack("Partida no encontrada o no iniciada"));
            return;
        }
        if (!userId.equals(state.getPlayer1().getUserId()) && !userId.equals(state.getPlayer2().getUserId())) {
            ack(principal, AckDto.nack("Usuario no pertenece a la sesión"));
            return;
        }

        GameAction action;
        try {
            action = toAction(userId, dto);
        } catch (RuntimeException e) {
            ack(principal, AckDto.nack(e.getMessage()));
            return;
        }

        ActionResult result = engine.applyAction(state, action);
        if (!result.isOk()) {
            ack(principal, AckDto.nack(result.getRejectionReason()));
            return;
        }

        long sequence = eventPublisher.publish(
                matchId,
                state.getPlayer1().getUserId(),
                state.getPlayer2().getUserId(),
                engine.getLastEvents());
        ack(principal, AckDto.success(sequence));
    }

    private void ack(Principal principal, AckDto ack) {
        broker.convertAndSendToUser(principal.getName(), "/queue/ack", ack);
    }

    private GameAction toAction(Long userId, GameActionDto dto) {
        if (dto == null || dto.type() == null || dto.type().isBlank()) {
            throw new IllegalArgumentException("Tipo de acción obligatorio");
        }
        Map<String, Object> payload = dto.payload() == null ? Map.of() : dto.payload();
        return switch (dto.type()) {
            case "PLAY_BASIC" -> new GameAction.PlayBasicPokemon(
                    userId,
                    string(payload, "cardId"),
                    bool(payload, "toBench"));
            case "ATTACH_ENERGY" -> new GameAction.AttachEnergy(
                    userId,
                    string(payload, "energyCardId"),
                    string(payload, "targetInPlayId"));
            case "ATTACK" -> new GameAction.Attack(userId, number(payload, "attackIndex").intValue());
            case "PASS" -> new GameAction.Pass(userId);
            case "PLAY_TRAINER" -> new GameAction.PlayTrainer(userId, string(payload, "cardId"));
            case "EVOLVE" -> new GameAction.Evolve(
                    userId,
                    string(payload, "evolutionCardId"),
                    string(payload, "targetInPlayId"));
            case "RETREAT" -> new GameAction.Retreat(userId, list(payload, "discardedEnergyIds"));
            default -> throw new IllegalArgumentException("Acción no soportada: " + dto.type());
        };
    }

    private String string(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private Boolean bool(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    private Number number(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof Number n ? n : Integer.parseInt(String.valueOf(value));
    }

    private List<String> list(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
