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
    public CompletableFuture<Hit2TaskResponse> ejecutarTareaConcurrente(Hit2TaskRequest request){
        //1. Recibi el timestamp del cliente y sincroniza el reloj
        long receivedTs = lamportClock.receive(request.getLamportTimestamp());
        log.info("Tarea recibida [clientLamport={}, serverLamport={}]",
                request.getLamportTimestamp(), receivedTs);

        //2. Encola en el pool
        return poolManager.submitTask(request, receivedTs);
    }

}
