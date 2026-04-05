# HIT2 — Frontend + Orchestrator Server: Guía Completa de Ejecución

> Concurrencia, Exclusión Mutua, Reloj de Lamport y Worker Pool

---

## Arquitectura

```
┌─────────────────────────────────────────────────────────────────────┐
│                         HIT2 Frontend (:3001)                      │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  UI: Operación + Números + Lamport Timestamp + Status      │     │
│  │  Auto-increment Lamport · Refresh Status Panel             │     │
│  └────────────────────────┬───────────────────────────────────┘     │
│                           │ POST /api/hit2/getRemoteTask             │
│                           │ GET  /api/hit2/status                    │
│                           ▼                                          │
│                    ┌──────────────┐                                  │
│                    │  Express     │  ← Proxy (evita CORS)            │
│                    │  Proxy       │                                  │
│                    └──────┬───────┘                                  │
└───────────────────────────┼─────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Orchestrator Server (:8080)                      │
│                                                                      │
│  ┌────────────┐    ┌──────────────┐    ┌──────────────────────┐    │
│  │ Controller │───►│ Hit2TaskSvc  │───►│ WorkerPoolManager    │    │
│  │  /api/hit2 │    │ (Lamport)    │    │  PriorityBlockingQ   │    │
│  └────────────┘    └──────────────┘    │  + N Workers         │    │
│                                        │                      │    │
│                                        │  ┌────┐ ┌────┐      │    │
│                                        │  │ W1 │ │ W2 │ ...  │    │
│                                        │  └────┘ └────┘      │    │
│                                        └──────────┬───────────┘    │
│                                                   │                │
│                                        ┌──────────▼───────────┐    │
│                                        │  TaskExecutorImpl    │    │
│                                        │  docker pull/run/port│    │
│                                        │  POST /ejecutar      │    │
│                                        │  docker stop/rm      │    │
│                                        └──────────┬───────────┘    │
└───────────────────────────────────────────────────┼────────────────┘
                                                    │
                                                    ▼
                                    ┌───────────────────────────┐
                                    │  Task Service (container) │
                                    │  ulisescasal/task-service │
                                    │  Puerto dinámico (-P)     │
                                    └───────────────────────────┘
```

---

## Requisitos

- **Docker Desktop** o **Docker Daemon** corriendo
- **Docker CLI** disponible en PATH
- Puerto `8080` y `3001` disponibles

---

## Ejecución con Docker (Recomendado)

### 1. Traer las imágenes

```bash
docker pull ulisescasal/orchestrator-server:latest
docker pull ulisescasal/frontend-hit2:latest
```

> **Nota:** Si aún no construiste la imagen `frontend-hit2`, saltá a la sección [Construcción de Imágenes](#construcción-de-imágenes).

### 2. Levantar el Orchestrator Server (HIT2)

```bash
docker rm -f orchestrator-server 2>/dev/null || true
docker run -d \
  --name orchestrator-server \
  -p 8080:8080 \
  -e TASK_SERVICE_HOST=host.docker.internal \
  -e HIT2_WORKERS_MAX=4 \
  --add-host=host.docker.internal:host-gateway \
  -v /var/run/docker.sock:/var/run/docker.sock \
  ulisescasal/orchestrator-server:latest
```

**Variables de entorno:**

| Variable | Default | Descripción |
|----------|---------|-------------|
| `TASK_SERVICE_HOST` | `localhost` | Host donde corre el task service |
| `HIT2_WORKERS_MAX` | `4` | Cantidad de workers en el pool |

### 3. Levantar el Frontend HIT2

#### Un solo cliente

```bash
docker rm -f frontend-hit2 2>/dev/null || true
docker run -d \
  --name frontend-hit2 \
  -p 3001:3001 \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  --add-host=host.docker.internal:host-gateway \
  ulisescasal/frontend-hit2:latest
```

#### Múltiples clientes (5 simultáneos)

Para probar concurrencia real y ver cómo el reloj de Lamport ordena tareas de distintos clientes, podés levantar 5 instancias del frontend en el mismo contenedor:

```bash
docker rm -f frontend-hit2 2>/dev/null || true
docker run -d \
  --name frontend-hit2 \
  -p 3011:3011 -p 3012:3012 -p 3013:3013 -p 3014:3014 -p 3015:3015 \
  -e MULTI_CLIENT=true \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  --add-host=host.docker.internal:host-gateway \
  ulisescasal/frontend-hit2:latest
```

Esto levanta 5 frontends independientes en los puertos **3011, 3012, 3013, 3014 y 3015**. Cada uno tiene su propio reloj de Lamport, por lo que podés:

1. Abrir 5 pestañas del navegador (una por puerto)
2. Enviar tareas simultáneas desde cada cliente
3. Observar cómo el orchestrator las ordena por Lamport timestamp en la `PriorityBlockingQueue`
4. Ver en el log de cada cliente cómo su reloj se sincroniza con el del servidor

**Variables de entorno del frontend:**

| Variable | Default | Descripción |
|----------|---------|-------------|
| `BACKEND_URL` | `http://host.docker.internal:8080` | URL del orchestrator |
| `PORT` | `3001` | Puerto del frontend |
| `MULTI_CLIENT` | `false` | Si es `true`, levanta 5 instancias (3011-3015) |

### 4. Abrir el navegador

**http://localhost:3001**

### 5. Detener todo

```bash
docker rm -f orchestrator-server frontend-hit2
```

---

## Ejecución Local (Sin Docker)

### 1. Orchestrator Server

```bash
cd sdypp
./mvnw spring-boot:run
```

O con variable de entorno para workers:

```bash
HIT2_WORKERS_MAX=4 ./mvnw spring-boot:run
```

### 2. Frontend HIT2

```bash
cd frontend/HIT2
npm install
npm start
```

O en modo desarrollo con hot-reload:

```bash
npm run dev
```

### 3. Abrir el navegador

**http://localhost:3001**

---

## Construcción de Imágenes

### Frontend HIT2

```bash
cd frontend/HIT2
docker build -t ulisescasal/frontend-hit2:latest .
```

### Orchestrator Server

```bash
cd sdypp
docker build -t ulisescasal/orchestrator-server:latest .
```

---

## Uso del Frontend HIT2

### Panel de Status

En la parte superior verás:

| Métrica | Descripción |
|---------|-------------|
| **Tareas en Cola** | Cantidad de tareas esperando ser procesadas (cambia de color según carga) |
| **Max Workers** | Workers configurados en el pool |
| **Lamport (cliente)** | Timestamp lógico actual del cliente |
| **⟳ Refresh Status** | Botón para refrescar manualmente el estado |

### Formulario de Tarea

| Campo | Descripción |
|-------|-------------|
| **Operación** | `sumar` o `multiplicar` |
| **Número A / B** | Operandos de la operación |
| **Imagen Docker** | Imagen del task service a usar (default: `ulisescasal/task-service:1.0.0`) |
| **Auto-incrementar Lamport** | Toggle que incrementa automáticamente el timestamp con cada envío |
| **Lamport Timestamp** | Valor del timestamp lógico (se auto-incrementa si el toggle está activo) |

### Resultado

Después de ejecutar una tarea se muestra:

```
✅ Tarea Completada

  Status:        OK
  Resultado:     30
  Mensaje:       Tarea ejecutada correctamente
  Container ID:  abc123def456
  Duración:      5014ms
  Lamport (srv): 15
  Posición Cola: 1
```

---

## Endpoints del Backend HIT2

### POST /api/hit2/getRemoteTask

Encola una tarea en el worker pool y espera el resultado.

**Request:**

```json
{
  "calculo": "sumar",
  "parametros": { "a": 10, "b": 20 },
  "datosAdicionales": { "traceId": "hit2-001" },
  "imagenDocker": "ulisescasal/task-service:1.0.0",
  "lamportTimestamp": 1
}
```

**Response:**

```json
{
  "status": "OK",
  "resultado": 30,
  "mensaje": "Tarea ejecutada correctamente",
  "containerId": "abc123def456",
  "duracionMs": 5014,
  "lamportTimestamp": 15,
  "posicionEnCola": 1
}
```

### GET /api/hit2/status

Devuelve el estado actual del worker pool.

**Response:**

```json
{
  "tareasEnCola": 0,
  "maxWorkers": 4
}
```

---

## Probar con curl

### Enviar una tarea HIT2

```bash
curl -X POST http://localhost:8080/api/hit2/getRemoteTask \
  -H "Content-Type: application/json" \
  -d '{
    "calculo":"sumar",
    "parametros":{"a":10,"b":20},
    "datosAdicionales":{"traceId":"hit2-curl-001"},
    "imagenDocker":"ulisescasal/task-service:1.0.0",
    "lamportTimestamp":1
  }'
```

### Ver status del pool

```bash
curl http://localhost:8080/api/hit2/status
```

---

## Cómo funciona el Reloj de Lamport

El **Reloj de Lamport** es un mecanismo de ordenamiento lógico que no depende de relojes físicos sincronizados.

### Reglas

1. **Evento local:** `clock++`
2. **Al enviar mensaje:** adjuntás tu `clock` actual
3. **Al recibir mensaje:** `clock = max(clock_local, clock_recibido) + 1`

### En este sistema

```
Cliente (lamportTimestamp=5)
        │
        ▼ POST /api/hit2/getRemoteTask
Orchestrator recibe → lamportClock.receive(5)
        │
        ▼ Encola en PriorityBlockingQueue
        │  (ordenado por menor timestamp = mayor prioridad)
        │
        ▼ Worker toma la tarea
        │
        ▼ Ejecuta docker pull → run → invoke → cleanup
        │
        ▼ lamportClock.tick() → timestamp de respuesta
        │
        ▼ Response: { lamportTimestamp: 15, posicionEnCola: 1 }
```

### Auto-incremento en el frontend

El frontend tiene un toggle **"Auto-incrementar Lamport Timestamp"** que:
- Empieza en `1`
- Se incrementa con cada tarea enviada
- Permite ver cómo el ordenamiento por Lamport afecta el procesamiento

---

## Worker Pool y Exclusión Mutua

### Componentes

| Componente | Rol |
|------------|-----|
| `PriorityBlockingQueue` | Cola thread-safe con prioridad por Lamport timestamp |
| `WorkerPoolManager` | Gestiona N workers que consumen de la cola |
| `TaskExecutorImpl` | Ejecuta una tarea: pull → run → invoke → cleanup |
| `LamportClock` | Reloj lógico thread-safe (AtomicLong) |

### Exclusión Mutua

`PriorityBlockingQueue` usa un `ReentrantLock` interno. Cada operación `put()` y `take()` adquiere el lock automáticamente. Esto garantiza que **no hay condición de carrera** al encolar/desencolar.

### Flujo de una tarea

```
1. Controller recibe POST → Hit2TaskService
2. Hit2TaskService sincroniza reloj: lamportClock.receive(clientTs)
3. Encola en PriorityBlockingQueue (ordenada por Lamport)
4. Controller espera con future.get()
5. Worker libre hace take() de la cola
6. TaskExecutor ejecuta: pull → run → port → invoke → cleanup
7. Worker completa el future → Controller devuelve response
```

---

## Benchmark de Throughput

El script `benchmark.sh` en la raíz del proyecto `sdypp` mide throughput variando workers.

```bash
cd sdypp
chmod +x benchmark.sh
./benchmark.sh 16
```

Esto prueba con 1, 2, 4 y 8 workers y genera un CSV con los resultados.

---

## Troubleshooting

| Problema | Solución |
|----------|----------|
| `pull access denied` | Ejecutá `docker login` |
| Puerto 8080 ocupado | `docker ps` y liberar el contenedor |
| Puerto 3001 ocupado | `docker rm -f frontend-hit2` o cambiar `PORT` |
| `Failed to fetch` | Verificá que el orchestrator esté corriendo (`docker ps`) |
| Contenedores no se crean | Verificá que Docker Desktop/Daemon esté corriendo |
| Error de Docker socket | El volumen `/var/run/docker.sock` debe estar montado |

### Ver logs

```bash
# Orchestrator
docker logs -f orchestrator-server

# Frontend HIT2
docker logs -f frontend-hit2
```

### Ver contenedores activos

```bash
docker ps
```

---

## Estructura de Archivos

```
frontend/HIT2/
├── Dockerfile          # Imagen Docker del frontend
├── index.html          # UI completa con status panel y formulario
├── package.json        # Dependencias (express, nodemon)
└── server.js           # Express server con proxy a /api/hit2/*
```

---

## Comparación HIT1 vs HIT2

| Aspecto | HIT1 | HIT2 |
|---------|------|------|
| **Frontend puerto** | 3000 | 3001 |
| **Endpoint** | `/api/hit1/getRemoteTask` | `/api/hit2/getRemoteTask` |
| **Ejecución** | Secuencial (un worker) | Pool de N workers concurrentes |
| **Ordenamiento** | FIFO | Priority Queue por Lamport Timestamp |
| **Timestamp** | No tiene | Lamport Clock (lógico, thread-safe) |
| **Status** | No tiene | `GET /api/hit2/status` |
| **Exclusión mutua** | N/A | `PriorityBlockingQueue` (ReentrantLock) |
| **Configurable** | No | `HIT2_WORKERS_MAX` (env var) |
