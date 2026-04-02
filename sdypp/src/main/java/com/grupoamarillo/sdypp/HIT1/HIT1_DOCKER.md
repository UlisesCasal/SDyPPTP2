# HIT #1 - Orquestador + Task Service + Docker + CI/CD

## 1) Resumen de la solución

El HIT #1 queda implementado con tres piezas:

- Cliente HTTP que envía el request JSON al orquestador.
- Servidor orquestador (Spring Boot), que recibe `POST /api/hit1/getRemoteTask`.
- Servicio tarea (Node.js), que se ejecuta en contenedor y expone `POST /ejecutar`.

Flujo:

1. El cliente envía `calculo`, `parametros`, `datosAdicionales`, `imagenDocker`.
2. El orquestador valida datos e imagen.
3. El orquestador hace `docker pull` y `docker run -d -P` del task service.
4. Obtiene el puerto mapeado con `docker port`.
5. Invoca `POST /ejecutar`.
6. Devuelve resultado al cliente y limpia contenedor (`stop` + `rm`).

## 2) Estructura importante

- `HIT1/controllers/Controller.java`: endpoint HTTP del orquestador.
- `HIT1/services/RemoteTaskService.java`: contrato del caso de uso.
- `HIT1/services/RemoteTaskServiceImpl.java`: orquestación Docker + llamada HTTP al task service.
- `HIT1/docker/DockerCommandRunner.java`: ejecución de comandos Docker CLI.
- `HIT1/docker/CommandResult.java`: estructura de resultado de comandos.
- `HIT1/dtos/*.java`: contratos de request/response.
- `.github/workflows/orchestrator-dockerhub.yml`: pipeline CI/CD del orquestador.

## 3) Prerrequisitos

- Docker Desktop/Engine corriendo.
- Java 17 para desarrollo local de Spring.
- Imagen del task service publicada en Docker Hub, por ejemplo:
  - `ulisescasal/task-service:1.0.0`
- Para imágenes privadas: sesión iniciada con Docker Hub:

```bash
docker login
```

## 4) Levantar el orquestador en Docker

Ubicarse en el proyecto:

```bash
cd "/Users/ulisescasal/Documents/Universidad/Sistemas Distribuidos y Prog Paralela/TP2/sdypp"
```

Construir imagen:

```bash
docker build -t ulisescasal/orchestrator-server:1.0.0 .
```

Levantar contenedor:

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

## 5) Prueba end-to-end

Probar endpoint del orquestador:

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

Resultado esperado:

- HTTP 200
- `status: "OK"`
- `resultado: 30`
- `containerId` y `duracionMs` informados

## 6) CI/CD del orquestador

Archivo:

- `.github/workflows/orchestrator-dockerhub.yml`

Comportamiento:

- En `pull_request` a `main`: ejecuta tests (`./mvnw -B clean test`).
- En `push` a `main`: ejecuta tests y luego build/push de imagen a Docker Hub.
- Tags publicadas:
  - `latest`
  - `sha-<commit>`

Secrets obligatorios en GitHub:

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`

## 7) Bloquear commits directos a main

El bloqueo real de commits directos en `main` se configura en GitHub con reglas de rama.

Configurar:

1. Ir a `Settings` del repositorio.
2. Ir a `Branches`.
3. En `Branch protection rules`, crear regla para `main`.
4. Activar:
   - `Require a pull request before merging`
   - `Require status checks to pass before merging`
   - Seleccionar check del workflow (`test`)
   - `Restrict who can push to matching branches` (opcional recomendado)
5. Guardar la regla.

Con eso:

- Nadie puede pushear directo a `main`.
- Solo se integra por PR con checks en verde.
- El push de imagen queda acoplado al merge a `main`.

## 8) Comandos de soporte

Ver estado de contenedores:

```bash
docker ps
```

Ver logs del orquestador:

```bash
docker logs -f orchestrator-server
```

Eliminar contenedor del orquestador:

```bash
docker rm -f orchestrator-server
```

## 9) Troubleshooting

Error de nombre en uso:

```bash
docker rm -f orchestrator-server
```

Error de puerto ocupado (`8080`):

- Ver qué ocupa el puerto con `docker ps`.
- Liberar ese contenedor o usar otro puerto host.

Error `pull access denied`:

- Verificar nombre/tag exacto de imagen.
- Verificar login (`docker login`) para repos privados.
