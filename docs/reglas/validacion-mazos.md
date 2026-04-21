# Reglas de Validación de Mazos — Set XY1

Implementado en `DeckValidator` (`domain/service/DeckValidator.java`).

## Reglas

| # | Regla | Condición de fallo | Mensaje de error |
|---|-------|--------------------|-----------------|
| 1 | **60 cartas exactas** | Total ≠ 60 | `El mazo debe tener exactamente 60 cartas. Cantidad actual: N.` |
| 2 | **Máx 4 copias por nombre** | Una carta no-Energía Básica aparece más de 4 veces | `La carta 'X' aparece N veces (máximo 4).` |
| 3 | **Máx 1 AS TÁCTICO** | Suma de cartas con subtipo `ACE SPEC` > 1 | `Solo se permite 1 carta AS TÁCTICO. Encontradas: X, Y.` |
| 4 | **Al menos 1 Pokémon Básico** | Ninguna carta cumple supertype=Pokémon y subtypes contiene "Basic" | `El mazo debe incluir al menos 1 Pokémon Básico.` |

## Excepción a la regla de copias

La Regla 2 **no aplica** a cartas con `supertype = "Energy"` y subtipo `"Basic"`. Las Energías Básicas pueden incluirse en cualquier cantidad.

## Ejemplos

### Mazo válido mínimo
- 4× cualquier Pokémon Básico  
- 56× cualquier Energía Básica  
→ `{ valid: true, errors: [] }`

### Casos inválidos

| Contenido | Errores esperados |
|-----------|-------------------|
| 59 cartas | Regla 1 |
| 61 cartas | Regla 1 |
| 5× mismo Trainer + 55 Energía Básica | Reglas 2 y 4 |
| 60× Energía Básica | Regla 4 |
| 1× ACE SPEC-A + 1× ACE SPEC-B + 58 otras | Regla 3 |
| 2× mismo ACE SPEC + 58 otras | Reglas 2 y 3 |

## Endpoint

```
POST /api/decks/{id}/validate
Authorization: Bearer <token>

200 OK
{
  "valid": false,
  "errors": [
    "El mazo debe tener exactamente 60 cartas. Cantidad actual: 59.",
    "El mazo debe incluir al menos 1 Pokémon Básico."
  ]
}
```

## Cobertura de tests

`DeckValidatorTest` cubre los 11 casos de la especificación con ≥95% de cobertura de líneas.  
`DeckBuilderIntegrationTest` verifica los 4 casos negativos end-to-end contra la base de datos.
