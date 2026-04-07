# TP2 — Sistemas Distribuidos y Programación Paralela

Este repositorio contiene la implementación del Trabajo Práctico 2 de la materia Sistemas Distribuidos y Programación Paralela. El proyecto se divide en tres niveles de complejidad (HITs) que abordan desde la ejecución remota básica hasta la coordinación de clústeres con tolerancia a fallos.

---

## 🚀 Guía Rápida de Ejecución

### Requisitos Previos
- **Docker Desktop** o **Docker Daemon** funcionando.
- **Docker CLI** disponible en el PATH.
- Puertos `8080`, `8090`, `3000`, `3001`, `3003` disponibles.

### Scripts de Prueba Automatizados
Todos los scripts de prueba se encuentran centralizados en la carpeta `Pruebas de HIT`.

---

## 🏗️ HITs Disponibles

| HIT | Descripción | Frontend | Backend |
|-----|-------------|----------|---------|
| **HIT1** | Tarea remota secuencial básica | `:3000` | `/api/hit1/getRemoteTask` |
| **HIT2** | Concurrencia + Lamport Clock + Worker Pool | `:3001` | `/api/hit2/getRemoteTask` |
| **HIT3** | Clúster con balanceador + elección de líder (Bully) | `:3003` | `/api/hit3/getRemoteTask` |

---

## 1️⃣ HIT1 — Tarea Remota Secuencial

### Descripción del Funcionamiento
El HIT1 implementa la ejecución de una tarea remota de forma secuencial. El Orquestador recibe una solicitud, descarga la imagen Docker necesaria, levanta un contenedor efímero, resuelve su puerto dinámico, ejecuta la tarea, devuelve el resultado y finalmente limpia el entorno (detiene y elimina el contenedor).

### Flujo de Ejecución HIT1
![Flujo HIT1](Graficos%20de%20Funcionamiento/hit1_flujo.jpg)

### Ejecución con Docker

```bash
docker pull ulisescasal/orchestrator-server:1.0.0
docker pull ulisescasal/frontend-client:1.0.0

# Levantar Orquestador
docker rm -f orchestrator-server 2>/dev/null || true
docker run -d \
  --name orchestrator-server \
  -p 8080:8080 \
  -e TASK_SERVICE_HOST=host.docker.internal \
  --add-host=host.docker.internal:host-gateway \
  -v /var/run/docker.sock:/var/run/docker.sock \
  ulisescasal/orchestrator-server:1.0.0

# Levantar Frontend
docker rm -f frontend-client 2>/dev/null || true
docker run -d \
  --name frontend-client \
  -p 3000:3000 \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  --add-host=host.docker.internal:host-gateway \
  ulisescasal/frontend-client:1.0.0
```

**Acceso:** [http://localhost:3000](http://localhost:3000)

---

## 2️⃣ HIT2 — Concurrencia y Relojes de Lamport

### Descripción del Funcionamiento
Este HIT introduce concurrencia real mediante un **Worker Pool** de tamaño configurable. Las tareas no se ejecutan inmediatamente, sino que se encolan en una **Priority Queue**.

- **Reloj de Lamport**: Se utiliza para el ordenamiento lógico de las tareas. Cada cliente mantiene su propio reloj y el servidor sincroniza el suyo con el `max(local, cliente) + 1` al recibir una tarea. Las tareas con menor timestamp tienen prioridad.
- **Exclusión Mutua**: Se garantiza el acceso seguro a la cola de tareas mediante el uso de estructuras thread-safe (`PriorityBlockingQueue`), evitando condiciones de carrera entre los múltiples hilos del pool.
- **Worker Pool**: Un conjunto de hilos (workers) consumen tareas de la cola de forma concurrente, optimizando el uso de recursos y mejorando el throughput.

### Arquitectura y Flujo HIT2
![Flujo HIT2](Graficos%20de%20Funcionamiento/hit2_flujo.jpg)

### Ejecución con Docker

```bash
docker rm -f orchestrator-server frontend-hit2 2>/dev/null || true

# Levantar Orquestador con Pool de 4 Workers
docker run -d \
  --name orchestrator-server \
  -p 8080:8080 \
  -e TASK_SERVICE_HOST=host.docker.internal \
  -e HIT2_WORKERS_MAX=4 \
  --add-host=host.docker.internal:host-gateway \
  -v /var/run/docker.sock:/var/run/docker.sock \
  ulisescasal/orchestrator-server:latest

# Levantar Frontend HIT2
docker run -d \
  --name frontend-hit2 \
  -p 3001:3001 \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  --add-host=host.docker.internal:host-gateway \
  ulisescasal/frontend-hit2:latest
```

**Acceso:** [http://localhost:3001](http://localhost:3001)

### Benchmark de Throughput (HIT2)
Para medir cómo escala el sistema variando la cantidad de hilos de ejecución (1, 2, 4, 8):
```bash
bash "./Pruebas de HIT/benchmark.sh" 16
```
Genera un archivo `benchmark_results.csv` con las métricas de rendimiento y tiempo de respuesta.

---

## 3️⃣ HIT3 — Clúster y Tolerancia a Fallos (Bully)

### Descripción del Funcionamiento
El HIT3 escala el sistema a un entorno de clúster con múltiples instancias del orquestador trabajando de forma coordinada.

- **Balanceador de Carga (Nginx)**: Actúa como punto de entrada único, distribuyendo las peticiones de los clientes entre los nodos del clúster.
- **Algoritmo Bully**: Mecanismo de elección de líder. Si un nodo detecta que el líder actual no responde (vía heartbeats), inicia una elección. El nodo con el ID más alto que esté operativo se convierte en el nuevo coordinador.
- **Coordinación de Tareas**: El líder es responsable de recibir las tareas, mantener el estado de salud de cada nodo y asignar el trabajo a los nodos workers disponibles mediante un algoritmo de Round Robin.
- **Tolerancia a Fallos**: Si cualquier nodo (incluyendo el líder) se cae, el sistema detecta la falla automáticamente y se reorganiza para seguir operando sin pérdida de servicio.

### Arquitectura del Clúster
![Interacción Lógica HIT3](Graficos%20de%20Funcionamiento/hit3_interaccion_logica.jpg)

### Escenarios de Funcionamiento

#### 1. Flujo Normal
Las peticiones llegan al balanceador, se redirigen a un nodo, este consulta al líder y el líder asigna la ejecución a un worker disponible.
![Flujo Normal HIT3](Graficos%20de%20Funcionamiento/hit3_flujo_normal.jpg)

#### 2. Caída de Líder y Elección (Bully)
Detección de timeout, envío de mensajes de elección y proclamación del nuevo coordinador.
![Flujo Failover HIT3](Graficos%20de%20Funcionamiento/hit3_flujo_caida_lider_bully.jpg)

### Ejecución (Recomendada con Script)
La ejecución de HIT3 está totalmente automatizada para facilitar las pruebas de coordinación:

```bash
# Levantar el cluster, ejecutar pruebas de carga y simular failover automático
bash "./Pruebas de HIT/hit3_test_suite.sh" --up --base-url http://localhost:8090 --image ulisescasal/task-service:1.0.0
```

**Opciones del script:**
- `--up`: Levanta el clúster completo (3 nodos + Nginx) usando Docker Compose.
- `--down`: Detiene y elimina todos los contenedores del clúster.
- `--timeout 90`: Define la tolerancia máxima para tareas de larga duración.

---

## 🛠️ Herramientas y Endpoints de Monitoreo

### Estado del Clúster (HIT3)
Permite ver el ID del líder actual, los nodos vivos y su carga:
```bash
curl http://localhost:8090/internal/hit3/cluster/status | python3 -m json.tool
```

### Estado del Worker Pool (HIT2)
Muestra la cantidad de tareas en cola y workers activos:
```bash
curl http://localhost:8080/api/hit2/status
```

---

## 🧹 Limpieza del Entorno

```bash
# Detener contenedores de HIT1/HIT2
docker rm -f orchestrator-server frontend-client frontend-hit2 2>/dev/null || true

# Detener el clúster de HIT3
bash "./Pruebas de HIT/hit3_test_suite.sh" --down
```
