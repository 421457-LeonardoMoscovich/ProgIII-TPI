package com.utn.pokemontcg.security;

import com.utn.pokemontcg.config.JwtUtil;
import com.utn.pokemontcg.application.service.MatchSessionService;
import com.utn.pokemontcg.domain.model.User;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class WsAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final MatchSessionService matchSessionService;
    private final UserRepository userRepository;

    public WsAuthChannelInterceptor(JwtUtil jwtUtil,
                                    MatchSessionService matchSessionService,
                                    UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.matchSessionService = matchSessionService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            return authenticateConnect(message, accessor);
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscribe(accessor);
        }
        if (accessor.getCommand() == StompCommand.SEND) {
            authorizeSend(accessor);
        }
        return message;
    }

    private Message<?> authenticateConnect(Message<?> message, StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing WebSocket bearer token");
        }

        String token = header.substring(7);
        if (!jwtUtil.isValid(token)) {
            throw new MessageDeliveryException("Invalid WebSocket bearer token");
        }

        String username = jwtUtil.extractUsername(token);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        accessor.setLeaveMutable(true);
        return message;
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || destination.startsWith("/user/queue/")) {
            return;
        }
        if (!destination.startsWith("/topic/match/")) {
            throw new MessageDeliveryException("Unauthorized WebSocket destination");
        }
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Missing WebSocket principal");
        }

        String[] parts = destination.split("/");
        if (parts.length != 4 && parts.length != 5) {
            throw new MessageDeliveryException("Invalid match topic");
        }

        Long matchId;
        try {
            matchId = Long.valueOf(parts[3]);
        } catch (NumberFormatException ex) {
            throw new MessageDeliveryException("Invalid match id");
        }

        String username = accessor.getUser().getName();
        MatchSessionService.MatchMeta meta = matchSessionService.getMeta(matchId)
                .orElseThrow(() -> new MessageDeliveryException("Match not found"));
        if (!username.equals(meta.player1Username()) && !username.equals(meta.player2Username())) {
            throw new MessageDeliveryException("User is not a match participant");
        }

        if (parts.length == 5) {
            Long viewerId;
            try {
                viewerId = Long.valueOf(parts[4]);
            } catch (NumberFormatException ex) {
                throw new MessageDeliveryException("Invalid viewer id");
            }

            Optional<User> user = userRepository.findByUsername(username);
            if (user.isEmpty() || !viewerId.equals(user.get().getId())) {
                throw new MessageDeliveryException("Cannot subscribe to another player's topic");
            }
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/app/match/")) {
            return;
        }
        if (!destination.endsWith("/action")) {
            throw new MessageDeliveryException("Unauthorized WebSocket destination");
        }
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Missing WebSocket principal");
        }

        String[] parts = destination.split("/");
        if (parts.length != 5) {
            throw new MessageDeliveryException("Invalid match action destination");
        }

        Long matchId;
        try {
            matchId = Long.valueOf(parts[3]);
        } catch (NumberFormatException ex) {
            throw new MessageDeliveryException("Invalid match id");
        }

        String username = accessor.getUser().getName();
        MatchSessionService.MatchMeta meta = matchSessionService.getMeta(matchId)
                .orElseThrow(() -> new MessageDeliveryException("Match not found"));
        if (!matchSessionService.isParticipant(username, meta)) {
            throw new MessageDeliveryException("User is not a match participant");
        }
    }
}
