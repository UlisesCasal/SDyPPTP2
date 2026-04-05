# TP2 — Sistemas Distribuidos y Programación Paralela

## HITs Disponibles

| HIT | Descripción | Frontend | Backend |
|-----|-------------|----------|---------|
| **HIT1** | Tarea remota secuencial | `:3000` | `/api/hit1/getRemoteTask` |
| **HIT2** | Concurrencia + Lamport Clock + Worker Pool | `:3001` | `/api/hit2/getRemoteTask` |

---

## HIT1 — Tarea Remota Secuencial

### Ejecución con Docker

```bash
docker pull ulisescasal/orchestrator-server:1.0.0
docker pull ulisescasal/frontend-client:1.0.0

docker rm -f orchestrator-server 2>/dev/null || true
docker run -d \
  --name orchestrator-server \
  -p 8080:8080 \
  -e TASK_SERVICE_HOST=host.docker.internal \
  --add-host=host.docker.internal:host-gateway \
  -v /var/run/docker.sock:/var/run/docker.sock \
  ulisescasal/orchestrator-server:1.0.0

docker rm -f frontend-client 2>/dev/null || true
docker run -d \
  --name frontend-client \
  -p 3000:3000 \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  --add-host=host.docker.internal:host-gateway \
  ulisescasal/frontend-client:1.0.0
```

**Abrir:** http://localhost:3000

### Probar con curl

```bash
curl -X POST http://localhost:8080/api/hit1/getRemoteTask \
  -H "Content-Type: application/json" \
  -d '{
    "calculo":"sumar",
    "parametros":{"a":10,"b":20},
    "datosAdicionales":{"traceId":"tp2-001"},
    "imagenDocker":"ulisescasal/task-service:1.0.0"
  }'
```

---

## HIT2 — Concurrencia, Exclusión Mutua y Reloj de Lamport

### Ejecución con Docker

```bash
# Construir imágenes si no existen
docker build -t ulisescasal/orchestrator-server:latest ./sdypp
docker build -t ulisescasal/frontend-hit2:latest ./frontend/HIT2

# Levantar orchestrator
docker rm -f orchestrator-server 2>/dev/null || true
docker run -d \
  --name orchestrator-server \
  -p 8080:8080 \
  -e TASK_SERVICE_HOST=host.docker.internal \
  -e HIT2_WORKERS_MAX=4 \
  --add-host=host.docker.internal:host-gateway \
  -v /var/run/docker.sock:/var/run/docker.sock \
  ulisescasal/orchestrator-server:latest

# Levantar frontend HIT2
docker rm -f frontend-hit2 2>/dev/null || true
docker run -d \
  --name frontend-hit2 \
  -p 3001:3001 \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  --add-host=host.docker.internal:host-gateway \
  ulisescasal/frontend-hit2:latest
```

**Abrir:** http://localhost:3001

### Probar con curl

```bash
# Enviar tarea HIT2
curl -X POST http://localhost:8080/api/hit2/getRemoteTask \
  -H "Content-Type: application/json" \
  -d '{
    "calculo":"sumar",
    "parametros":{"a":10,"b":20},
    "datosAdicionales":{"traceId":"hit2-001"},
    "imagenDocker":"ulisescasal/task-service:1.0.0",
    "lamportTimestamp":1
  }'

# Ver status del worker pool
curl http://localhost:8080/api/hit2/status
```

### Ejecución local (sin Docker)

```bash
# Terminal 1: Orchestrator
cd sdypp
HIT2_WORKERS_MAX=4 ./mvnw spring-boot:run

# Terminal 2: Frontend HIT2
cd frontend/HIT2
npm install
npm start
```

### Documentación completa

Ver [`frontend/HIT2/README.md`](frontend/HIT2/README.md) para la guía detallada de HIT2.

---

## Detener todo

```bash
docker rm -f orchestrator-server frontend-client frontend-hit2
```

---

## Arquitectura HIT2

```
┌─────────────┐     POST /api/hit2/getRemoteTask     ┌─────────────────┐
│  Frontend   │ ────────────────────────────────────► │  Orquestador    │
│  HIT2       │                                       │  (:8080)        │
│  (:3001)    │ ◄──────────────────────────────────── │                 │
│             │   JSON + lamportTimestamp             │  LamportClock   │
└─────────────┘                                       │  WorkerPool(N)  │
                                                      │  PriorityQ      │
                                                      └────────┬────────┘
                                                               │
                                               ┌───────────────┼───────────────┐
                                               │ docker pull   │ docker run -d -P
                                               │               │ docker port
                                               │               │ POST /ejecutar
                                               │               │ docker stop + rm
                                               ▼               ▼
                                        ┌─────────────────────────┐
                                        │   Task Service (Docker) │
                                        │   (Puerto dinámico)     │
                                        └─────────────────────────┘
```

---

## Comandos útiles

```bash
# Ver contenedores corriendo
docker ps

# Ver logs
docker logs -f orchestrator-server
docker logs -f frontend-client
docker logs -f frontend-hit2
```

## Troubleshooting

| Problema | Solución |
|----------|----------|
| `pull access denied` | `docker login` |
| Nombre en uso | `docker rm -f orchestrator-server frontend-client frontend-hit2` |
| Puerto ocupado | `docker ps` y liberar el contenedor |
| `Failed to fetch` | Verificá que el orquestador esté corriendo (`docker ps`) |
| Contenedores no se crean | Verificá que Docker Desktop/Daemon esté corriendo |
