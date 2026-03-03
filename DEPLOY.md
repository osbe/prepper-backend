# Deploying prepper-backend

There are two ways to deploy:

- **From source** — build the image locally and deploy using the chart bundled in this repo. Intended for local development.
- **Published chart** — use the pre-built chart published to `oci://ghcr.io/osbe/charts`. Intended for any real cluster.

---

## From source (local development)

### Prerequisites

- [Helm](https://helm.sh/docs/intro/install/) ≥ 3
- [Helmfile](https://helmfile.readthedocs.io/en/latest/#installation)
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


### 3 — Deploy

```bash
helmfile sync
```

This installs PostgreSQL first, then the backend once Postgres is ready. Both land in the `prepper` namespace.

Check pod status:

```bash
kubectl get pods -n prepper
```

Both pods should reach `Running` within a minute or two.

### 4 — Verify

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

### 5 — Teardown

```bash
helmfile destroy
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

## Configuration reference

| Helm value | Description |
|---|---|
| `db.host` | PostgreSQL hostname |
| `db.user` | Database username |
| `db.password` | Database password |
| `db.name` | Database name |
| `auth.adminPassword` | Password for the seeded admin user |
| `auth.userPassword` | Password for the seeded regular user |

The chart creates a Kubernetes Secret from these values when `db.createSecret: true` and `auth.createSecret: true` (both default to `true`).

## Chart structure

```
app/helm/
  Chart.yaml
  values.yaml                  ← defaults
  templates/
    deployment.yaml            ← liveness/readiness probes on /q/health/live and /q/health/ready
    service.yaml               ← ClusterIP on port 8080
    secret.yaml                ← DB credentials (rendered when db.createSecret: true)
    configmap.yaml             ← DB_URL, QUARKUS_HTTP_AUTH_BASIC
```
