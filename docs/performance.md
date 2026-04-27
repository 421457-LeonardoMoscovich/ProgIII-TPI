# Performance

## Objetivos Sprint 6

| RNF | Objetivo |
| --- | --- |
| Accion de partida | P95 menor a 200 ms. |
| Busqueda de cartas | P95 menor a 500 ms. |
| Build frontend | Sin warnings de presupuesto. |
| Backend verify | Tests + JaCoCo verdes. |

## Estado Actual

| Medicion | Resultado local |
| --- | --- |
| `frontend npm run build` | Verde, sin warnings. |
| `backend ./mvnw verify -q` | Verde, con JaCoCo gate activo. |
| Cobertura global filtrada | Mayor o igual a 80% por gate de build. |
| Cobertura critica engine | Mayor o igual a 90% por gate de build. |

## Como Medir Latencia De Acciones

1. Levantar backend y frontend.
2. Crear dos usuarios y dos mazos validos.
3. Crear una partida y unir el segundo usuario.
4. En DevTools, medir el tiempo entre `sendAction()` y el ACK recibido en `/user/queue/ack`.
5. Repetir al menos 30 acciones y calcular P95.

Ejemplo de datos a registrar:

```text
action,type,latency_ms
1,PLAY_BASIC,42
2,ATTACH_ENERGY,38
3,ATTACK,61
```

## Riesgos Pendientes

- No hay benchmark automatizado de WebSocket P95 todavia.
- La busqueda de cartas usa operadores PostgreSQL (`ILIKE`, JSONB `@>`) y debe medirse contra PostgreSQL real, no H2.
- Las imagenes vienen de URLs externas; si se agregan imagenes locales, usar formatos comprimidos y lazy loading.

