package com.utn.pokemontcg.api.ws;

import com.utn.pokemontcg.api.dto.ws.AckDto;
import com.utn.pokemontcg.api.dto.ws.GameActionDto;
import com.utn.pokemontcg.application.service.MatchSessionService;
import com.utn.pokemontcg.domain.engine.GameEngineFacade;
import com.utn.pokemontcg.domain.engine.model.GameState;
import com.utn.pokemontcg.domain.engine.model.PlayerState;
import com.utn.pokemontcg.domain.model.User;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchActionControllerTest {

    private final MatchSessionService matchSessionService = mock(MatchSessionService.class);
    private final GameEventPublisher eventPublisher = mock(GameEventPublisher.class);
    private final SimpMessagingTemplate broker = mock(SimpMessagingTemplate.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MatchActionController controller = new MatchActionController(
            matchSessionService,
            eventPublisher,
            broker,
            userRepository);

    @Test
    void action_fromNonParticipantSendsNack() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(99L);
        when(userRepository.findByUsername("intruder")).thenReturn(Optional.of(user));
        when(matchSessionService.isParticipant("10", "intruder")).thenReturn(false);

        controller.action("10", new GameActionDto("PASS", Map.of()), principal("intruder"));

        ArgumentCaptor<AckDto> ack = ArgumentCaptor.forClass(AckDto.class);
        verify(broker).convertAndSendToUser(eq("intruder"), eq("/queue/ack"), ack.capture());
        assertThat(ack.getValue().ok()).isFalse();
        assertThat(ack.getValue().reason()).contains("No particip");
    }

    @Test
    void action_withMissingTypeSendsNack() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(userRepository.findByUsername("player1")).thenReturn(Optional.of(user));
        when(matchSessionService.isParticipant("10", "player1")).thenReturn(true);
        when(matchSessionService.getSession("10")).thenReturn(Optional.of(new GameState(
                "10",
                new PlayerState(1L),
                new PlayerState(2L))));
        when(matchSessionService.getEngine("10")).thenReturn(Optional.of(new GameEngineFacade()));

        controller.action("10", new GameActionDto(null, Map.of()), principal("player1"));

        ArgumentCaptor<AckDto> ack = ArgumentCaptor.forClass(AckDto.class);
        verify(broker).convertAndSendToUser(eq("player1"), eq("/queue/ack"), ack.capture());
        assertThat(ack.getValue().ok()).isFalse();
        assertThat(ack.getValue().reason()).contains("Tipo de acción obligatorio");
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
