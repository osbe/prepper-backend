# prepper-backend

REST API for tracking a household emergency supply — what you stock, how much of it you want on hand, and what is about to expire.

You define **products** (e.g. "Canned tomatoes", target 20 cans) and record **stock entries** against them (12 cans, bought 2026-01-04, expires 2027-06-01, stored in the basement). The API then answers the questions that actually matter: what has expired, what expires soon, and what has run low against its target.

Built with [Quarkus](https://quarkus.io/) 3.31 on Java 21, backed by PostgreSQL.

## Quickstart

```bash
./mvnw quarkus:dev
```

That's it — no local database setup. In dev and test mode Quarkus Dev Services starts a PostgreSQL container automatically.

The API is on <http://localhost:8080>, the Dev UI on <http://localhost:8080/q/dev/>.

Every endpoint requires HTTP Basic Auth. Two users are seeded at startup; in dev they are `admin` / `admin` and `user` / `user`:

```bash
curl -u admin:admin http://localhost:8080/products
```

Create a product and add stock to it:

```bash
curl -u admin:admin -X POST http://localhost:8080/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Canned tomatoes","category":"PRESERVED_FOOD","unit":"CANS","targetQuantity":20}'

curl -u admin:admin -X POST http://localhost:8080/products/1/stock \
  -H 'Content-Type: application/json' \
  -d '{"quantity":12,"expiryDate":"2027-06-01","location":"Basement"}'
```

## API

The full contract is in [`rest/src/main/resources/openapi.yaml`](rest/src/main/resources/openapi.yaml) — that file is the source of truth, not the Java code. There is no Swagger UI: `quarkus-smallrye-openapi` is deliberately not a dependency, since the spec is hand-written input to code generation rather than something derived from the code. Read it directly or point any OpenAPI viewer at it.

| Method | Path | Description |
|---|---|---|
| `GET` | `/products` | List products (optional `?category=`), each with its `currentStock` |
| `POST` | `/products` | Create a product |
| `GET` | `/products/{id}` | Get one product with current stock |
| `PUT` | `/products/{id}` | Update a product |
| `DELETE` | `/products/{id}` | Delete a product **and all its stock entries** |
| `GET` | `/products/{id}/stock` | Stock entries for a product, oldest expiry first |
| `POST` | `/products/{id}/stock` | Add a stock entry |
| `PUT` | `/stock/{id}` | Replace all fields of a stock entry |
| `PATCH` | `/stock/{id}` | Update only the remaining quantity |
| `DELETE` | `/stock/{id}` | Delete a stock entry (fully consumed) |
| `GET` | `/stock/expired` | Entries whose expiry date has passed |
| `GET` | `/stock/expiring?days=N` | Entries expiring within N days (`days` defaults to 30) |
| `GET` | `/stock/low` | Products running low against their target |

Health checks are exposed separately at `/q/health` (plus `/q/health/live` and `/q/health/ready`, which the Helm chart's probes use). They require no authentication.

### Authentication and roles

HTTP Basic Auth, backed by users in the database. Two roles:

- **`user`** — read-only. All `GET` endpoints.
- **`admin`** — everything, including all writes. The seeded admin holds both roles.

Passwords come from `APP_ADMIN_PASSWORD` and `APP_USER_PASSWORD`, defaulting to `admin` / `user` in dev. Both users are deleted and recreated on every application start, so changing those variables and restarting is how you rotate them.

Unauthenticated requests get `401`; a `user` attempting a write gets `403`.

### Business rules worth knowing

These are computed by the backend and are not configurable:

- **Low stock** (`GET /stock/low`) means current stock is **below 25% of the product's target quantity**. Results are sorted by fill ratio, emptiest first. A product with a target quantity of `0` is never low — nothing is below zero — so it never appears here even with no stock at all.
- **`expiryStatus`** on a stock entry is `EXPIRED` once the expiry date has passed, `APPROACHING` when it falls within the next **30 days**, and `null` otherwise. It is intended to be combined with the product category as an i18n key by the frontend, e.g. `action.WATER.APPROACHING`.
- `expiryDate` is **required** when creating or replacing a stock entry, so entries made through the API always have one. The column itself is nullable; should a null-expiry row exist, it sorts last in `GET /products/{id}/stock` and appears in neither `/stock/expired` nor `/stock/expiring`.
- `/stock/expired` and `/stock/expiring` never overlap: an entry expiring **today** counts as expiring, not expired. Both are ordered oldest expiry first.
- Deleting a product cascades to every stock entry belonging to it.

### Validation

Required fields are enforced by Bean Validation against the `required:` lists in the OpenAPI spec — the generator emits `@NotNull`, so the spec is what the code checks. Two shapes of `400` come back:

- **A missing required field** returns JSON: `{"title": "Constraint Violation", "status": 400, "violations": [{"field": "createProduct.productRequest.name", "message": "must not be null"}]}`
- **A value that is not accepted** — an unknown category or unit, say — returns a plain-text message.

## Architecture

Three Maven modules, layered strictly:

```
core/   domain entities (Product, StockEntry, User) + services — no HTTP
rest/   JAX-RS resources, auth, OpenAPI spec — no business logic
app/    aggregator: runnable app, configuration, Helm chart, Dockerfiles
```

**The OpenAPI spec drives the HTTP layer.** `openapi-generator-maven-plugin` generates the JAX-RS interfaces (`se.oskr.api`) and DTOs (`se.oskr.model`) into `rest/target/generated-sources/` at build time; `ProductResource` and `StockResource` implement those interfaces. Generated sources are never edited by hand.

To add an endpoint: edit `openapi.yaml`, rebuild so the interface regenerates, implement the new method in the matching resource, and put the logic in a service in `core/`. The `tags` on an operation decide which interface it lands on — `Products` → `ProductsApi`, `Stock` → `StockApi`.

Authentication uses a custom `UserIdentityProvider` rather than `quarkus-security-jpa`, because that extension only scans entities in the application's own module and would not see `User` in `core/`.

### Database

PostgreSQL via Hibernate ORM Panache. The schema is managed with `quarkus.hibernate-orm.database.generation=update`, so it evolves in place rather than being recreated; override with `DB_SCHEMA_GENERATION` in prod. There is no seed data — `import.sql` is intentionally empty, and stock is added through the API.

## Development

```bash
./mvnw quarkus:dev                                  # dev mode, live reload
./mvnw test -pl rest -am                            # tests (-am builds core first)
./mvnw verify -Dquarkus.package.jar.type=uber-jar   # integration tests, against the packaged app
./mvnw spotless:apply                               # format (Google Java Format)
./mvnw package                                      # build

# A single test class. -Dsurefire.failIfNoSpecifiedTests=false is required: -am also
# builds core, which has no tests, and surefire fails there without it.
./mvnw test -pl rest -am -Dtest=ProductResourceTest -Dsurefire.failIfNoSpecifiedTests=false
```

`@QuarkusTest` tests live in `rest/`; they authenticate with `.auth().basic("admin", "admin")`. CI runs `spotless:check`, the test suite, and a package build on every push and PR to `main` — formatting failures break the build, so run `spotless:apply` before pushing.

### Configuration

| Environment variable | Used in | Description |
|---|---|---|
| `DB_URL` | prod | JDBC URL, e.g. `jdbc:postgresql://host:5432/prepper` |
| `DB_USER` | prod | Database username |
| `DB_PASSWORD` | prod | Database password |
| `DB_SCHEMA_GENERATION` | prod | Hibernate schema mode, defaults to `update`. Set through the chart's `db.schemaGeneration` value when deploying with Helm |
| `APP_ADMIN_PASSWORD` | all | Seeded admin password, defaults to `admin` |
| `APP_USER_PASSWORD` | all | Seeded user password, defaults to `user` |

In dev and test none of these need to be set.

## Packaging

```bash
./mvnw package
java -jar app/target/quarkus-app/quarkus-run.jar
```

This produces `quarkus-run.jar` in `app/target/quarkus-app/` — not an über-jar, since dependencies are copied into `app/target/quarkus-app/lib/`. For a single self-contained jar, build with `-Dquarkus.package.jar.type=uber-jar` and run `java -jar app/target/*-runner.jar`.

A native executable can be built with `./mvnw package -Dnative`, or `-Dnative -Dquarkus.native.container-build=true` without a local GraalVM. See the [Quarkus Maven tooling guide](https://quarkus.io/guides/maven-tooling).

## Deployment and releases

- [DEPLOY.md](DEPLOY.md) — running it on Kubernetes, from a local cluster to the published Helm chart.
- [RELEASING.md](RELEASING.md) — cutting a release, and what the tag triggers.

Docker images are published to `ghcr.io/osbe/prepper-backend` and Helm charts to `oci://ghcr.io/osbe/charts` on every tagged release.
