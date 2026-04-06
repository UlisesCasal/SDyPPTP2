# TP2 — Sistemas Distribuidos y Programación Paralela

## HITs Disponibles

| HIT | Descripción | Frontend | Backend |
|-----|-------------|----------|---------|
| **HIT1** | Tarea remota secuencial | `:3000` | `/api/hit1/getRemoteTask` |
| **HIT2** | Concurrencia + Lamport Clock + Worker Pool | `:3001` | `/api/hit2/getRemoteTask` |
| **HIT3** | Clúster con balanceador + elección de líder (Bully) | Cliente HTTP (`curl`/Postman) | `/api/hit3/getRemoteTask` |

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

#### Un solo cliente

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

# Levantar frontend HIT2 (1 cliente)
docker rm -f frontend-hit2 2>/dev/null || true
docker run -d \
  --name frontend-hit2 \
  -p 3001:3001 \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  --add-host=host.docker.internal:host-gateway \
  ulisescasal/frontend-hit2:latest
```

**Abrir:** http://localhost:3001

#### Múltiples clientes (5 simultáneos)

Para probar concurrencia real y ver cómo el reloj de Lamport ordena tareas de distintos clientes:

```bash
# Levantar orchestrator (si no está corriendo)
docker rm -f orchestrator-server 2>/dev/null || true
docker run -d \
  --name orchestrator-server \
  -p 8080:8080 \
  -e TASK_SERVICE_HOST=host.docker.internal \
  -e HIT2_WORKERS_MAX=2 \
  --add-host=host.docker.internal:host-gateway \
  -v /var/run/docker.sock:/var/run/docker.sock \
  ulisescasal/orchestrator-server:latest

# Levantar 5 frontends HIT2 en un solo contenedor
docker rm -f frontend-hit2 2>/dev/null || true
docker run -d \
  --name frontend-hit2 \
  -p 3011:3011 -p 3012:3012 -p 3013:3013 -p 3014:3014 -p 3015:3015 \
  -e MULTI_CLIENT=true \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  --add-host=host.docker.internal:host-gateway \
  ulisescasal/frontend-hit2:latest
```

**Abrir:** http://localhost:3011, http://localhost:3012, http://localhost:3013, http://localhost:3014, http://localhost:3015

Cada puerto es un cliente independiente con su propio reloj de Lamport. Abrí varias pestañas y enviá tareas simultáneas para observar:

- Cómo cada cliente incrementa su Lamport local
- Cómo el orchestrator ordena las tareas por timestamp en la `PriorityBlockingQueue`
- Cómo el reloj de cada cliente se sincroniza al recibir la respuesta del servidor (`max(local, server) + 1`)

> **Tip:** Usá `HIT2_WORKERS_MAX=2` con 5 clientes para forzar que las tareas se encolen y veas el ordenamiento por Lamport en acción.

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

## HIT3 — Clúster con Balanceador y Elección de Líder (Bully)

### Ejecución completa de HIT3 (balanceador + cluster + elección Bully)

Desde la raíz:

```bash
# 0) Descargar imagen del orquestador desde Docker Hub (opcional, recomendado)
docker pull ulisescasal/orchestrator-server:1.0.0

# 1) Levantar 3 nodos + nginx
docker compose -f docker-compose.hit3.yml up -d

# 2) Verificar que todos estén "healthy"
docker ps

# 3) Ver quién es el líder
curl http://localhost:8090/internal/hit3/cluster/status | python3 -m json.tool

# 4) Ejecutar tarea (va al balanceador, el líder la asigna)
curl -X POST http://localhost:8090/api/hit3/getRemoteTask \
  -H "Content-Type: application/json" \
  -d '{
    "calculo":"sumar",
    "parametros":{"a":10,"b":20},
    "datosAdicionales":{"traceId":"hit3-demo"},
    "imagenDocker":"ulisescasal/task-service:1.0.0"
  }'

# 5) Carga concurrente rápida
for i in {1..20}; do
  curl -s -X POST http://localhost:8090/api/hit3/getRemoteTask \
    -H "Content-Type: application/json" \
    -d "{\"calculo\":\"sumar\",\"parametros\":{\"a\":10,\"b\":20},\"datosAdicionales\":{\"traceId\":\"hit3-$i\"},\"imagenDocker\":\"ulisescasal/task-service:1.0.0\"}" &
done
wait

# 6) Failover: matar líder y medir recuperación
docker kill orchestrator-3
t0=$(date +%s%3N)
until curl -sf http://localhost:8090/internal/hit3/cluster/status | grep -q '"leaderId".*[12]'; do sleep 0.2; done
t1=$(date +%s%3N)
echo "Recovery time: $((t1 - t0)) ms"

# 7) Script de pruebas automatizadas
bash ./hit3_test_suite.sh --base-url http://localhost:8090 --image ulisescasal/task-service:1.0.0

# 8) Apagar cluster
docker compose -f docker-compose.hit3.yml down
```

### Guía completa HIT3

Ver [`HIT3_README.md`](HIT3_README.md) para arquitectura, troubleshooting, métricas de recuperación y diagrama de secuencia.

---

## Detener todo

```bash
docker rm -f orchestrator-server frontend-client frontend-hit2

# Si levantaste HIT3
docker compose -f docker-compose.hit3.yml down
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
