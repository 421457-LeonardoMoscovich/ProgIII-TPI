# Manual De Despliegue

## Requisitos

- Docker 24+
- Docker Compose v2
- Puerto `80` disponible para frontend
- Puerto `8080` disponible para backend si se expone publicamente

## Variables

Crear un archivo `.env.prod` fuera del repositorio o en el entorno del servidor:

```bash
POSTGRES_DB=pokemontcg
POSTGRES_USER=pokemontcg
POSTGRES_PASSWORD=change-me
JWT_SECRET=change-me-with-at-least-32-random-bytes
FRONTEND_PORT=80
BACKEND_PORT=8080
```

No commitear este archivo.

## Build Y Arranque

Desde la raiz del repositorio:

```bash
docker compose --env-file .env.prod -f config/docker-compose.prod.yml up --build -d
```

Servicios:

| Servicio | Puerto | Descripcion |
| --- | --- | --- |
| `frontend` | `FRONTEND_PORT` | Angular servido por Nginx. |
| `backend` | `BACKEND_PORT` | Spring Boot API + WebSocket. |
| `postgres` | interno | PostgreSQL 16 con volumen persistente. |

## Verificacion

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/v3/api-docs
```

Abrir:

- Frontend: `http://localhost`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## Actualizacion

```bash
git pull
docker compose --env-file .env.prod -f config/docker-compose.prod.yml up --build -d
```

Flyway aplica migraciones al iniciar el backend con perfil `prod`.

## Rollback

Volver al commit anterior y reconstruir:

```bash
git checkout <commit-anterior>
docker compose --env-file .env.prod -f config/docker-compose.prod.yml up --build -d
```

El volumen de PostgreSQL no se elimina con este comando.

