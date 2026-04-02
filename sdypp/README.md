# Cómo ejecutar

## Requisitos

- Docker corriendo.
- Imagen del task service publicada:
  - `ulisescasal/task-service:1.0.0`

## Levantar el orquestador desde Docker Hub

```bash
docker rm -f orchestrator-server 2>/dev/null || true
docker run -d \
  --name orchestrator-server \
  -p 8080:8080 \
  -e TASK_SERVICE_HOST=host.docker.internal \
  --add-host=host.docker.internal:host-gateway \
  -v /var/run/docker.sock:/var/run/docker.sock \
  ulisescasal/orchestrator-server:latest
```

## Probar

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

## Logs

```bash
docker logs -f orchestrator-server
```
