# Cliente Frontend - Consulta al Servidor

Cliente web para consumir el servicio de tareas remotas del backend.

## Requisitos

- Docker instalado y corriendo
- Backend corriendo en el puerto 8080

## Cómo correr el cliente

### 1. Verificar que el backend esté corriendo

```bash
curl -sS -X POST http://localhost:8080/api/hit1/getRemoteTask \
  -H "Content-Type: application/json" \
  -d '{"calculo":"sumar","parametros":{"a":10,"b":20},"datosAdicionales":{"traceId":"tp2-001"},"imagenDocker":"ulisescasal/task-service:1.0.0"}'
```

Deberías ver una respuesta como:
```json
{"status":"OK","resultado":30,"mensaje":"Tarea ejecutada correctamente","containerId":"...","duracionMs":5014}
```

### 2. Construir la imagen Docker

```bash
docker build -t frontend-client:latest ./frontend
```

### 3. Correr el contenedor

```bash
docker run -d --network host -p 3000:3000 --name frontend-client frontend-client:latest
```

> **Nota:** Se usa `--network host` para que el contenedor pueda acceder al backend que corre en tu máquina host.

### 4. Acceder al cliente

Abrí tu navegador en:
```
http://localhost:3000
```

### 5. Usar el cliente

1. Hacé clic en el botón **"Enviar Solicitud"**
2. Esperá la respuesta del servidor
3. El resultado se mostrará en formato JSON

## Detener el cliente

```bash
docker stop frontend-client && docker rm frontend-client
```

## Estructura de archivos

```
frontend/
├── index.html      # Interfaz web + lógica del cliente
├── server.js       # Servidor Express para servir el frontend
├── package.json    # Dependencias del proyecto
└── Dockerfile      # Configuración de Docker
```

## Solución de problemas

### Error "Failed to fetch"

1. Verificá que el backend esté corriendo en el puerto 8080
2. Verificá que el contenedor tenga acceso a la red host (`--network host`)
3. En macOS, si `host.docker.internal` no funciona, se usa `--network host` como alternativa

### El contenedor no arranca

```bash
# Ver logs del contenedor
docker logs frontend-client

# Eliminar y recrear
docker stop frontend-client && docker rm frontend-client
docker run -d --network host -p 3000:3000 --name frontend-client frontend-client:latest
```
