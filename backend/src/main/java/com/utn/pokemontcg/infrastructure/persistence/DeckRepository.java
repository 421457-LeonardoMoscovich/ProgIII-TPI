package com.utn.pokemontcg.infrastructure.persistence;

import com.utn.pokemontcg.domain.model.Deck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeckRepository extends JpaRepository<Deck, Long> {

    List<Deck> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT d FROM Deck d JOIN FETCH d.cards dc JOIN FETCH dc.card WHERE d.id = :id")
    Optional<Deck> findByIdWithCards(@Param("id") Long id);
}
