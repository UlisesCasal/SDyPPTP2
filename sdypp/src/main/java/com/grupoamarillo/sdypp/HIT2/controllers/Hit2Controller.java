package com.grupoamarillo.sdypp.HIT2.controllers;

import java.util.Map;
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
        @Valid @RequestBody Hit2TaskRequest request
    ) throws Exception{
        CompletableFuture<Hit2TaskResponse> future = taskService.ejecutarTareaConcurrente(request);

        //Bloquea el HTTP thread hasta que el worker complete la tarea
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
