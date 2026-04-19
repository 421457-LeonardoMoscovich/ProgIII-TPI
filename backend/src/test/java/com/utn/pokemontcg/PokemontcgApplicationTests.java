package com.utn.pokemontcg;

import com.utn.pokemontcg.infrastructure.external.PokemonTcgApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class PokemontcgApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Avoid hitting pokemontcg.io during the smoke test. */
    @MockitoBean
    PokemonTcgApiClient apiClient;

    @Test
    void contextLoads() {
        when(apiClient.fetchAllCardsInSet("xy1")).thenReturn(List.of());
    }
}
