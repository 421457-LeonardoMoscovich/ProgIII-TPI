# Sprint 6 — Calidad y entrega

**Objetivo:** cerrar cobertura de tests, documentación completa, opcionales de bonus, despliegue y preparar la entrega final.

**Duración estimada:** 1–2 semanas
**RF/RNF cubiertos:** RNF-01, RNF-02, RNF-03, RNF-05, RNF-06. Opcionales.

## Testing

### QA-04 — Cobertura global JaCoCo ≥80%
- Revisar reporte, cubrir gaps en services/controllers
- Excluir DTOs y configuraciones de la cobertura
- **Done:** reporte generado en `target/site/jacoco/index.html`, ≥80%

### QA-05 — Cobertura crítica ≥90%
- `RuleValidator`, `DamageCalculator`, `StatusEffectManager` deben estar ≥90%
- Validar umbrales en el build con `jacoco-maven-plugin`
- **Done:** build falla si baja del umbral

### QA-06 — Tests de integración completos
Cubrir casos principales:
- Partida completa (ya hecho en Sprint 4, reforzar)
- Mulligan múltiple (3 seguidos ambos jugadores)
- Evolución Básico → Fase 1 → Fase 2
- Knockout de Pokémon normal (1 premio) y EX (2 premios)
- Victoria por premios / por KO total / por mazo vacío
- Muerte Súbita
- **Done:** todos verdes en CI

### QA-07 — Tests E2E ampliados
- Reforzar el flujo básico de Sprint 5
- Agregar: reconexión, drag & drop de energía, condición especial aplicada visualmente
- **Done:** verde en CI

## Rendimiento y seguridad

### PERF-01 — Verificar RNF-01
- Benchmarks de latencia de acción: <200ms P95
- Búsqueda de cartas: <500ms P95
- Optimizar imágenes (webp, lazy loading) si no estaba
- Revisar índices de BD (evitar N+1 con `@EntityGraph` o fetch joins)
- **Done:** métricas documentadas en `docs/performance.md`

### SEC-01 — Auditoría de seguridad
- Escaneo de dependencias con OWASP Dependency Check o `mvn versions:display-dependency-updates`
- Sin vulnerabilidades críticas/altas (RNF-05)
- Verificar que la mano del rival nunca llega al cliente (test de inspección de payload)
- Sanitizar entradas del frontend (nombres de mazos, chat si existe)
- **Done:** reporte limpio

### SEC-02 — (Opcional +1/feature) Autenticación JWT
- Login/registro con Spring Security + JWT
- Token en header `Authorization: Bearer`
- Interceptor en Angular
- **Done:** si se incluye, todos los endpoints requieren auth

## Documentación

### DOC-03 — README.md final
- Descripción del proyecto
- Stack y requisitos (Java 21, Node 20+, Docker)
- Instrucciones de instalación y arranque local (backend, frontend, Postgres)
- Variables de entorno
- Comandos útiles (tests, build, cobertura)
- Diagrama de arquitectura (incluir imagen)
- Link a documentación técnica
- **Done:** un desarrollador externo levanta todo sin ayuda

### DOC-04 — Documentación técnica
- `docs/arquitectura.md`: capas, patrones, decisiones
- `docs/decisiones/*.md`: ADRs de decisiones importantes (WebSocket vs SSE, JSONB vs tablas normalizadas, etc.)
- `docs/manual-despliegue.md`: cómo desplegar backend + frontend + BD en un servidor
- **Done:** completa y revisada

### DOC-05 — Swagger/OpenAPI revisado
- Todos los endpoints con descripción, ejemplos, códigos de respuesta
- Schemas bien tipados
- Exportar a `docs/openapi.json`
- **Done:** documentación navegable en Swagger UI

### DOC-06 — Script SQL + seed
- Exportar esquema completo (DDL)
- Seed data: usuarios de prueba, mazos de ejemplo del set xy1
- Orden de ejecución documentado
- **Done:** `scripts/db/schema.sql` + `scripts/db/seed.sql`

## Opcionales (bonus +15)

### OPT-01 — Mazo temático funcional (+10)
- Seleccionar una estrategia temática del set xy1 (ej. mazo de fuego con Charizard)
- Mazo válido y jugable, probado en partidas
- Incluir como seed
- **Done:** mazo cargado y jugable

### OPT-02 — Animaciones (+2)
- Animaciones CSS/Angular para: ataque (flash de daño), evolución (transición), knockout (fade-out)
- No afectar rendimiento
- **Done:** visuales notables sin lag

### OPT-03 — Chat entre jugadores (+2)
- Canal WS adicional `/topic/match/{id}/chat`
- UI de chat lateral
- Sanitización de mensajes
- **Done:** chat funcional durante la partida

### OPT-04 — Historial/ranking (+1)
- Tabla `match_history` por jugador
- Vista de historial de partidas con resultado
- Ranking simple por wins
- **Done:** visible en perfil del jugador

### OPT-05 — Pokémon Megaevolución (bonus de RF-02)
- Implementar Megaevolución como evolución de Pokémon-EX
- Regla especial: al convertirse en Mega, el turno termina inmediatamente
- KO da 2 cartas de Premio
- **Done:** testeado y funcional

## Entrega final

### REL-01 — Despliegue
- Backend dockerizado, Dockerfile multi-stage
- Frontend buildeado y servido (Nginx, Vercel, Netlify u opción del equipo)
- docker-compose de producción
- **Done:** URL pública accesible o instrucciones claras de despliegue local

### REL-02 — Video demo (recomendado)
- Video corto (3–5 min) mostrando: creación de mazo, creación de partida, partida con dos jugadores, victoria
- **Done:** link en README

### REL-03 — Revisión final previa a entrega
- Checklist de todos los RF-* y RNF-*
- Probar en navegadores objetivo (Chrome, Firefox, Safari, Edge)
- Probar en desktop y tablet
- Tag de release en Git (`v1.0.0`)
- **Done:** entregado a tiempo en GitHub Classroom

## Definition of Done del Sprint
- [ ] Cobertura global ≥80%, crítica ≥90%, validada en CI
- [ ] Toda la documentación actualizada y completa
- [ ] Al menos 1 opcional implementado
- [ ] Sin vulnerabilidades críticas/altas
- [ ] Despliegue funcional
- [ ] Proyecto tageado y entregado
