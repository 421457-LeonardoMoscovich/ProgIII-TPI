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

    @Query(value = """
            SELECT * FROM cards
            WHERE (:name IS NULL OR name ILIKE '%' || :name || '%')
              AND (:supertype IS NULL OR supertype = :supertype)
              AND (:subtype IS NULL OR subtypes @> CAST(:subtype AS jsonb))
            ORDER BY CAST(number AS integer) ASC
            """, nativeQuery = true)
    List<Card> search(@Param("name") String name,
                      @Param("supertype") String supertype,
                      @Param("subtype") String subtype);
}
