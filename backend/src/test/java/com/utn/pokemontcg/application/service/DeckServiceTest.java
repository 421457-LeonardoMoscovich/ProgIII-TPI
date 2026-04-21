package com.utn.pokemontcg.application.service;

import com.utn.pokemontcg.domain.model.Deck;
import com.utn.pokemontcg.domain.model.User;
import com.utn.pokemontcg.infrastructure.persistence.CardRepository;
import com.utn.pokemontcg.infrastructure.persistence.DeckRepository;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeckServiceTest {

    @Mock DeckRepository deckRepo;
    @Mock UserRepository userRepo;
    @Mock CardRepository cardRepo;

    DeckService service;

    User user = new User("alice", "alice@test.com", "hash");
    User other = new User("bob", "bob@test.com", "hash");

    @BeforeEach
    void setUp() {
        service = new DeckService(deckRepo, userRepo, cardRepo);
        setId(user, 1L);
        setId(other, 2L);
    }

    @Test
    void create_savesNewDeck() {
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        var saved = new Deck(user, "My Deck");
        setId(saved, 10L);
        when(deckRepo.save(any(Deck.class))).thenReturn(saved);

        var dto = service.create("alice", "My Deck");

        assertThat(dto.name()).isEqualTo("My Deck");
        verify(deckRepo).save(any(Deck.class));
    }

    @Test
    void validate_throwsForbidden_whenNotOwner() {
        when(userRepo.findByUsername("bob")).thenReturn(Optional.of(other));
        var deck = new Deck(user, "Alice's Deck");
        setId(deck, 5L);
        when(deckRepo.findByIdWithCards(5L)).thenReturn(Optional.of(deck));

        assertThatThrownBy(() -> service.validate("bob", 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void listForUser_returnsOnlyUserDecks() {
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(deckRepo.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        var result = service.listForUser("alice");

        assertThat(result).isEmpty();
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
