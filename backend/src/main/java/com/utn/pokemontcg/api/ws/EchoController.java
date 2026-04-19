package com.utn.pokemontcg.api.ws;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.OffsetDateTime;

/**
 * Spike POC (Sprint 1 / SPIKE-01) — exercises the STOMP transport end-to-end.
 * Client sends to /app/echo, server broadcasts to /topic/echo.
 */
@Controller
public class EchoController {

    public record EchoIn(String message) {}
    public record EchoOut(String echoed, String timestamp) {}

    @MessageMapping("/echo")
    @SendTo("/topic/echo")
    public EchoOut echo(EchoIn in) {
        return new EchoOut(in.message(), OffsetDateTime.now().toString());
    }
}
