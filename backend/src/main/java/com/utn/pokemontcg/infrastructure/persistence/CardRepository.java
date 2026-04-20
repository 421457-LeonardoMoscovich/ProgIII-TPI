package com.utn.pokemontcg.infrastructure.persistence;

import com.utn.pokemontcg.domain.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CardRepository extends JpaRepository<Card, String> {

    @Query("SELECT c FROM Card c WHERE c.setCode = :setCode ORDER BY CAST(c.number AS integer) ASC")
    List<Card> findBySetCodeOrderByNumberAsc(@Param("setCode") String setCode);

    long countBySetCode(String setCode);
}
