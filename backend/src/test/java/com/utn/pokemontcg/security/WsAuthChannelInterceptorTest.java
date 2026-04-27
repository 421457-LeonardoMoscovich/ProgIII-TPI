package com.utn.pokemontcg.security;

import com.utn.pokemontcg.application.service.MatchSessionService;
import com.utn.pokemontcg.config.JwtUtil;
import com.utn.pokemontcg.domain.model.User;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WsAuthChannelInterceptorTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final MatchSessionService matchSessionService = mock(MatchSessionService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final WsAuthChannelInterceptor interceptor =
            new WsAuthChannelInterceptor(jwtUtil, matchSessionService, userRepository);

    @Test
    void connect_withValidToken_setsPrincipal() {
        when(jwtUtil.isValid("good-token")).thenReturn(true);
        when(jwtUtil.extractUsername("good-token")).thenReturn("player1");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer good-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("player1");
    }

    @Test
    void connect_withInvalidToken_throwsException() {
        when(jwtUtil.isValid("bad-token")).thenReturn(false);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer bad-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void nonConnect_passesWithoutTokenCheck() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        assertThat(result).isNotNull();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void subscribe_toOwnPersonalTopic_passes() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(userRepository.findByUsername("player1")).thenReturn(Optional.of(user));
        when(matchSessionService.getMeta(10L)).thenReturn(Optional.of(new MatchSessionService.MatchMeta(
                10L, "ACTIVE", "player1", "player2", "now", 1, 100L, 200L
        )));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/match/10/1");
        accessor.setUser(new UsernamePasswordAuthenticationToken("player1", null));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        assertThat(result).isNotNull();
    }

    @Test
    void subscribe_toOtherPlayerPersonalTopic_throwsException() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(userRepository.findByUsername("player1")).thenReturn(Optional.of(user));
        when(matchSessionService.getMeta(10L)).thenReturn(Optional.of(new MatchSessionService.MatchMeta(
                10L, "ACTIVE", "player1", "player2", "now", 1, 100L, 200L
        )));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/match/10/2");
        accessor.setUser(new UsernamePasswordAuthenticationToken("player1", null));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribe_byNonParticipant_throwsException() {
        when(matchSessionService.getMeta(10L)).thenReturn(Optional.of(new MatchSessionService.MatchMeta(
                10L, "ACTIVE", "player1", "player2", "now", 1, 100L, 200L
        )));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/match/10");
        accessor.setUser(new UsernamePasswordAuthenticationToken("intruder", null));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void sendAction_byParticipant_passes() {
        when(matchSessionService.getMeta(10L)).thenReturn(Optional.of(new MatchSessionService.MatchMeta(
                10L, "ACTIVE", "player1", "player2", "now", 1, 100L, 200L
        )));
        when(matchSessionService.isParticipant("player1", new MatchSessionService.MatchMeta(
                10L, "ACTIVE", "player1", "player2", "now", 1, 100L, 200L
        ))).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/match/10/action");
        accessor.setUser(new UsernamePasswordAuthenticationToken("player1", null));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        assertThat(result).isNotNull();
    }

    @Test
    void sendAction_byNonParticipant_throwsException() {
        when(matchSessionService.getMeta(10L)).thenReturn(Optional.of(new MatchSessionService.MatchMeta(
                10L, "ACTIVE", "player1", "player2", "now", 1, 100L, 200L
        )));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/match/10/action");
        accessor.setUser(new UsernamePasswordAuthenticationToken("intruder", null));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
