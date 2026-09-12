# Building a client against prepper-backend

Everything a frontend or mobile app needs to talk to this API correctly. The machine-readable
contract is [`rest/src/main/resources/openapi.yaml`](rest/src/main/resources/openapi.yaml); this
document covers the parts a spec cannot express — write semantics, server-side business rules,
and what the API deliberately does not do.

---

## 1. Scope and constraints

Read this first. It rules out a lot of app designs.

- **Two shared accounts, not user accounts.** `admin` and `user` are seeded at startup from
  `APP_ADMIN_PASSWORD` / `APP_USER_PASSWORD`. There is no registration, no password-change
  endpoint, and no password reset.
- **`Startup.java` deletes and recreates both users on every application restart.** Rotating a
  password means changing the env var and restarting. A client holding old credentials simply
  starts getting `401`.
- **Nothing is scoped per user.** Every authenticated caller sees the same single inventory. Do
  not build user profiles, per-user lists, or sharing features — there is no data model for them.
- This is a single-household tool. Expect tens to hundreds of products, not thousands.

## 2. Reaching the server

The spec's `servers:` block lists two entries:

| URL | When |
|---|---|
| `http://localhost:8080` | `./mvnw quarkus:dev`, or `kubectl port-forward -n prepper svc/prepper-backend 8080:8080` |
| `{scheme}://{host}` | A deployed instance — substitute your own host |

**There is no ingress in this repo.** `app/helm/values.yaml` sets `service.type: ClusterIP` and
`app/helm/templates/` contains no `ingress.yaml`, so a fresh `helm install` produces a service
reachable only from inside the cluster. Before a mobile app can reach it, someone must put an
Ingress, LoadBalancer, or tunnel in front of it — that happens outside this repo (see the
`prepper-infra` repo referenced in [DEPLOY.md](DEPLOY.md)).

Make the base URL a build-time or settings-screen configuration value. Do not hardcode
`localhost`: it works in the simulator and fails on a physical device, which is a confusing
failure to debug. On Android, note that an emulator reaches the host machine at `10.0.2.2`,
not `localhost`.

Use HTTPS in production. Basic Auth sends the password in a trivially reversible base64 header
on every single request; over plain HTTP it is exposed on the wire.

## 3. Authentication

HTTP Basic on every endpoint except the health checks. Send the header on every request:

```
Authorization: Basic base64(username + ":" + password)
```

### Login flow

There is no token endpoint and no session. "Logging in" means: collect credentials, verify them
once against `GET /me`, and store them for reuse.

```
GET /me
Authorization: Basic ...

200 → {"username": "admin", "roles": ["admin", "user"], "canWrite": true}
401 → credentials are wrong
```

`GET /me` is the only endpoint whose purpose is credential checking; it is cheap and touches no
inventory data. Use it for the login screen and for a "test connection" action in settings.

### Role gating

- `user` — the read-only `GET` endpoints.
- `admin` — everything, including all writes. The seeded `admin` holds both roles.

**Gate write UI on `canWrite` from `GET /me`, never on the username.** The role lives in the
database `role` column, not in the name, and hiding the "Add product" button because the
username happens not to be `"admin"` is a bug waiting for the day someone renames an account.

A `user` that attempts a write gets `403` with an empty body. Treat that as a programming error
in the client — if you gated the UI correctly, it should be unreachable.

### Storing credentials

Store them in the platform secure store: **Keychain** on iOS, **EncryptedSharedPreferences** or
the Keystore on Android, `expo-secure-store` under Expo, `flutter_secure_storage` under Flutter.
Not `UserDefaults`, not `SharedPreferences`, not `AsyncStorage`, not a plain file.

Note that the server sends `WWW-Authenticate: Basic` on a `401`. That is harmless for a native
HTTP client but triggers the browser's native credential dialog in a WebView-based stack — if
you are building with Capacitor or a WebView, intercept `401` before the browser sees it.

## 4. Data model

### Product

What you want to stock, and how much of it.

| Field | Type | Notes |
|---|---|---|
| `id` | int64 | |
| `name` | string | 1–255 chars |
| `category` | enum | see below |
| `unit` | enum | see below |
| `targetQuantity` | double | ≥ 0. How much you want on hand |
| `currentStock` | double | **read-only, server-computed** — the sum of all its stock entries |
| `notes` | string | ≤ 255 chars |

`currentStock` is derived, never sent. It appears on `Product` responses but not on
`ProductRequest`.

### StockEntry

A specific batch you actually own, belonging to one product.

| Field | Type | Notes |
|---|---|---|
| `id` | int64 | |
| `productId` | int64 | read-only; set by the route you POST to |
| `quantity` | double | ≥ 0 |
| `subType` | string | ≤ 255 chars. Variant, e.g. `"Spaghetti"` for a Pasta product |
| `purchasedDate` | date | `YYYY-MM-DD`, optional |
| `expiryDate` | date | `YYYY-MM-DD`, **required on create and on PUT** |
| `location` | string | ≤ 255 chars, e.g. `"Basement"` |
| `notes` | string | ≤ 255 chars |
| `expiryStatus` | enum\|null | **read-only, server-computed** — `EXPIRED`, `APPROACHING`, or absent |

One product has many stock entries. That is the whole schema — there is no nesting beyond it.

### Enums

Fixed server-side; a value outside these lists is rejected with a `400`.

- `Category`: `WATER`, `PRESERVED_FOOD`, `DRY_GOODS`, `FREEZE_DRIED`, `MEDICINE`, `FUEL`,
  `STAPLES`, `OTHER`
- `Unit`: `LITERS`, `KG`, `CANS`, `PIECES`, `GRAMS`
- `ExpiryStatus`: `APPROACHING`, `EXPIRED`

Render enum values through your own localization layer — they are stable identifiers, not
display strings. The backend never returns human-readable labels.

## 5. Endpoints

| Method | Path | Role | Notes |
|---|---|---|---|
| `GET` | `/me` | user | Current user and roles |
| `GET` | `/products` | user | Optional `?category=`. Returns everything, unpaginated |
| `POST` | `/products` | admin | Returns `200` with the created product, not `201` |
| `GET` | `/products/{id}` | user | |
| `PUT` | `/products/{id}` | admin | Replaces all fields |
| `DELETE` | `/products/{id}` | admin | `204`. **Cascades to every stock entry** |
| `GET` | `/products/{id}/stock` | user | Oldest expiry first |
| `POST` | `/products/{id}/stock` | admin | Returns `200` |
| `PUT` | `/stock/{id}` | admin | Replaces all fields |
| `PATCH` | `/stock/{id}` | admin | Quantity only |
| `DELETE` | `/stock/{id}` | admin | `204` |
| `GET` | `/stock/expired` | user | |
| `GET` | `/stock/expiring?days=N` | user | `days` defaults to 30 |
| `GET` | `/stock/low` | user | Returns `Product`s, not stock entries |
| `GET` | `/q/health` | none | Also `/q/health/live`, `/q/health/ready` |

Successful creates return `200`, not `201`, and carry no `Location` header. Deletes return `204`
with an empty body.

`/q/health` needs no authentication, which makes it the right probe for a "can I reach the
server at all" check — it distinguishes a network or base-URL problem from a credentials
problem.

## 6. Write semantics

### PUT replaces every field

`PUT /products/{id}` and `PUT /stock/{id}` overwrite the whole record. **Any optional field you
omit is written as `null`.** Sending

```json
{"quantity": 6, "expiryDate": "2027-06-01"}
```

to a stock entry that had a `location`, `subType`, `purchasedDate`, and `notes` silently erases
all four. This is the most common way to corrupt data through this API.

Always read the current entity, apply your change to the full object, and send everything back.
For the common case of "I used some of this", use `PATCH` instead:

```json
PATCH /stock/{id}
{"quantity": 6}
```

`PATCH` accepts `quantity` and nothing else, and leaves every other field alone.

### No optimistic locking

There is no `version` field, no `ETag`, and no `If-Match` support. Concurrent writes are
last-write-wins with no conflict detection. If your app queues edits while offline and replays
them later, it will silently overwrite anything changed in the meantime. Either keep the app
online-only for writes, or reconcile in the client before replaying.

### Consuming stock

There is no "consume" operation. Draw stock down yourself: `PATCH` the entry to its new
quantity, and `DELETE` it once it reaches zero — an entry at quantity `0` is legal and will keep
appearing in expiry reports, so deleting it is usually what you want.

Entries are returned oldest-expiry-first precisely so you can implement first-expired-first-out:
consume from the head of `GET /products/{id}/stock`.

## 7. Server-side business rules

These are computed by the backend and are not configurable. **Do not reimplement them in the
client** — a second implementation will disagree with the server at the boundaries.

- **Low stock** (`GET /stock/low`) means `currentStock < targetQuantity / 4`, i.e. strictly below
  25% of target. Results are sorted by fill ratio, emptiest first. A product with
  `targetQuantity` of `0` is never low and never appears here, even with no stock at all.
- **`expiryStatus`** is `EXPIRED` once `expiryDate` is strictly before today, `APPROACHING` when
  it falls within the next 30 days inclusive, and absent otherwise. The 30-day threshold is a
  constant in `Category.java`, the same for every category.
- **`/stock/expired` and `/stock/expiring` never overlap.** An entry expiring *today* counts as
  expiring, not expired. Both are ordered oldest expiry first.
- An entry with a null `expiryDate` sorts last in `GET /products/{id}/stock` and appears in
  neither report. The API will not let you create one — `expiryDate` is required — so this only
  matters for rows predating that rule.
- Deleting a product deletes all of its stock entries.

### Timezone

All date logic uses the **server's** current date (`LocalDate.now()` in the JVM default zone).
No `TZ` is set in the Dockerfile or the Helm chart, so a deployed instance runs in the container
default, UTC; in local dev it uses your machine's zone.

A phone in a different timezone that computes "expires within 30 days" locally will disagree
with the server for a few hours each day at the boundary. Prefer the server's `expiryStatus` and
the `/stock/expired` and `/stock/expiring` endpoints over recomputing from `expiryDate` in the
client. If you must show a local countdown, accept that it can be off by one day.

### i18n keys

`expiryStatus` is designed to be combined with the product's category into a lookup key, e.g.
`action.WATER.APPROACHING` or `action.MEDICINE.EXPIRED` — the intent being category-specific
advice ("rotate your water", "check the dosage"). The backend ships no translations and no key
list; the client owns all of that copy. With 8 categories and 2 statuses there are 16 keys.

## 8. Validation

Constraints are declared in the OpenAPI spec and enforced by Bean Validation at runtime, so the
spec and the server agree by construction. Mirror them in your forms to fail fast:

| Field | Rule |
|---|---|
| `ProductRequest.name` | required, 1–255 chars |
| `ProductRequest.category` | required, valid enum |
| `ProductRequest.unit` | required, valid enum |
| `ProductRequest.targetQuantity` | required, ≥ 0 |
| `ProductRequest.notes` | ≤ 255 chars |
| `StockEntryRequest.quantity` | required, ≥ 0 |
| `StockEntryRequest.expiryDate` | required, `YYYY-MM-DD` |
| `StockEntryRequest.subType` / `location` / `notes` | ≤ 255 chars |
| `StockEntryPatch.quantity` | required, ≥ 0 |
| `days` query param | ≥ 0 |

The 255-character limits are not arbitrary: the underlying columns are `varchar(255)`. The
`notes` fields are genuinely short — design the UI for a one-line note, not a paragraph.

## 9. Error handling

| Status | Meaning | Body |
|---|---|---|
| `400` | Validation failure or unparseable value | Two shapes, see below |
| `401` | Missing or invalid credentials | Empty, plus `WWW-Authenticate: Basic` |
| `403` | Authenticated but lacking the role (a `user` attempting a write) | Empty |
| `404` | No such id, or no such parent product | Empty |

`401`, `403` and `404` have empty bodies. Do not try to parse them; map the status code to a
message yourself.

`400` comes back in **two different shapes**, distinguished by `Content-Type`:

**A constraint violation** — missing required field, negative number, string too long — returns
`application/json`:

```json
{
  "title": "Constraint Violation",
  "status": 400,
  "violations": [
    {"field": "createProduct.productRequest.name", "message": "must not be null"}
  ]
}
```

Note the `field` prefix: it is `<operationId>.<parameterName>.<field>`, not a bare field name.
**Strip everything up to the last dot** to map a violation onto a form field.

**An unparseable value** — an unknown category or unit, a malformed date — returns `text/plain`
with a raw message such as `No enum constant se.oskr.model.Category.NOT_A_CATEGORY`. This text
is an implementation detail, not user-facing copy: log it, but show the user something you
wrote. Since client-side enum pickers make this case nearly unreachable, treating it as a
generic "invalid request" is fine.

Check `Content-Type` before parsing a `400` as JSON, or you will throw a parse error while
handling an error.

## 10. What this API does not provide

Do not design screens around these. None of them exist:

- **Pagination, sorting, or filtering** beyond `GET /products?category=`. Everything returns in
  full. Sort and filter client-side.
- **Text search.** There is no search endpoint over products or stock. Fetch `GET /products` and
  filter in memory — fine at household scale.
- **A global stock list.** Stock entries are reachable only per-product, or through the three
  derived reports (`/stock/expired`, `/stock/expiring`, `/stock/low`). An "everything I own,
  sorted by location" view requires N+1 calls — one per product — so cache it.
- **Barcode lookup, product images, or any external catalog integration.**
- **Push notifications.** Expiry alerts must be driven by the client: poll `/stock/expiring` and
  schedule local notifications.
- **Bulk or batch operations.** One entity per request.
- **Change history, audit log, or soft delete.** Deletes are permanent and immediate.
- **Offline sync support.** No sync tokens, no `updatedAt` timestamps, no change feed. A client
  cannot ask "what changed since last time" — it can only refetch.

### Performance note

`GET /products` and `GET /stock/low` compute `currentStock` with a separate query per product
(`ProductService.currentStock`). It is an N+1, harmless at household scale but worth knowing
before you poll `/products` on a timer. Fetch on screen focus and pull-to-refresh instead.

## 11. CORS

The backend sets no CORS configuration at all — there is no `quarkus.http.cors` property
anywhere in the project.

This does not matter for a native app: **React Native, Flutter (native targets), native iOS and
native Android do not enforce CORS.** It breaks web-based stacks — Flutter web, Expo web,
Capacitor, Ionic, or any browser-hosted client — which will see requests fail preflight.

If you need CORS, add it to `app/src/main/resources/application.properties`:

```properties
quarkus.http.cors.enabled=true
quarkus.http.cors.origins=https://your-app-origin
quarkus.http.cors.methods=GET,POST,PUT,PATCH,DELETE
quarkus.http.cors.headers=authorization,content-type
```

Set an explicit origin. A wildcard combined with Basic Auth credentials is the combination
browsers refuse anyway.

## 12. Generating a client

The spec is a plain OpenAPI 3.0.3 document and generates cleanly:

```bash
# Dart / Flutter
openapi-generator generate -i rest/src/main/resources/openapi.yaml -g dart-dio -o ./api

# Kotlin
openapi-generator generate -i rest/src/main/resources/openapi.yaml -g kotlin -o ./api

# Swift
openapi-generator generate -i rest/src/main/resources/openapi.yaml -g swift5 -o ./api

# TypeScript (React Native)
openapi-generator generate -i rest/src/main/resources/openapi.yaml -g typescript-fetch -o ./api
```

Client generators group operations by **tag**, so you get `ProductsApi`, `StockApi` and `AuthApi`
classes. (The server-side `jaxrs-spec` generator used by this repo groups by path segment
instead, which is why the generated server interface for `/me` is `MeApi` — see
[CLAUDE.md](CLAUDE.md).)

Two things to fix up after generating:

1. `dateLibrary` — make sure `expiryDate` and `purchasedDate` map to a plain calendar date type,
   not a timestamp. They are `YYYY-MM-DD` with no time and no zone; round-tripping them through
   a `DateTime` will shift them across midnight.
2. `expiryStatus` is **nullable** — absent, not `null`, when the entry is neither expired nor
   approaching. Some generators emit a non-optional enum here; make it optional by hand if so.
