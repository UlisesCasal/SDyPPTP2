# HIT2 — Concurrencia y Exclusión Mutua: Guía paso a paso

> Basado en tu HIT1 existente. Todo el código va dentro del mismo proyecto Spring Boot ([sdypp](file:///Users/ulisescasal/Documents/Universidad/Sistemas%20Distribuidos%20y%20Prog%20Paralela/TP2/sdypp)), en un paquete nuevo `HIT2`.

---

## Arquitectura general del HIT2

```
                              ┌──────────────────────────────────────────────┐
                              │            Orquestador (Spring Boot)         │
  ┌──────────┐   POST         │                                              │
  │ Cliente  │ ──────────────►│  Controller ──► LamportClock (timestamp)     │
  │          │ (JSON +        │      │                                        │
  │          │  lamport_ts)   │      ▼                                        │
  │          │                │  TaskQueue (mutex) ──► Pool de Workers (N)   │
  │          │ ◄──────────────│      │                   │  │  │  │          │
  └──────────┘  JSON +        │      │              Worker1 W2 W3 ... WN    │
               lamport_ts     │      │                   │                   │
                              │      │              docker run/stop          │
                              │      │                   │                   │
                              │      │              Task Service (container) │
                              └──────────────────────────────────────────────┘
```

---

## Paso 0: Configuración — [application.properties](file:///Users/ulisescasal/Documents/Universidad/Sistemas%20Distribuidos%20y%20Prog%20Paralela/TP2/sdypp/src/main/resources/application.properties)

Agregá la propiedad para el tamaño del pool de workers.

#### [MODIFY] [application.properties](file:///Users/ulisescasal/Documents/Universidad/Sistemas%20Distribuidos%20y%20Prog%20Paralela/TP2/sdypp/src/main/resources/application.properties)

```properties
spring.application.name=sdypp

# HIT2 - Pool de workers
hit2.workers.max=4
```

---

## Paso 1: Reloj de Lamport — `LamportClock.java`

Esta es la pieza fundamental de sistemas distribuidos. El reloj de Lamport te da un **orden parcial** de eventos sin depender de relojes físicos sincronizados.

**Reglas del reloj de Lamport:**
1. Antes de cada evento local → `clock++`
2. Al enviar un mensaje → adjuntás tu `clock` actual
3. Al recibir un mensaje → `clock = max(clock, received_clock) + 1`

#### [NEW] `LamportClock.java`

**Ruta**: `src/main/java/com/grupoamarillo/sdypp/HIT2/concurrency/LamportClock.java`

```java
package com.grupoamarillo.sdypp.HIT2.concurrency;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Reloj lógico de Lamport thread-safe.
 * 
 * Usa AtomicLong porque múltiples threads (workers) van a estar
 * actualizando el reloj concurrentemente. Sin esto → condición de carrera.
 */
public class LamportClock {

    private final AtomicLong clock = new AtomicLong(0);

    /**
     * Evento local: incrementa el reloj.
     * Se llama ANTES de procesar cualquier evento interno.
     */
    public long tick() {
        return clock.incrementAndGet();
    }

    /**
     * Evento de recepción: sincroniza con el timestamp recibido.
     * clock = max(local, received) + 1
     */
    public long receive(long receivedTimestamp) {
        return clock.updateAndGet(current -> Math.max(current, receivedTimestamp) + 1);
    }

    /**
     * Devuelve el valor actual sin modificarlo.
     */
    public long current() {
        return clock.get();
    }
}
```

> [!IMPORTANT]
> Usamos `AtomicLong` y no `synchronized` porque `AtomicLong.updateAndGet()` es lock-free (usa CAS internamente). Es más eficiente para algo tan frecuente como el reloj.

---

## Paso 2: DTOs del HIT2 — Incorporar timestamps de Lamport

Los DTOs del HIT2 son iguales a los del HIT1 pero **agregan el campo `lamportTimestamp`** para el ordenamiento lógico.

#### [NEW] `Hit2TaskRequest.java`

**Ruta**: `src/main/java/com/grupoamarillo/sdypp/HIT2/dtos/Hit2TaskRequest.java`

```java
package com.grupoamarillo.sdypp.HIT2.dtos;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hit2TaskRequest {

    @NotBlank
    private String calculo;

    @NotEmpty
    private Map<String, Object> parametros;

    private Map<String, Object> datosAdicionales;

    @NotBlank
    private String imagenDocker;

    /**
     * Timestamp lógico de Lamport del cliente.
     * Si el cliente no lo manda, se asume 0.
     */
    private long lamportTimestamp;
}
```

#### [NEW] `Hit2TaskResponse.java`

**Ruta**: `src/main/java/com/grupoamarillo/sdypp/HIT2/dtos/Hit2TaskResponse.java`

```java
package com.grupoamarillo.sdypp.HIT2.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hit2TaskResponse {

    private String status;
    private Object resultado;
    private String mensaje;
    private String containerId;
    private Long duracionMs;

    /** Timestamp de Lamport del servidor al RESPONDER */
    private long lamportTimestamp;

    /** Posición que tuvo en la cola (para que el cliente vea el ordenamiento) */
    private long posicionEnCola;
}
```

---

## Paso 3: Tarea interna con prioridad — `QueuedTask.java`

Representación interna de una tarea encolada. La prioridad se resuelve por reloj de Lamport.

#### [NEW] `QueuedTask.java`

**Ruta**: `src/main/java/com/grupoamarillo/sdypp/HIT2/concurrency/QueuedTask.java`

```java
package com.grupoamarillo.sdypp.HIT2.concurrency;

import java.util.concurrent.CompletableFuture;

import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;

/**
 * Envuelve un request + un future para devolver el resultado al controller.
 * 
 * Implementa Comparable para que la PriorityBlockingQueue ordene
 * por timestamp de Lamport (menor timestamp = mayor prioridad = se ejecuta primero).
 */
public class QueuedTask implements Comparable<QueuedTask> {

    private final Hit2TaskRequest request;
    private final CompletableFuture<Hit2TaskResponse> future;
    private final long lamportTimestamp;
    private final long enqueueOrder; // desempate FIFO

    public QueuedTask(Hit2TaskRequest request,
                      CompletableFuture<Hit2TaskResponse> future,
                      long lamportTimestamp,
                      long enqueueOrder) {
        this.request = request;
        this.future = future;
        this.lamportTimestamp = lamportTimestamp;
        this.enqueueOrder = enqueueOrder;
    }

    public Hit2TaskRequest getRequest() { return request; }
    public CompletableFuture<Hit2TaskResponse> getFuture() { return future; }
    public long getLamportTimestamp() { return lamportTimestamp; }
    public long getEnqueueOrder() { return enqueueOrder; }

    @Override
    public int compareTo(QueuedTask other) {
        // Primero por Lamport timestamp (menor = más prioritario)
        int cmp = Long.compare(this.lamportTimestamp, other.lamportTimestamp);
        if (cmp != 0) return cmp;
        // Desempate por orden de llegada (FIFO)
        return Long.compare(this.enqueueOrder, other.enqueueOrder);
    }
}
```

> [!NOTE]
> **¿Por qué `CompletableFuture`?** Porque el controller necesita devolver el HTTP response CUANDO la tarea termine. El worker completa el future y el controller estaba esperándolo con `.get()`. Es el patrón **productor-consumidor** clásico.

---

## Paso 4: Worker Pool Manager — `WorkerPoolManager.java`

Este es el **CORAZÓN** del HIT2. Maneja la cola con exclusión mutua y el pool de workers.

#### [NEW] `WorkerPoolManager.java`

**Ruta**: `src/main/java/com/grupoamarillo/sdypp/HIT2/concurrency/WorkerPoolManager.java`

```java
package com.grupoamarillo.sdypp.HIT2.concurrency;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class WorkerPoolManager {

    private static final Logger log = LoggerFactory.getLogger(WorkerPoolManager.class);

    @Value("${hit2.workers.max:4}")
    private int maxWorkers;

    /**
     * Cola con prioridad thread-safe.
     * PriorityBlockingQueue ya provee exclusión mutua interna (lock).
     * Las tareas se ordenan por Lamport timestamp (menor = primero).
     */
    private final PriorityBlockingQueue<QueuedTask> taskQueue = new PriorityBlockingQueue<>();

    /** Contador atómico para desempate FIFO en la cola */
    private final AtomicLong enqueueCounter = new AtomicLong(0);

    /** Pool de threads fijo — cada thread es un "worker" */
    private ExecutorService workerPool;

    /** Referencia al servicio que ejecuta la tarea (se inyecta después) */
    private TaskExecutor taskExecutor;

    /** Flag para shutdown */
    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        log.info("Inicializando WorkerPoolManager con {} workers", maxWorkers);

        // Pool fijo de N workers
        workerPool = Executors.newFixedThreadPool(maxWorkers);

        // Lanzar N worker threads que consumen de la cola
        for (int i = 0; i < maxWorkers; i++) {
            final int workerId = i + 1;
            workerPool.submit(() -> workerLoop(workerId));
        }
    }

    /**
     * Inyectar el executor después de la construcción para evitar
     * dependencias circulares.
     */
    public void setTaskExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    /**
     * Encola una tarea y devuelve un Future con la respuesta.
     * El controller llama a esto y espera el future.
     */
    public CompletableFuture<Hit2TaskResponse> submitTask(Hit2TaskRequest request, long lamportTs) {
        CompletableFuture<Hit2TaskResponse> future = new CompletableFuture<>();
        long order = enqueueCounter.incrementAndGet();

        QueuedTask task = new QueuedTask(request, future, lamportTs, order);

        // put() es thread-safe en PriorityBlockingQueue (exclusión mutua interna)
        taskQueue.put(task);

        log.info("Tarea encolada [lamport={}, orden={}, queueSize={}]",
                lamportTs, order, taskQueue.size());

        return future;
    }

    /**
     * Loop de cada worker: toma tareas de la cola y las ejecuta.
     * take() es BLOQUEANTE — el worker duerme hasta que hay trabajo.
     */
    private void workerLoop(int workerId) {
        log.info("Worker-{} iniciado", workerId);
        while (running) {
            try {
                // take() bloquea hasta que hay una tarea disponible.
                // La cola con prioridad garantiza que sale la de menor Lamport timestamp.
                QueuedTask task = taskQueue.take();

                log.info("Worker-{} procesando tarea [lamport={}, orden={}]",
                        workerId, task.getLamportTimestamp(), task.getEnqueueOrder());

                // Ejecutar la tarea (pull + run + invoke + cleanup)
                Hit2TaskResponse response = taskExecutor.execute(
                        task.getRequest(),
                        task.getLamportTimestamp(),
                        task.getEnqueueOrder()
                );

                // Completar el future → el controller recibe la respuesta
                task.getFuture().complete(response);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Worker-{} interrumpido, saliendo", workerId);
                break;
            } catch (Exception e) {
                log.error("Worker-{} error inesperado: {}", workerId, e.getMessage(), e);
                // No romper el loop — seguir procesando otras tareas
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        workerPool.shutdownNow();
        log.info("WorkerPoolManager apagado");
    }

    /** Para métricas */
    public int getQueueSize() {
        return taskQueue.size();
    }

    public int getMaxWorkers() {
        return maxWorkers;
    }
}
```

> [!IMPORTANT]
> **Exclusión mutua**: `PriorityBlockingQueue` usa un `ReentrantLock` interno. Cada operación `put()` y `take()` adquiere el lock. Esto GARANTIZA que no hay condición de carrera al encolar/desencolar. Es el equivalente a un mutex sobre la cola compartida.

---

## Paso 5: Task Executor — `TaskExecutor.java`

Extrae la lógica de ejecución Docker del [RemoteTaskServiceImpl](file:///Users/ulisescasal/Documents/Universidad/Sistemas%20Distribuidos%20y%20Prog%20Paralela/TP2/sdypp/src/main/java/com/grupoamarillo/sdypp/HIT1/services/RemoteTaskServiceImpl.java#24-198) del HIT1. Cada worker llama a este componente.

#### [NEW] `TaskExecutor.java` (interfaz)

**Ruta**: `src/main/java/com/grupoamarillo/sdypp/HIT2/concurrency/TaskExecutor.java`

```java
package com.grupoamarillo.sdypp.HIT2.concurrency;

import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;

public interface TaskExecutor {
    Hit2TaskResponse execute(Hit2TaskRequest request, long lamportTs, long posicionEnCola);
}
```

#### [NEW] `TaskExecutorImpl.java`

**Ruta**: `src/main/java/com/grupoamarillo/sdypp/HIT2/concurrency/TaskExecutorImpl.java`

```java
package com.grupoamarillo.sdypp.HIT2.concurrency;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.grupoamarillo.sdypp.HIT1.docker.CommandResult;
import com.grupoamarillo.sdypp.HIT1.docker.DockerCommandRunner;
import com.grupoamarillo.sdypp.HIT1.dtos.TaskServiceRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ejecuta UNA tarea: pull → run → invoke → cleanup.
 * Reutiliza DockerCommandRunner del HIT1.
 * 
 * NOTA: Cada invocación es independiente y thread-safe porque
 * no comparte estado mutable. Cada worker tiene su propio containerId.
 */
@Component
public class TaskExecutorImpl implements TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutorImpl.class);
    private static final Pattern IMAGE_PATTERN = Pattern
            .compile("^[a-z0-9]+([._-][a-z0-9]+)*(/[a-z0-9]+([._-][a-z0-9]+)*)*(:[\\w][\\w.-]{0,127})?$");
    private static final Pattern PORT_PATTERN = Pattern.compile(".*:(\\d+)$");

    private final DockerCommandRunner dockerRunner;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final LamportClock lamportClock;
    private final String taskServiceHost;

    public TaskExecutorImpl(DockerCommandRunner dockerRunner,
                            ObjectMapper objectMapper,
                            LamportClock lamportClock) {
        this.dockerRunner = dockerRunner;
        this.objectMapper = objectMapper;
        this.lamportClock = lamportClock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        String host = System.getenv("TASK_SERVICE_HOST");
        this.taskServiceHost = (host == null || host.isBlank()) ? "localhost" : host;
    }

    @Override
    public Hit2TaskResponse execute(Hit2TaskRequest request, long lamportTs, long posicionEnCola) {
        long inicio = System.currentTimeMillis();
        String containerId = null;

        try {
            // Validar imagen
            if (!IMAGE_PATTERN.matcher(request.getImagenDocker()).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Formato de imagenDocker inválido");
            }

            // Pull
            CommandResult pullResult = dockerRunner.run(
                    List.of("docker", "pull", request.getImagenDocker()));
            if (pullResult.exitCode() != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No se pudo descargar la imagen: " + pullResult.stderr());
            }

            // Run
            CommandResult runResult = dockerRunner.run(
                    List.of("docker", "run", "-d", "-P", request.getImagenDocker()));
            if (runResult.exitCode() != 0) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo iniciar el contenedor: " + runResult.stderr());
            }
            containerId = runResult.stdout().trim();

            // Port
            CommandResult portResult = dockerRunner.run(
                    List.of("docker", "port", containerId, "8080"));
            Matcher matcher = PORT_PATTERN.matcher(portResult.stdout().trim());
            if (!matcher.find()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo obtener el puerto");
            }
            String hostPort = matcher.group(1);

            // Invoke con reintentos
            TaskServiceRequest payload = new TaskServiceRequest(
                    request.getCalculo(),
                    request.getParametros(),
                    request.getDatosAdicionales());

            JsonNode taskResponse = invocarConReintentos(hostPort, payload);

            long duracion = System.currentTimeMillis() - inicio;

            // Tick del reloj al responder
            long responseTs = lamportClock.tick();

            return new Hit2TaskResponse(
                    taskResponse.path("status").asText("OK"),
                    taskResponse.path("resultado"),
                    taskResponse.path("mensaje").asText("Tarea ejecutada"),
                    containerId,
                    duracion,
                    responseTs,
                    posicionEnCola);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            long duracion = System.currentTimeMillis() - inicio;
            return new Hit2TaskResponse("ERROR", null, e.getMessage(),
                    containerId, duracion, lamportClock.tick(), posicionEnCola);
        } finally {
            if (containerId != null && !containerId.isBlank()) {
                dockerRunner.run(List.of("docker", "stop", containerId));
                dockerRunner.run(List.of("docker", "rm", containerId));
            }
        }
    }

    private JsonNode invocarConReintentos(String hostPort, TaskServiceRequest payload) {
        ResponseStatusException ultimoError = null;
        for (int i = 1; i <= 10; i++) {
            try {
                String body = objectMapper.writeValueAsString(payload);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://" + taskServiceHost + ":" + hostPort + "/ejecutar"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Error HTTP " + response.statusCode());
                }
                return objectMapper.readTree(response.body());
            } catch (ResponseStatusException e) {
                ultimoError = e;
                try { Thread.sleep(1000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Interrumpido");
                }
            } catch (Exception e) {
                ultimoError = new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Interrumpido");
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Reintentos agotados: " + (ultimoError == null ? "" : ultimoError.getReason()));
    }
}
```

---

## Paso 6: Configuración Spring — `Hit2Config.java`

Registrar el `LamportClock` como bean singleton y conectar el pool con el executor.

#### [NEW] `Hit2Config.java`

**Ruta**: `src/main/java/com/grupoamarillo/sdypp/HIT2/config/Hit2Config.java`

```java
package com.grupoamarillo.sdypp.HIT2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.grupoamarillo.sdypp.HIT2.concurrency.LamportClock;
import com.grupoamarillo.sdypp.HIT2.concurrency.TaskExecutor;
import com.grupoamarillo.sdypp.HIT2.concurrency.WorkerPoolManager;

import jakarta.annotation.PostConstruct;

@Configuration
public class Hit2Config {

    private final WorkerPoolManager poolManager;
    private final TaskExecutor taskExecutor;

    public Hit2Config(WorkerPoolManager poolManager, TaskExecutor taskExecutor) {
        this.poolManager = poolManager;
        this.taskExecutor = taskExecutor;
    }

    @Bean
    public LamportClock lamportClock() {
        return new LamportClock();
    }

    /**
     * Conecta el executor al pool manager después de que ambos estén creados.
     * Evita dependencia circular.
     */
    @PostConstruct
    public void wirePoolWithExecutor() {
        poolManager.setTaskExecutor(taskExecutor);
    }
}
```

---

## Paso 7: Servicio del HIT2 — `Hit2TaskService.java`

#### [NEW] `Hit2TaskService.java`

**Ruta**: `src/main/java/com/grupoamarillo/sdypp/HIT2/services/Hit2TaskService.java`

```java
package com.grupoamarillo.sdypp.HIT2.services;

import java.util.concurrent.CompletableFuture;

import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;

public interface Hit2TaskService {
    CompletableFuture<Hit2TaskResponse> ejecutarTareaConcurrente(Hit2TaskRequest request);
}
```

#### [NEW] `Hit2TaskServiceImpl.java`

**Ruta**: `src/main/java/com/grupoamarillo/sdypp/HIT2/services/Hit2TaskServiceImpl.java`

```java
package com.grupoamarillo.sdypp.HIT2.services;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.grupoamarillo.sdypp.HIT2.concurrency.LamportClock;
import com.grupoamarillo.sdypp.HIT2.concurrency.WorkerPoolManager;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;

@Service
public class Hit2TaskServiceImpl implements Hit2TaskService {

    private static final Logger log = LoggerFactory.getLogger(Hit2TaskServiceImpl.class);

    private final WorkerPoolManager poolManager;
    private final LamportClock lamportClock;

    public Hit2TaskServiceImpl(WorkerPoolManager poolManager, LamportClock lamportClock) {
        this.poolManager = poolManager;
        this.lamportClock = lamportClock;
    }

    @Override
    public CompletableFuture<Hit2TaskResponse> ejecutarTareaConcurrente(Hit2TaskRequest request) {
        // 1. Recibir timestamp del cliente y sincronizar reloj
        long receivedTs = lamportClock.receive(request.getLamportTimestamp());
        log.info("Tarea recibida [clientLamport={}, serverLamport={}]",
                request.getLamportTimestamp(), receivedTs);

        // 2. Encolar en el pool (el worker la tomará cuando haya uno libre)
        return poolManager.submitTask(request, receivedTs);
    }
}
```

---

## Paso 8: Controller del HIT2 — `Hit2Controller.java`

#### [NEW] `Hit2Controller.java`

**Ruta**: `src/main/java/com/grupoamarillo/sdypp/HIT2/controllers/Hit2Controller.java`

```java
package com.grupoamarillo.sdypp.HIT2.controllers;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grupoamarillo.sdypp.HIT2.concurrency.WorkerPoolManager;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;
import com.grupoamarillo.sdypp.HIT2.services.Hit2TaskService;

import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/hit2")
public class Hit2Controller {

    private final Hit2TaskService taskService;
    private final WorkerPoolManager poolManager;

    public Hit2Controller(Hit2TaskService taskService, WorkerPoolManager poolManager) {
        this.taskService = taskService;
        this.poolManager = poolManager;
    }

    /**
     * Endpoint principal: encola la tarea y espera resultado.
     * El request se bloquea hasta que un worker libre la procese.
     */
    @PostMapping("/getRemoteTask")
    public ResponseEntity<Hit2TaskResponse> getRemoteTask(
            @Valid @RequestBody Hit2TaskRequest request) throws Exception {

        CompletableFuture<Hit2TaskResponse> future =
                taskService.ejecutarTareaConcurrente(request);

        // Bloquea el HTTP thread hasta que el worker complete la tarea
        Hit2TaskResponse response = future.get();

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint de status: cuántas tareas en cola y workers configurados.
     * Útil para el análisis de throughput.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "tareasEnCola", poolManager.getQueueSize(),
                "maxWorkers", poolManager.getMaxWorkers()
        ));
    }
}
```

---

## Paso 9: Script de benchmark para medición de throughput

Creá este script en la raíz del proyecto para medir throughput. Lanza N requests en paralelo.

#### [NEW] `benchmark.sh`

**Ruta**: raíz del proyecto `sdypp/benchmark.sh`

```bash
#!/bin/bash
# ================================================================
# Benchmark HIT2 — Mide throughput variando workers (1, 2, 4, 8)
# Uso: ./benchmark.sh <total_tareas>
# Ejemplo: ./benchmark.sh 16
# ================================================================

TOTAL_TASKS=${1:-16}
SERVER_URL="http://localhost:8080/api/hit2/getRemoteTask"
RESULTS_FILE="benchmark_results.csv"

echo "workers,total_tasks,completed,failed,total_seconds,throughput_per_min" > $RESULTS_FILE

for WORKERS in 1 2 4 8; do
    echo ""
    echo "========================================="
    echo "Probando con $WORKERS workers..."
    echo "========================================="

    # Reiniciar el server con N workers (ajustar según cómo lo levantes)
    # Si usás Docker:
    docker rm -f orchestrator-server 2>/dev/null || true
    docker run -d \
        --name orchestrator-server \
        -p 8080:8080 \
        -e TASK_SERVICE_HOST=host.docker.internal \
        -e HIT2_WORKERS_MAX=$WORKERS \
        --add-host=host.docker.internal:host-gateway \
        -v /var/run/docker.sock:/var/run/docker.sock \
        ulisescasal/orchestrator-server:latest

    echo "Esperando que el servidor arranque..."
    sleep 10

    # Timestamp inicio
    START=$(date +%s)

    # Lanzar todas las tareas en paralelo con curl
    PIDS=""
    COMPLETED=0
    FAILED=0

    for i in $(seq 1 $TOTAL_TASKS); do
        (
            RESULT=$(curl -s -o /dev/null -w "%{http_code}" \
                -X POST $SERVER_URL \
                -H "Content-Type: application/json" \
                -d "{
                    \"calculo\":\"sumar\",
                    \"parametros\":{\"a\":$i,\"b\":$((i * 2))},
                    \"datosAdicionales\":{\"traceId\":\"bench-$i\"},
                    \"imagenDocker\":\"ulisescasal/task-service:1.0.0\",
                    \"lamportTimestamp\":$i
                }" --max-time 120)
            echo "$RESULT" > /tmp/bench_result_$i.txt
        ) &
        PIDS="$PIDS $!"
    done

    # Esperar que terminen todas
    for PID in $PIDS; do
        wait $PID
    done

    END=$(date +%s)
    ELAPSED=$((END - START))

    # Contar resultados
    for i in $(seq 1 $TOTAL_TASKS); do
        CODE=$(cat /tmp/bench_result_$i.txt 2>/dev/null)
        if [ "$CODE" = "200" ]; then
            COMPLETED=$((COMPLETED + 1))
        else
            FAILED=$((FAILED + 1))
        fi
        rm -f /tmp/bench_result_$i.txt
    done

    # Throughput = (completed / elapsed_seconds) * 60
    if [ $ELAPSED -gt 0 ]; then
        THROUGHPUT=$(echo "scale=2; ($COMPLETED / $ELAPSED) * 60" | bc)
    else
        THROUGHPUT="N/A"
    fi

    echo "Workers: $WORKERS | Completadas: $COMPLETED | Falladas: $FAILED | Tiempo: ${ELAPSED}s | Throughput: $THROUGHPUT tareas/min"
    echo "$WORKERS,$TOTAL_TASKS,$COMPLETED,$FAILED,$ELAPSED,$THROUGHPUT" >> $RESULTS_FILE

    # Cleanup
    docker rm -f orchestrator-server 2>/dev/null || true
done

echo ""
echo "Resultados guardados en $RESULTS_FILE"
cat $RESULTS_FILE
```

> [!TIP]
> Para que `HIT2_WORKERS_MAX` funcione desde la variable de entorno, en [application.properties](file:///Users/ulisescasal/Documents/Universidad/Sistemas%20Distribuidos%20y%20Prog%20Paralela/TP2/sdypp/src/main/resources/application.properties) usá: `hit2.workers.max=${HIT2_WORKERS_MAX:4}`. Spring Boot resuelve variables de entorno automáticamente con esa sintaxis.

---

## Paso 10: Actualizar [application.properties](file:///Users/ulisescasal/Documents/Universidad/Sistemas%20Distribuidos%20y%20Prog%20Paralela/TP2/sdypp/src/main/resources/application.properties) para variable de entorno

```properties
spring.application.name=sdypp

# HIT2 - Pool de workers (configurable por env var)
hit2.workers.max=${HIT2_WORKERS_MAX:4}
```

---

## Estructura final de archivos

```
src/main/java/com/grupoamarillo/sdypp/
├── SdyppApplication.java                   (sin cambios)
├── HIT1/                                    (sin cambios)
│   ├── controllers/Controller.java
│   ├── docker/
│   │   ├── CommandResult.java
│   │   └── DockerCommandRunner.java
│   ├── dtos/
│   │   ├── RemoteTaskRequest.java
│   │   ├── RemoteTaskResponse.java
│   │   └── TaskServiceRequest.java
│   └── services/
│       ├── RemoteTaskService.java
│       └── RemoteTaskServiceImpl.java
└── HIT2/                                    ← TODO NUEVO
    ├── concurrency/
    │   ├── LamportClock.java                ← Reloj lógico
    │   ├── QueuedTask.java                  ← Wrapper para la cola
    │   ├── TaskExecutor.java                ← Interfaz
    │   ├── TaskExecutorImpl.java            ← Lógica Docker (del HIT1)
    │   └── WorkerPoolManager.java           ← Pool + cola con mutex
    ├── config/
    │   └── Hit2Config.java                  ← Wiring Spring
    ├── controllers/
    │   └── Hit2Controller.java              ← Endpoint /api/hit2/
    ├── dtos/
    │   ├── Hit2TaskRequest.java             ← Con lamportTimestamp
    │   └── Hit2TaskResponse.java            ← Con lamportTimestamp + posición
    └── services/
        ├── Hit2TaskService.java             ← Interfaz
        └── Hit2TaskServiceImpl.java         ← Orquesta clock + pool
```

---

## Checklist de verificación

| Requisito | Dónde está |
|-----------|-----------|
| Pool de workers configurable (N) | `WorkerPoolManager` + [application.properties](file:///Users/ulisescasal/Documents/Universidad/Sistemas%20Distribuidos%20y%20Prog%20Paralela/TP2/sdypp/src/main/resources/application.properties) |
| Exclusión mutua en la cola | `PriorityBlockingQueue` (usa `ReentrantLock` interno) |
| Encolamiento cuando workers ocupados | `taskQueue.take()` bloquea workers, `put()` encola |
| Relojes lógicos de Lamport | `LamportClock` + DTOs con `lamportTimestamp` |
| Ordenamiento por Lamport | `QueuedTask.compareTo()` ordena por timestamp |
| Medición de throughput (1,2,4,8) | `benchmark.sh` genera CSV |
| Endpoint de status | `GET /api/hit2/status` |

---

## Análisis de escalabilidad (para el informe)

### Tabla esperada (ejemplo)

| Workers | Tareas | Tiempo (s) | Throughput (tareas/min) | Speedup |
|---------|--------|------------|------------------------|---------|
| 1       | 16     | ~80        | ~12                    | 1.0x    |
| 2       | 16     | ~42        | ~23                    | ~1.9x   |
| 4       | 16     | ~25        | ~38                    | ~3.2x   |
| 8       | 16     | ~18        | ~53                    | ~4.4x   |

### Cuellos de botella a analizar

1. **Docker daemon**: Es un proceso ÚNICO. Todos los `docker run`, `docker pull`, `docker stop` pasan por él. Con 8 workers, el daemon se satura.
2. **CPU**: Cada contenedor consume CPU. En un solo equipo, los cores son finitos.
3. **Memoria RAM**: Cada contenedor Node.js consume ~50-100MB. Con 8 simultáneos → 400-800MB.
4. **I/O de disco**: Las imágenes Docker usan overlayfs. Muchas lecturas simultáneas degradan performance.
5. **Red Docker bridge**: Todas las comunicaciones HTTP al task service pasan por la red bridge de Docker.

### ¿Es lineal el speedup?

**No.** El speedup se degrada después de cierto punto (ley de Amdahl). El cuello de botella principal es el **Docker daemon** que serializa operaciones internas. Medirlo con `docker stats` y `htop` durante el benchmark.
