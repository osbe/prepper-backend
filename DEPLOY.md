# Deploying prepper-backend to Kubernetes

Deploys two Helm releases into the `prepper` namespace:

- **prepper-postgres** — Bitnami PostgreSQL chart
- **prepper-backend** — custom chart in `app/helm/`

## Prerequisites

- [Helm](https://helm.sh/docs/intro/install/) ≥ 3
- [Helmfile](https://helmfile.readthedocs.io/en/latest/#installation)
- A local cluster: [minikube](https://minikube.sigs.k8s.io/docs/start/) or [kind](https://kind.sigs.k8s.io/docs/user/quick-start/)

## 1 — Build the JAR

Run from the repo root:

```bash
./mvnw package -pl app -am -DskipTests
```

Output lands in `app/target/quarkus-app/`.

## 2 — Build the Docker image

The build context is the `app/` directory (the Dockerfile copies from `target/quarkus-app/`).

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

> For kind, also change `pullPolicy` in `deploy/values/backend.yaml` from `Never` to `IfNotPresent`.

## 3 — Deploy

```bash
helmfile sync
```

This adds the Bitnami Helm repo if missing, installs PostgreSQL first, then the backend once Postgres is ready.

Check pod status:

```bash
kubectl get pods -n prepper
```

Both pods should reach `Running` within a minute or two.

## 4 — Verify

Forward the service port locally:

```bash
kubectl port-forward -n prepper svc/prepper-backend 8080:8080
```

Then:

```bash
# Health endpoint (no auth required)
curl http://localhost:8080/q/health

# API (HTTP Basic Auth)
curl -u admin:admin http://localhost:8080/products
```

## 5 — Teardown

```bash
helmfile destroy
```

This removes both releases but leaves the `prepper` namespace and any PersistentVolumeClaims. To clean up completely:

```bash
kubectl delete namespace prepper
```

---

## Configuration reference

| Variable | Default | Description |
|---|---|---|
| `DB_USER` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `DB_URL` | `jdbc:postgresql://localhost:5432/prepper` | JDBC connection URL |
| `DB_SCHEMA_GENERATION` | `update` | Hibernate schema strategy in prod (`update` / `validate` / `none`) |

These are injected automatically from the Helm Secret and ConfigMap. To change them, edit `deploy/values/backend.yaml` (credentials) or `deploy/values/postgres.yaml` (database setup) and re-run `helmfile sync`.

## Helm chart structure

```
app/helm/
  Chart.yaml
  values.yaml                  ← defaults (fullnameOverride: prepper-backend)
  templates/
    deployment.yaml            ← liveness/readiness probes on /q/health/live and /q/health/ready
    service.yaml               ← ClusterIP on port 8080
    secret.yaml                ← DB_USER / DB_PASSWORD (rendered when db.createSecret: true)
    configmap.yaml             ← DB_URL, QUARKUS_HTTP_AUTH_BASIC
deploy/
  values/
    postgres.yaml              ← Bitnami PostgreSQL overrides
    backend.yaml               ← local cluster image + DB connection overrides
helmfile.yaml
```
