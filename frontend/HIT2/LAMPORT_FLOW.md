# Flujo Completo del Reloj de Lamport — HIT2

> 4 Clientes → 2 Workers → Orchestrator

---

## FASE 1: Los Clientes Preparan y Envían Tareas

Todos los clientes empiezan con `reloj = 0`.

```
  Cliente 1 (reloj=0)     Cliente 2 (reloj=0)     Cliente 3 (reloj=0)     Cliente 4 (reloj=0)
       │                         │                         │                         │
       │ tick()                  │ tick()                  │ tick()                  │ tick()
       │ reloj = 0+1 = 1         │ reloj = 0+1 = 1         │ reloj = 0+1 = 1         │ reloj = 0+1 = 1
       │                         │                         │                         │
       ├─────────────────────────┼─────────────────────────┼─────────────────────────┤
       │                         │                         │                         │
       ▼                         ▼                         ▼                         ▼
  ┌─────────┐              ┌─────────┐              ┌─────────┐              ┌─────────┐
  │ POST /  │              │ POST /  │              │ POST /  │              │ POST /  │
  │ hit2... │              │ hit2... │              │ hit2... │              │ hit2... │
  │ Lamport │              │ Lamport │              │ Lamport │              │ Lamport │
  │   = 1   │              │   = 1   │              │   = 1   │              │   = 1   │
  └────┬────┘              └────┬────┘              └────┬────┘              └────┬────┘
       │                         │                         │                         │
       └─────────────────────────┴─────────────────────────┴─────────────────────────┘
                                   │
                                   ▼
                    Todas llegan al Orchestrator
```

---

## FASE 2: El Orchestrator Recibe y Encola (`receive`)

El orchestrator empieza con `reloj = 0`. La `PriorityBlockingQueue` está vacía.

```
  Orchestrator (reloj = 0)

  ┌──────────────────────────────────────────────────────────────────────┐
  │ Tarea 1 llega → receive(1)                                          │
  │   reloj = max(0, 1) + 1 = 2                                         │
  │   Se encola con prioridad Lamport=2                                 │
  │   Queue: [T1(lamport=2)]                                            │
  │                                                                      │
  │ Tarea 2 llega → receive(1)                                          │
  │   reloj = max(2, 1) + 1 = 3                                         │
  │   Se encola con prioridad Lamport=3                                 │
  │   Queue: [T1(2), T2(3)]  ← ordenada por menor Lamport primero       │
  │                                                                      │
  │ Tarea 3 llega → receive(1)                                          │
  │   reloj = max(3, 1) + 1 = 4                                         │
  │   Se encola con prioridad Lamport=4                                 │
  │   Queue: [T1(2), T2(3), T3(4)]                                      │
  │                                                                      │
  │ Tarea 4 llega → receive(1)                                          │
  │   reloj = max(4, 1) + 1 = 5                                         │
  │   Se encola con prioridad Lamport=5                                 │
  │   Queue: [T1(2), T2(3), T3(4), T4(5)]                               │
  └──────────────────────────────────────────────────────────────────────┘
```

---

## FASE 3: Los Workers Procesan (`tick` al completar)

Cada worker toma una tarea de la cola con `take()`, la ejecuta, y al terminar llama `tick()`.

```
  Worker 1                    Worker 2
  ┌──────────────┐            ┌──────────────┐
  │ take() → T1  │            │ take() → T2  │
  │ (Lamport=2)  │            │ (Lamport=3)  │
  │              │            │              │
  │ docker pull  │            │ docker pull  │
  │ docker run   │            │ docker run   │
  │ POST /ejec   │            │ POST /ejec   │
  │ (5 seg)      │            │ (5 seg)      │
  │              │            │              │
  │ tick()       │            │ tick()       │
  │ reloj=2+1=3  │            │ reloj=3+1=4  │
  │              │            │              │
  │ complete()   │            │ complete()   │
  │ → responde   │            │ → responde   │
  │   Lamport=3  │            │   Lamport=4  │
  └──────────────┘            └──────────────┘

  ┌──────────────┐            ┌──────────────┐
  │ take() → T3  │            │ take() → T4  │
  │ (Lamport=4)  │            │ (Lamport=5)  │
  │              │            │              │
  │ docker pull  │            │ docker pull  │
  │ docker run   │            │ docker run   │
  │ POST /ejec   │            │ POST /ejec   │
  │ (5 seg)      │            │ (5 seg)      │
  │              │            │              │
  │ tick()       │            │ tick()       │
  │ reloj=4+1=5  │            │ reloj=5+1=6  │
  │              │            │              │
  │ complete()   │            │ complete()   │
  │ → responde   │            │ → responde   │
  │   Lamport=5  │            │   Lamport=6  │
  └──────────────┘
```

> **Nota:** Los workers corren en paralelo, pero el reloj de Lamport es compartido (`AtomicLong`). Cada `tick()` incrementa el **mismo** reloj global.

---

## FASE 4: Los Clientes Reciben y Actualizan (`receive` + `tick`)

Cada cliente recibe la respuesta con el Lamport del servidor y actualiza su reloj local.

```
  Cliente 1 recibe Lamport=3        Cliente 2 recibe Lamport=4
  ┌──────────────────────────┐      ┌──────────────────────────┐
  │ receive(3):              │      │ receive(4):              │
  │   max(1, 3) + 1 = 4      │      │   max(1, 4) + 1 = 5      │
  │                          │      │                          │
  │ tick() (auto-increment): │      │ tick() (auto-increment): │
  │   4 + 1 = 5              │      │   5 + 1 = 6              │
  │                          │      │                          │
  │ RELOJ FINAL = 5          │      │ RELOJ FINAL = 6          │
  └──────────────────────────┘      └──────────────────────────┘

  Cliente 3 recibe Lamport=5        Cliente 4 recibe Lamport=6
  ┌──────────────────────────┐      ┌──────────────────────────┐
  │ receive(5):              │      │ receive(6):              │
  │   max(1, 5) + 1 = 6      │      │   max(1, 6) + 1 = 7      │
  │                          │      │                          │
  │ tick() (auto-increment): │      │ tick() (auto-increment): │
  │   6 + 1 = 7              │      │   7 + 1 = 8              │
  │                          │      │                          │
  │ RELOJ FINAL = 7          │      │ RELOJ FINAL = 8          │
  └──────────────────────────┘
```

---

## FASE 5: Resumen Final

| Entidad      | Reloj Final | Por qué                              |
| ------------ | ----------- | ------------------------------------ |
| Orchestrator | 6           | 4 receive + 4 tick = 8 eventos       |
| Cliente 1    | 5           | receive(3) + tick = 2 eventos        |
| Cliente 2    | 6           | receive(4) + tick = 2 eventos        |
| Cliente 3    | 7           | receive(5) + tick = 2 eventos        |
| Cliente 4    | 8           | receive(6) + tick = 2 eventos        |

> **⚠️ Nota:** Si en tu ejecución real el servidor dio `9` y el Cliente 4 dio `11`, es porque el orden de ejecución fue distinto (los workers no terminaron en paralelo perfecto). El **principio** es el mismo: cada evento incrementa el reloj. Los números exactos dependen del timing real de ejecución.

---

## Reglas Clave del Reloj de Lamport

### 1. Evento local (`tick`)

```
reloj = reloj + 1
```

Se llama antes de procesar cualquier evento local.

### 2. Al enviar mensaje

Adjuntás tu reloj actual en el mensaje.

### 3. Al recibir mensaje (`receive`)

```
reloj = max(reloj_local, reloj_recibido) + 1
```

Sincronizás tu reloj con el del emisor.

---

## Garantía de Orden Causal

> Si el evento **A** causó el evento **B**, entonces `Lamport(A) < Lamport(B)`.

Esto **no** significa que A ocurrió antes en tiempo real. Significa que existe una **relación causal** entre ambos eventos, y el reloj de Lamport la preserva.

---

## ¿Dónde se aplica en el código?

### En el cliente (frontend)

```javascript
// Antes de enviar una tarea
function tickLamport() {
    lamportCounter++;
    return lamportCounter;  // Se envía en el body
}

// Al recibir la respuesta del servidor
function receiveLamport(serverTs) {
    lamportCounter = Math.max(lamportCounter, serverTs) + 1;
    return lamportCounter;
}
```

### En el servidor (Java)

```java
// Cuando llega una tarea (Hit2TaskServiceImpl)
long receivedTs = lamportClock.receive(request.getLamportTimestamp());

// Cuando un worker completa la tarea (TaskExecutorImpl)
lamportClock.tick();  // Antes de armar la respuesta
```

---

## Implementación del `LamportClock` (Java)

```java
public class LamportClock {
    private final AtomicLong clock = new AtomicLong(0);

    // Incrementa el reloj en 1 (evento local)
    public long tick() {
        return clock.incrementAndGet();
    }

    // Sincroniza con un timestamp recibido
    public long receive(long receivedTimestamp) {
        return clock.updateAndGet(current ->
            Math.max(current, receivedTimestamp) + 1
        );
    }

    // Devuelve el valor actual sin modificar
    public long current() {
        return clock.get();
    }
}
```

> `AtomicLong` garantiza que no hay condiciones de carrera cuando múltiples workers llaman `tick()` simultáneamente.
