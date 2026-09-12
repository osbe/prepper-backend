# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Dev mode (live reload, Dev UI at http://localhost:8080/q/dev/)
./mvnw quarkus:dev

# Run tests
./mvnw test

# Run a single test class (-Dsurefire.failIfNoSpecifiedTests=false is required:
# -am also builds core, which has no tests, and surefire fails there without it)
./mvnw test -pl rest -am -Dtest=ProductResourceTest -Dsurefire.failIfNoSpecifiedTests=false

# Run integration tests (requires packaged app)
./mvnw verify -Dquarkus.package.jar.type=uber-jar

# Build
./mvnw package

# Format code (Spotless / Google Java Format)
./mvnw spotless:apply

# Check formatting without applying
./mvnw spotless:check
```

## Architecture

Multi-module Maven project with a strict layered separation:

- **`core/`** — Domain logic and data access, split into two sub-packages:
  - `se.oskr.core.domain` — JPA entities (`Product`, `StockEntry`, `User`) and enums (`Category`, `Unit`)
  - `se.oskr.core.service` — Application-scoped services (`ProductService`, `StockService`) with transactional methods
- **`rest/`** — HTTP layer only. The OpenAPI spec (`rest/src/main/resources/openapi.yaml`) is the source of truth; JAX-RS interfaces are **generated** by the OpenAPI Generator Maven plugin during build. `ProductResource` and `StockResource` implement the generated interfaces.
- **`app/`** — Aggregator module. Brings together `core` and `rest`, holds `application.properties` and `import.sql`.

**Key pattern:** Add an endpoint by updating `openapi.yaml` first, then implement the generated interface in `rest/`. Business logic goes in a service in `core/`.

## Database

PostgreSQL, accessed via Hibernate ORM Panache. In dev/test mode, Quarkus Dev Services automatically starts a PostgreSQL container — no local database setup needed.

Schema is managed with `quarkus.hibernate-orm.database.generation=update`, so it evolves in place instead of being recreated on each start. In prod this is overridable via `DB_SCHEMA_GENERATION` (defaults to `update`).

There is no seed data: `app/src/main/resources/import.sql` is intentionally empty, and stock is added through the API.

Production configuration is supplied via environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD` for the datasource, and `APP_ADMIN_PASSWORD` / `APP_USER_PASSWORD` for the seeded users (defaulting to `admin` / `user` in dev). `Startup.java` deletes and recreates both users on every application start, so changing those variables and restarting rotates the passwords.

## OpenAPI Code Generation

The `rest` module runs `openapi-generator-maven-plugin` (jaxrs-spec, interface-only) targeting packages `se.oskr.api` (interfaces) and `se.oskr.model` (models). Generated sources are produced during `generate-sources` phase and should not be edited manually.

`useBeanValidation=true`, so the `required:` lists in the spec become `@NotNull` on the generated models and `@Valid @NotNull` on the body parameters — the spec is what actually enforces required fields at runtime (via `quarkus-hibernate-validator`). Do not hand-write null checks in the resources for fields the spec already marks required; mark them required in the spec instead.

Tags in the spec determine which interface a method belongs to: tag `Products` → `ProductsApi`, tag `Stock` → `StockApi`. `ProductResource` and `StockResource` implement these. `dateLibrary: java8` is set so date fields use `java.time.LocalDate`.

`@QuarkusTest` tests live in the `rest` module. Auth uses a custom `UserIdentityProvider` (in `rest`) that delegates to `UserService` (in `core`) — this avoids `quarkus-security-jpa`'s `ApplicationIndexBuildItem` scanning limitation. The `rest` test classpath needs `rest/src/test/resources/application.properties`, which sets `quarkus.http.auth.basic=true`, `quarkus.datasource.db-kind=postgresql`, the two `app.auth.*-password` values the tests authenticate with, and `quarkus.hibernate-orm.database.generation=drop-and-create` (tests start from an empty schema, unlike dev and prod which use `update`).

## Releases

Versions live in six files that must agree: `app/helm/Chart.yaml` (`version` and `appVersion`), the four POMs, and `info.version` in `openapi.yaml`. The `release-hooks` script bumps all of them; the release workflow fails if the pushed `v*.*.*` tag does not match `Chart.yaml`. See `RELEASING.md`.

## Documentation

`README.md` (overview, API, dev workflow), `DEPLOY.md` (Kubernetes), `RELEASING.md` (release flow). Keep them in sync when changing config, env vars, or endpoints — the API table in `README.md` and the Helm value table in `DEPLOY.md` are the two that drift most easily.
