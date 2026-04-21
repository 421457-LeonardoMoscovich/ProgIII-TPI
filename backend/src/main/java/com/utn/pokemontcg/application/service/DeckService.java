package com.utn.pokemontcg.application.service;

import com.utn.pokemontcg.api.dto.DeckCardDto;
import com.utn.pokemontcg.api.dto.DeckDto;
import com.utn.pokemontcg.api.dto.ValidationResultDto;
import com.utn.pokemontcg.domain.model.Deck;
import com.utn.pokemontcg.domain.model.DeckCard;
import com.utn.pokemontcg.domain.service.DeckValidator;
import com.utn.pokemontcg.infrastructure.persistence.CardRepository;
import com.utn.pokemontcg.infrastructure.persistence.DeckRepository;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class DeckService {

    private final DeckRepository deckRepo;
    private final UserRepository userRepo;
    private final CardRepository cardRepo;
    private final DeckValidator validator = new DeckValidator();

    public DeckService(DeckRepository deckRepo, UserRepository userRepo,
                       CardRepository cardRepo) {
        this.deckRepo = deckRepo;
        this.userRepo = userRepo;
        this.cardRepo = cardRepo;
    }

    @Transactional(readOnly = true)
    public List<DeckDto> listForUser(String username) {
        var user = requireUser(username);
        return deckRepo.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public DeckDto create(String username, String name) {
        var user = requireUser(username);
        var deck = deckRepo.save(new Deck(user, name));
        return toDto(deck);
    }

    @Transactional
    public DeckDto updateCards(String username, Long deckId,
                               Map<String, Integer> cardQuantities) {
        var deck = requireOwned(username, deckId);
        deck.getCards().clear();

        for (var entry : cardQuantities.entrySet()) {
            var card = cardRepo.findById(entry.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Carta no encontrada: " + entry.getKey()));
            deck.getCards().add(new DeckCard(deck, card, entry.getValue()));
        }

        var entries = deck.getCards().stream()
                .map(dc -> new DeckValidator.CardEntry(
                        dc.getCard().getName(),
                        dc.getCard().getSupertype(),
                        dc.getCard().getSubtypes(),
                        dc.getQuantity()))
                .toList();

        deck.setValid(validator.validate(entries).valid());
        deck.setUpdatedAt(OffsetDateTime.now());
        return toDto(deckRepo.save(deck));
    }

    public ValidationResultDto validate(String username, Long deckId) {
        var deck = requireOwned(username, deckId);
        var entries = loadEntries(deck);
        var result = validator.validate(entries);
        return new ValidationResultDto(result.valid(), result.errors());
    }

    @Transactional
    public void delete(String username, Long deckId) {
        requireOwned(username, deckId);
        deckRepo.deleteById(deckId);
    }

    private com.utn.pokemontcg.domain.model.User requireUser(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario no encontrado: " + username));
    }

    private Deck requireOwned(String username, Long deckId) {
        var user = requireUser(username);
        var deck = deckRepo.findByIdWithCards(deckId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mazo no encontrado"));
        if (!deck.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        return deck;
    }

    private List<DeckValidator.CardEntry> loadEntries(Deck deck) {
        return deck.getCards().stream()
                .map(dc -> new DeckValidator.CardEntry(
                        dc.getCard().getName(),
                        dc.getCard().getSupertype(),
                        dc.getCard().getSubtypes(),
                        dc.getQuantity()))
                .toList();
    }

    private DeckDto toDto(Deck d) {
        var cards = d.getCards().stream()
                .map(dc -> new DeckCardDto(
                        dc.getCard().getId(),
                        dc.getCard().getName(),
                        dc.getCard().getImageSmall(),
                        dc.getQuantity()))
                .toList();
        return new DeckDto(
                d.getId(), d.getName(), d.isValid(), cards,
                d.getCreatedAt() != null ? d.getCreatedAt().toString() : null,
                d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null);
    }
}
