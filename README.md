# TP2 - HIT #1 | Sistemas Distribuidos y Programación Paralela

## Cómo ejecutar

### Requisitos

- Docker Desktop o Daemon  corriendo

### 1. Traer las imágenes

```bash
docker pull ulisescasal/orchestrator-server:1.0.0
docker pull ulisescasal/frontend-client:1.0.0
```

### 2. Levantar el Orquestador

```bash
docker rm -f orchestrator-server 2>/dev/null || true
docker run -d \
  --name orchestrator-server \
  -p 8080:8080 \
  -e TASK_SERVICE_HOST=host.docker.internal \
  --add-host=host.docker.internal:host-gateway \
  -v /var/run/docker.sock:/var/run/docker.sock \
  ulisescasal/orchestrator-server:1.0.0
```

### 3. Levantar el Frontend

```bash
docker rm -f frontend-client 2>/dev/null || true
docker run -d \
  --name frontend-client \
  -p 3000:3000 \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  --add-host=host.docker.internal:host-gateway \
  ulisescasal/frontend-client:1.0.0
```

### 4. Abrir el navegador

**http://localhost:3000**

---

## Probar con curl

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

**Respuesta esperada:**

```json
{
  "status": "OK",
  "resultado": 30,
  "mensaje": "Tarea ejecutada correctamente",
  "containerId": "...",
  "duracionMs": 5014
}
```

---

## Detener todo

```bash
docker rm -f orchestrator-server frontend-client
```

---

## Arquitectura

```
┌─────────────┐     POST /api/hit1/getRemoteTask     ┌─────────────────┐
│  Frontend   │ ────────────────────────────────────► │  Orquestador    │
│  (:3000)    │                                       │  (:8080)        │
│             │ ◄──────────────────────────────────── │                 │
└─────────────┘        JSON con resultado             └────────┬────────┘
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

1. El usuario ingresa operación y números en el **frontend**
2. El frontend envía el request al **orquestador** (Spring Boot)
3. El orquestador hace `docker pull` + `docker run -d -P` del **task service**
4. Obtiene el puerto mapeado con `docker port`
5. Invoca `POST /ejecutar` en el task service
6. Devuelve el resultado y limpia el contenedor

---

## Comandos útiles

```bash
# Ver contenedores corriendo
docker ps

# Ver logs
docker logs -f orchestrator-server
docker logs -f frontend-client
```

## Troubleshooting

| Problema | Solución |
|----------|----------|
| `pull access denied` | `docker login` |
| Nombre en uso | `docker rm -f orchestrator-server frontend-client` |
| Puerto ocupado | `docker ps` y liberar el contenedor |
| `Failed to fetch` | Verificá que el orquestador esté corriendo (`docker ps`) |
