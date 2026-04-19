package com.utn.pokemontcg.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin client around pokemontcg.io v2. Only used by the catalog bootstrap flow —
 * the match engine must NEVER hit this client (see CLAUDE.md architectural invariants).
 */
@Component
public class PokemonTcgApiClient {

    private static final Logger log = LoggerFactory.getLogger(PokemonTcgApiClient.class);
    private static final int PAGE_SIZE = 250;

    private final RestClient restClient;

    public PokemonTcgApiClient(
            @Value("${pokemontcg.api.base-url}") String baseUrl,
            @Value("${pokemontcg.api.timeout-ms}") int timeoutMs,
            @Value("${POKEMONTCG_API_KEY:}") String apiKey) {

        var builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor((req, body, exec) -> {
                    req.getHeaders().add(HttpHeaders.ACCEPT, "application/json");
                    if (apiKey != null && !apiKey.isBlank()) {
                        req.getHeaders().add("X-Api-Key", apiKey);
                    }
                    return exec.execute(req, body);
                });
        this.restClient = builder.build();
        log.info("PokemonTcgApiClient initialized baseUrl={} timeoutMs={} apiKey={}",
                baseUrl, timeoutMs, apiKey == null || apiKey.isBlank() ? "absent" : "present");
    }

    /**
     * Fetches every card in a set, paginating through /cards?q=set.id:<set>.
     * Retries up to 3 times with exponential backoff on transient failures.
     */
    public List<JsonNode> fetchAllCardsInSet(String setId) {
        List<JsonNode> all = new ArrayList<>();
        int page = 1;
        while (true) {
            JsonNode response = fetchPageWithRetry(setId, page);
            JsonNode data = response.path("data");
            if (!data.isArray() || data.isEmpty()) break;
            data.forEach(all::add);
            int total = response.path("totalCount").asInt(-1);
            log.info("Fetched page {} ({} cards) — accumulated {}/{}", page, data.size(), all.size(),
                    total < 0 ? "?" : total);
            if (total >= 0 && all.size() >= total) break;
            if (data.size() < PAGE_SIZE) break;
            page++;
        }
        return all;
    }

    private JsonNode fetchPageWithRetry(String setId, int page) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return restClient.get()
                        .uri(uri -> uri.path("/cards")
                                .queryParam("q", "set.id:" + setId)
                                .queryParam("page", page)
                                .queryParam("pageSize", PAGE_SIZE)
                                .build())
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RuntimeException ex) {
                last = ex;
                long backoff = Duration.ofMillis(500L * (1L << (attempt - 1))).toMillis();
                log.warn("Attempt {}/3 failed for set={} page={} — retrying in {}ms ({})",
                        attempt, setId, page, backoff, ex.getMessage());
                sleep(backoff);
            }
        }
        throw new IllegalStateException("pokemontcg.io fetch failed for set=" + setId + " page=" + page, last);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
