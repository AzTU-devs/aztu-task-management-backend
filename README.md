# AzTU Kanban — Backend

Spring Boot 3.3 / Java 21 REST API behind **AzTU Kanban**, a Jira-style task
management system for Azerbaijan Technical University.

* PostgreSQL 16 + JPA/Hibernate
* Stateless JWT authentication (BCrypt password hashing, role based access)
* Platforms → Boards → Columns → Tasks, with comments, watchers, labels and a full activity trail
* In-app notifications **and** transactional e-mail (assignment, status change, new comment,
  deadline reminder, account created, password reset)
* Swagger UI at `/swagger-ui.html`

---

## Data model

```
Platform  (admin-managed, e.g. "Education Platform" / EDU)
  └── Board            (admin-managed, key e.g. LMS → tasks LMS-1, LMS-2 …)
        ├── BoardColumn  (Backlog / To Do / In Progress / In Review / Done, optional WIP limit)
        └── Task         (type, priority, assignee, reporter, watchers, labels,
                          start/due date, story points, estimate, order index)
              ├── TaskComment
              └── Activity      (created / updated / moved / assigned / commented)
```

`Role` is one of `ADMIN`, `MANAGER`, `MEMBER`. **Only `ADMIN` may create platforms,
boards, columns and user accounts** — every signed-in user can create, edit, move and
comment on tasks.

## Running locally

```bash
cp .env.example .env          # then edit the values
docker compose up -d --build  # API on http://localhost:8080
```

Without Docker (needs a local PostgreSQL and JDK 21):

```bash
export DB_HOST=localhost DB_NAME=aztu_kanban DB_USER=aztu DB_PASSWORD=aztu
mvn spring-boot:run
```

## Configuration

Every setting is an environment variable — see [.env.example](.env.example).

| Variable | Default | Purpose |
| --- | --- | --- |
| `API_PORT` | `8080` | Host port published by docker compose |
| `DB_EXPOSED_PORT` | `5433` | Host port for PostgreSQL (set to `""` in production to keep it private) |
| `JWT_SECRET` | — (**required**) | ≥ 64 random characters: `openssl rand -base64 64 \| tr -d '\n'` |
| `JWT_EXPIRATION_HOURS` | `24` | Token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated list of frontend origins |
| `FRONTEND_URL` | `http://localhost:3000` | Used to build the links inside e-mails |
| `MAIL_ENABLED` | `false` | When `false` e-mails are only logged, never sent |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | Gmail SMTP | SMTP credentials |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_FULL_NAME` | `admin@aztu.edu.az` / `Admin123!` | First bootstrap admin |
| `ADMIN2_*` / `ADMIN3_*` | empty | Two more optional bootstrap admins — skipped when blank |
| `SEED_DEMO_DATA` | `true` | Seeds 3 platforms, 4 boards and demo tasks on an empty database |
| `REMINDERS_CRON` | `0 0 8 * * *` | Daily deadline-reminder job (Asia/Baku) |

### Bootstrap administrators

Up to **three** administrator accounts are created when the application boots
(`ADMIN_*`, `ADMIN2_*`, `ADMIN3_*`). Slots with a blank e-mail or password are skipped,
and duplicate addresses are ignored with a warning.

* An account that **does not exist** is created as `ADMIN` with the configured password.
* An account that **already exists** is never overwritten — its password is left alone.
  A still-active account is promoted to `ADMIN` if it was not one yet.
* A **deactivated** account stays deactivated. Re-enabling it here would silently undo an
  offboarding on the next restart while the old password still worked, so the slot is
  skipped with a warning instead.

> **Offboarding an administrator:** deactivate them in *Users*, **and clear their slot in
> `.env`**. Leaving the slot in place is harmless while they stay deactivated, but it will
> keep logging a warning on every boot.

> Set the passwords **before the first start**. Because existing accounts are never
> overwritten, changing `ADMIN_PASSWORD` later has no effect. To recover from a typo,
> either sign in with another administrator and use *Users → reset password*, or delete
> that row and restart:
> ```bash
> docker exec -it aztu-kanban-db psql -U aztu -d aztu_kanban \
>   -c "DELETE FROM app_user WHERE email = 'admin@aztu.edu.az';"
> docker compose up -d          # NOT `restart` - that reuses the old environment
> ```
>
> `docker compose restart` re-runs the existing container with the environment it was
> created with, so a corrected password in `.env` would not be picked up. `up -d`
> recreates the container when `.env` has changed.

Real credentials belong in `.env`, which is git-ignored — never in `.env.example`,
`application.yml` or `docker-compose.yml`.

> `.env` is parsed by Docker Compose, **not** by a shell. `!`, `&` and spaces are safe
> as-is. A literal `$` must be doubled (`$$`) — quoting does **not** stop Compose from
> interpolating `$VAR`. And never `source .env` / `set -a; . .env` in bash: a value such
> as `Pa55word!&` would be cut at the `&` and the rest run as a background command.

## API overview

| Method | Path | Access |
| --- | --- | --- |
| `POST` | `/api/auth/login` | public |
| `GET`/`PUT` | `/api/auth/me` | authenticated |
| `POST` | `/api/auth/change-password` | authenticated |
| `GET` | `/api/users/directory` | authenticated (assignee picker) |
| `GET`/`POST`/`PUT`/`DELETE` | `/api/users…` | **ADMIN** |
| `POST` | `/api/users/{id}/reset-password` | **ADMIN** |
| `GET` | `/api/platforms` | authenticated |
| `POST`/`PUT`/`DELETE` | `/api/platforms…` | **ADMIN** |
| `GET` | `/api/boards`, `/api/boards/{key}`, `/api/boards/{key}/kanban` | authenticated |
| `POST`/`PUT`/`DELETE` | `/api/boards…`, `/api/boards/columns/{id}` | **ADMIN** |
| `GET`/`POST`/`PUT`/`PATCH`/`DELETE` | `/api/tasks…` | authenticated |
| `GET`/`POST` | `/api/tasks/{id}/comments` | authenticated |
| `GET`/`POST` | `/api/notifications…` | authenticated |
| `GET` | `/api/dashboard/stats` | authenticated |

Full interactive documentation: `http://<host>:<API_PORT>/swagger-ui.html`.

## Health

`GET /actuator/health` — used by the container healthcheck and by any reverse proxy.

## Notes

* Schema is managed by Hibernate (`DDL_AUTO=update`). Set `DDL_AUTO=validate` and add
  migrations if you later need strict schema control.
* Deleting a user is blocked while tasks are still assigned to them — deactivate instead.
* Deleting a platform is blocked while it still owns boards.
