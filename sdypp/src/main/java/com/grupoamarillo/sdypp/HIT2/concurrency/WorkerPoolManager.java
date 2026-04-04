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

    /*
    Cola con prioridades
    Ya la clase provee exclusión mutua
    Las tareas se ordenan por Lamport timestamp
    */
    private final PriorityBlockingQueue<QueuedTask> taskQueue = new PriorityBlockingQueue<>();

    //Contador para desempatar FIFO en la cola (numero en el que entro la task)
    private AtomicLong enqueueCounter = new AtomicLong(0);

    //Pool de threads, cada thread es un "worker"
    private ExecutorService workerPool;

    private TaskExecutor taskExecutor;

    private volatile boolean running = true; 

    @PostConstruct
    public void init(){
        log.info("Inicializando WorkerPoolManager con {} workers", maxWorkers);
        
        //Reserva los hilos
        workerPool = Executors.newFixedThreadPool(maxWorkers);

        //Lanza los workers en los hilos reservados
        for (int i = 0; i < maxWorkers; i++){
            final int workerId = i + 1;
            workerPool.submit(() -> workerLoop(workerId));
        }

    }

    //Inyecto al ejecutor despues de la construccion
    public void setTaskExecutor(TaskExecutor taskExecutor){
        this.taskExecutor = taskExecutor;
    }

    public CompletableFuture<Hit2TaskResponse> submitTask(Hit2TaskRequest request, long lamportTs){
        CompletableFuture<Hit2TaskResponse> future = new CompletableFuture<>();
        
        long order = enqueueCounter.incrementAndGet();

        QueuedTask task = new QueuedTask(request, future, lamportTs, order);
        taskQueue.put(task);

        log.info("Tarea encolada [lamport={}, orden={}, queueSize={}]", 
        lamportTs, order, taskQueue.size());

        return future;
    }

    //Loopeo en cada worker: toma tareas d ela cola y las ejecuta
    // take manda a dormir al worker si no hay tareas
    private void workerLoop(int workerId){
        log.info("Worker-{} iniciado", workerId);

        while (running){
            try{
                // take() bloqueo hasta que hay una tarea disponible.
                //la cola con prioridad garantiza que sale la de menor Lamport
                QueuedTask task = taskQueue.take();

                log.info("Worker-{} procesando tarea [lamport={}, orden={}]",
                workerId, task.getLamportTimestamp(), task.getEnqueueOrder());

                //Ejecuta la tarea (pull + run + invoke + cleanup)
                Hit2TaskResponse response = taskExecutor.execute(
                    task.getRequest(),
                    task.getLamportTimestamp(),
                    task.getEnqueueOrder()
                );

                //Completa el future con la respuesta para enviarselo al controlador
                task.getFuture().complete(response);


            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
                log.info("Worker-{} interrumpido, saliendo", workerId);
                break;
            } catch (Exception e){
                log.error("Worker-{} error inesperado: {}", workerId, e.getMessage(), e);
            }
        }
    }

    @PreDestroy
    public void shutdown(){
        running = false;
        workerPool.shutdownNow();
        log.info("WorkerPoolManager apagado");
    }

    //Metricas
     public int getQueueSize() {
        return taskQueue.size();
    }
    public int getMaxWorkers() {
        return maxWorkers;
    }
    
}
