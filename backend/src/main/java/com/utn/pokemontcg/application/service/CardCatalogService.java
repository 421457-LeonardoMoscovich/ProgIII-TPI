package com.utn.pokemontcg.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.utn.pokemontcg.domain.model.Card;
import com.utn.pokemontcg.infrastructure.external.CardMapper;
import com.utn.pokemontcg.infrastructure.external.PokemonTcgApiClient;
import com.utn.pokemontcg.infrastructure.persistence.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads the xy1 card set into the local cache on startup (RF-03, RF-04).
 * The match engine only ever reads from the local cache — never the external API.
 */
@Service
public class CardCatalogService {

    private static final Logger log = LoggerFactory.getLogger(CardCatalogService.class);

    private final CardRepository repo;
    private final PokemonTcgApiClient client;
    private final CardMapper mapper;
    private final String setId;

    public CardCatalogService(CardRepository repo, PokemonTcgApiClient client, CardMapper mapper,
                              @Value("${pokemontcg.api.set}") String setId) {
        this.repo = repo;
        this.client = client;
        this.mapper = mapper;
        this.setId = setId;
    }

    public List<Card> findBySet(String setCode) {
        return repo.findBySetCodeOrderByNumberAsc(setCode);
    }

    @Transactional
    public int bootstrapCache() {
        long existing = repo.countBySetCode(setId);
        if (existing > 0) {
            log.info("Card cache already populated for set={} ({} cards) — skipping fetch", setId, existing);
            return 0;
        }
        log.info("Card cache empty for set={} — fetching from pokemontcg.io", setId);
        List<JsonNode> raw = client.fetchAllCardsInSet(setId);
        List<Card> entities = raw.stream().map(mapper::toEntity).toList();
        repo.saveAll(entities);
        log.info("Persisted {} cards for set={}", entities.size(), setId);
        return entities.size();
    }

    @Configuration
    static class Bootstrap {
        @Bean
        ApplicationRunner cardCatalogBootstrap(CardCatalogService service) {
            return args -> service.bootstrapCache();
        }
    }
}
