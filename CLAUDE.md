# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Dev mode (live reload, Dev UI at http://localhost:8080/q/dev/)
./mvnw quarkus:dev

# Run tests
./mvnw test

# Run a single test class
./mvnw test -pl rest -Dtest=GreetingResourceTest

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
  - `se.oskr.core.domain` — JPA entities (`Product`, `StockEntry`) and enums (`Category`, `Unit`)
  - `se.oskr.core.service` — Application-scoped services (`ProductService`, `StockService`) with transactional methods
- **`rest/`** — HTTP layer only. The OpenAPI spec (`rest/src/main/resources/openapi.yaml`) is the source of truth; JAX-RS interfaces are **generated** by the OpenAPI Generator Maven plugin during build. `ProductResource` and `StockResource` implement the generated interfaces.
- **`app/`** — Aggregator module. Brings together `core` and `rest`, holds `application.properties` and `import.sql`.

**Key pattern:** Add an endpoint by updating `openapi.yaml` first, then implement the generated interface in `rest/`. Business logic goes in a service in `core/`.

## Database

PostgreSQL, accessed via Hibernate ORM Panache. In dev/test mode, Quarkus Dev Services automatically starts a PostgreSQL container — no local database setup needed.

Schema is dropped and recreated on each start (`quarkus.hibernate-orm.database.generation=drop-and-create`). Seed data lives in `app/src/main/resources/import.sql`.

Production credentials are supplied via environment variables: `DB_USER`, `DB_PASSWORD`, `DB_URL`.

## OpenAPI Code Generation

The `rest` module runs `openapi-generator-maven-plugin` (jaxrs-spec, interface-only) targeting packages `se.oskr.api` (interfaces) and `se.oskr.model` (models). Generated sources are produced during `generate-sources` phase and should not be edited manually.

Tags in the spec determine which interface a method belongs to: tag `Products` → `ProductsApi`, tag `Stock` → `StockApi`. `ProductResource` and `StockResource` implement these. `dateLibrary: java8` is set so date fields use `java.time.LocalDate`.

The `rest` module's `@QuarkusTest` tests need `quarkus-jdbc-postgresql` as a test-scoped dependency (Dev Services spins up Postgres automatically).