package com.utn.pokemontcg.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.utn.pokemontcg.domain.model.Card;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CardMapper {

    public Card toEntity(JsonNode node) {
        return new Card(
                text(node, "id"),
                nested(node, "set", "id"),
                text(node, "number"),
                text(node, "name"),
                text(node, "supertype"),
                textList(node.path("subtypes")),
                parseHp(text(node, "hp")),
                textList(node.path("types")),
                retreatCost(node),
                nodeOrNull(node.path("weaknesses")),
                nodeOrNull(node.path("resistances")),
                nodeOrNull(node.path("attacks")),
                nodeOrNull(node.path("rules")),
                text(node, "evolvesFrom"),
                nested(node, "images", "small"),
                nested(node, "images", "large"),
                node);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String nested(JsonNode node, String outer, String inner) {
        JsonNode v = node.path(outer).path(inner);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static List<String> textList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr.isArray()) arr.forEach(e -> out.add(e.asText()));
        return out;
    }

    private static List<String> retreatCost(JsonNode node) {
        JsonNode arr = node.path("retreatCost");
        if (arr.isMissingNode() || arr.isNull()) return List.of();
        return textList(arr);
    }

    private static Integer parseHp(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static JsonNode nodeOrNull(JsonNode n) {
        return n.isMissingNode() || n.isNull() ? null : n;
    }
}
