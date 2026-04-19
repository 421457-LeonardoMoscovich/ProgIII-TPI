package com.utn.pokemontcg.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "cards")
public class Card {

    @Id
    private String id;

    @Column(name = "set_code", nullable = false)
    private String setCode;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String supertype;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> subtypes;

    private Integer hp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> types;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retreat_cost", nullable = false, columnDefinition = "jsonb")
    private List<String> retreatCost;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode weaknesses;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode resistances;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode attacks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode rules;

    @Column(name = "evolves_from")
    private String evolvesFrom;

    @Column(name = "image_small")
    private String imageSmall;

    @Column(name = "image_large")
    private String imageLarge;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode rawJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Card() {}

    public Card(String id, String setCode, String number, String name, String supertype,
                List<String> subtypes, Integer hp, List<String> types, List<String> retreatCost,
                JsonNode weaknesses, JsonNode resistances, JsonNode attacks, JsonNode rules,
                String evolvesFrom, String imageSmall, String imageLarge, JsonNode rawJson) {
        this.id = id;
        this.setCode = setCode;
        this.number = number;
        this.name = name;
        this.supertype = supertype;
        this.subtypes = subtypes;
        this.hp = hp;
        this.types = types;
        this.retreatCost = retreatCost;
        this.weaknesses = weaknesses;
        this.resistances = resistances;
        this.attacks = attacks;
        this.rules = rules;
        this.evolvesFrom = evolvesFrom;
        this.imageSmall = imageSmall;
        this.imageLarge = imageLarge;
        this.rawJson = rawJson;
    }

    public String getId() { return id; }
    public String getSetCode() { return setCode; }
    public String getNumber() { return number; }
    public String getName() { return name; }
    public String getSupertype() { return supertype; }
    public List<String> getSubtypes() { return subtypes; }
    public Integer getHp() { return hp; }
    public List<String> getTypes() { return types; }
    public List<String> getRetreatCost() { return retreatCost; }
    public JsonNode getWeaknesses() { return weaknesses; }
    public JsonNode getResistances() { return resistances; }
    public JsonNode getAttacks() { return attacks; }
    public JsonNode getRules() { return rules; }
    public String getEvolvesFrom() { return evolvesFrom; }
    public String getImageSmall() { return imageSmall; }
    public String getImageLarge() { return imageLarge; }
    public JsonNode getRawJson() { return rawJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
