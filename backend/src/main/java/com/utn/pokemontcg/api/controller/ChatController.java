package com.utn.pokemontcg.api.controller;

import com.utn.pokemontcg.api.dto.ChatMessageDto;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatController {

    private static final int MAX_CONTENT_LENGTH = 200;

    @MessageMapping("/match/{matchId}/chat")
    @SendTo("/topic/match/{matchId}/chat")
    public ChatMessageDto chat(
            @DestinationVariable String matchId,
            ChatMessageDto message,
            Principal principal) {
        String sanitized = sanitize(message.content());
        return new ChatMessageDto(principal.getName(), sanitized, System.currentTimeMillis());
    }

    private String sanitize(String raw) {
        if (raw == null) return "";
        String stripped = raw.replaceAll("<[^>]*>", "");
        return stripped.length() > MAX_CONTENT_LENGTH
                ? stripped.substring(0, MAX_CONTENT_LENGTH)
                : stripped;
    }
}
