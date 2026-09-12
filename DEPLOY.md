# Deploying prepper-backend

There are three ways to deploy:

- **From source** — build the image locally and install the chart bundled in this repo with plain Helm. Intended for local development against a throwaway cluster.
- **Published chart** — install the pre-built chart from `oci://ghcr.io/osbe/charts`. Intended for any real cluster.
- **Full stack** — the `prepper-infra` repo deploys backend, frontend, and PostgreSQL together with Helmfile. That is the supported path for the production cluster; the two options below deploy the backend alone.

---

## From source (local development)

### Prerequisites

- [Helm](https://helm.sh/docs/intro/install/) ≥ 3
- A local cluster: [minikube](https://minikube.sigs.k8s.io/docs/start/) or [kind](https://kind.sigs.k8s.io/docs/user/quick-start/)

### 1 — Build the JAR

```bash
./mvnw package -pl app -am -DskipTests
```

Output lands in `app/target/quarkus-app/`.

### 2 — Build the Docker image

**minikube** — build directly inside minikube's Docker daemon so the image is available without pushing to a registry:

```bash
eval $(minikube docker-env)
docker build -f app/src/main/docker/Dockerfile.jvm -t prepper-backend:latest app/
```

**kind** — build locally then load into the cluster:

```bash
docker build -f app/src/main/docker/Dockerfile.jvm -t prepper-backend:latest app/
kind load docker-image prepper-backend:latest
```

### 3 — Install PostgreSQL

The chart does not bundle a database. Install one into the same namespace:

```bash
helm install prepper-postgres oci://registry-1.docker.io/bitnamicharts/postgresql \
  --namespace prepper --create-namespace \
  --set auth.username=prepper \
  --set auth.password=prepper \
  --set auth.database=prepper
```

> The release name matters: `db.host` defaults to `prepper-postgres-postgresql`, which is the Service this release creates. Use a different release name and you must set `db.host` to match in the next step.

Wait for it to be ready before continuing:

```bash
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=postgresql -n prepper --timeout=120s
```

### 4 — Deploy the backend

```bash
helm install prepper-backend ./app/helm \
  --namespace prepper \
  --set image.repository=prepper-backend \
  --set image.tag=latest \
  --set image.pullPolicy=Never \
  --set db.user=prepper \
  --set db.password=prepper \
  --set auth.adminPassword=admin \
  --set auth.userPassword=user
```

`image.pullPolicy=Never` keeps Kubernetes from trying to pull the locally built image from a registry.

Check pod status:

```bash
kubectl get pods -n prepper
```

Both pods should reach `Running` within a minute or two.

### 5 — Verify

Forward the service port locally:

```bash
kubectl port-forward -n prepper svc/prepper-backend 8080:8080
```

Then:

```bash
# Health endpoint (no auth required)
curl http://localhost:8080/q/health

# API (HTTP Basic Auth)
curl -u <username>:<password> http://localhost:8080/products
```

### 6 — Teardown

```bash
helm uninstall prepper-backend -n prepper
helm uninstall prepper-postgres -n prepper
```

This removes both releases but leaves the `prepper` namespace and any PersistentVolumeClaims. To clean up completely:

```bash
kubectl delete namespace prepper
```

---

## Published chart

Charts are published to `oci://ghcr.io/osbe/charts` on every release. You need [Helm](https://helm.sh/docs/intro/install/) ≥ 3 and a running PostgreSQL instance, or you can deploy the Bitnami PostgreSQL chart alongside it.

### Vanilla Helm

```bash
helm install prepper-backend oci://ghcr.io/osbe/charts/prepper-backend \
  --namespace <namespace> \
  --set db.host=<postgres-host> \
  --set db.password=<db-password> \
  --set auth.adminPassword=<admin-password> \
  --set auth.userPassword=<user-password>
```

### FluxCD — using `valuesFrom`

If managing the release via a FluxCD `HelmRelease` with `valuesFrom`, create the following secrets in the target namespace before reconciling.

**`prepper-postgres-credentials`**
```bash
kubectl create secret generic prepper-postgres-credentials \
  -n <namespace> \
  --from-literal=password=<db-password> \
  --from-literal=postgresPassword=<postgres-superuser-password>
```

**`prepper-backend-credentials`**
```bash
kubectl create secret generic prepper-backend-credentials \
  -n <namespace> \
  --from-literal=dbPassword=<db-password> \
  --from-literal=adminPassword=<admin-password> \
  --from-literal=userPassword=<user-password>
```

> `dbPassword` must match `password` in `prepper-postgres-credentials`.

---

## Full stack (Helmfile)

The `prepper-infra` repo deploys the whole Prepper stack — backend, frontend, and PostgreSQL — pinning each to a released chart version. It consumes the published chart above; nothing in this repo is referenced directly. See that repo's README for setup, and bump the `backend:` version in its `helmfile.yaml` to roll out a new backend release.

---

## Configuration reference

| Helm value | Default | Description |
|---|---|---|
| `image.repository` | `ghcr.io/osbe/prepper-backend` | Image to deploy |
| `image.tag` | `""` (falls back to chart `appVersion`) | Image tag |
| `image.pullPolicy` | `IfNotPresent` | Set to `Never` for locally built images |
| `replicaCount` | `1` | Number of pods |
| `db.host` | `prepper-postgres-postgresql` | PostgreSQL hostname |
| `db.port` | `5432` | PostgreSQL port |
| `db.user` | `CHANGEME` | Database username |
| `db.password` | `CHANGEME` | Database password |
| `db.name` | `prepper` | Database name |
| `db.createSecret` | `true` | Render the DB credentials Secret from these values |
| `db.schemaGeneration` | `update` | Hibernate schema mode (`DB_SCHEMA_GENERATION`) — `update`, `validate`, `none`, `drop-and-create` |
| `auth.adminPassword` | `CHANGEME` | Password for the seeded admin user |
| `auth.userPassword` | `CHANGEME` | Password for the seeded regular user |
| `auth.createSecret` | `true` | Render the auth Secret from these values |
| `cors.origins` | `http://localhost:3000` | Comma-separated browser origins allowed to call the API. Only browser-based clients need this; set it to your frontend's real origin. Never leave it empty — Quarkus allows every origin when none are configured |
| `service.type` / `service.port` | `ClusterIP` / `8080` | Service exposure |
| `resources` | 100m/256Mi → 500m/512Mi | Container requests and limits |
| `fullnameOverride` | `prepper-backend` | Name for the rendered resources, and the prefix of the Secret and ConfigMap names |
| `livenessProbe.initialDelaySeconds` / `periodSeconds` | `30` / `10` | Liveness probe timing on `/q/health/live` |
| `readinessProbe.initialDelaySeconds` / `periodSeconds` | `15` / `5` | Readiness probe timing on `/q/health/ready` |

Seeded user passwords are re-applied on every pod start — the app deletes and recreates both users at boot (`Startup.java`), so changing `auth.*` and upgrading the release is enough to rotate them.

With `db.createSecret: false` or `auth.createSecret: false`, the chart skips rendering that Secret but the Deployment still mounts it by name — you must create `prepper-backend-db-secret` (keys `DB_USER`, `DB_PASSWORD`) or `prepper-backend-auth-secret` (keys `APP_ADMIN_PASSWORD`, `APP_USER_PASSWORD`) yourself.

## Chart structure

```
app/helm/
  Chart.yaml
  values.yaml                  ← defaults
  templates/
    _helpers.tpl               ← name and label helpers
    deployment.yaml            ← liveness/readiness probes on /q/health/live and /q/health/ready
    service.yaml               ← ClusterIP on port 8080
    secret.yaml                ← DB credentials (rendered when db.createSecret: true)
    auth-secret.yaml           ← seeded user passwords (rendered when auth.createSecret: true)
    configmap.yaml             ← DB_URL, DB_SCHEMA_GENERATION, QUARKUS_HTTP_AUTH_BASIC
```
