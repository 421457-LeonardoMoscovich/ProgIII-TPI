package com.utn.pokemontcg.application.service;

import com.utn.pokemontcg.domain.engine.GameEngineFacade;
import com.utn.pokemontcg.domain.engine.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchSessionServiceTest {

    private MatchSessionService service;

    @BeforeEach
    void setUp() { service = new MatchSessionService(); }

    @Test
    void startSession_storesGameState() {
        GameState state = service.startSession("match-1", 1L, 2L, java.util.List.of(), java.util.List.of(), 42L);
        assertThat(state).isNotNull();
        assertThat(state.getMatchId()).isNotNull();
    }

    @Test
    void getSession_returnsStoredState() {
        service.startSession("match-2", 1L, 2L, java.util.List.of(), java.util.List.of(), 0L);
        Optional<GameState> found = service.getSession("match-2");
        assertThat(found).isPresent();
    }

    @Test
    void getSession_emptyForUnknownMatch() {
        assertThat(service.getSession("unknown")).isEmpty();
    }

    @Test
    void removeSession_deletesState() {
        service.startSession("match-3", 1L, 2L, java.util.List.of(), java.util.List.of(), 0L);
        service.removeSession("match-3");
        assertThat(service.getSession("match-3")).isEmpty();
    }

    @Test
    void getEngine_returnsEngineForStartedSession() {
        service.startSession("match-4", 1L, 2L, java.util.List.of(), java.util.List.of(), 0L);
        assertThat(service.getEngine("match-4")).isPresent();
    }
}
