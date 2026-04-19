package com.utn.pokemontcg.infrastructure.persistence;

import com.utn.pokemontcg.domain.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardRepository extends JpaRepository<Card, String> {

    List<Card> findBySetCodeOrderByNumberAsc(String setCode);

    long countBySetCode(String setCode);
}
