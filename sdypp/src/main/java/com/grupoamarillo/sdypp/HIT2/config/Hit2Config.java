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

    public Hit2Config(WorkerPoolManager poolManager, TaskExecutor taskExecutor){
        this.poolManager = poolManager;
        this.taskExecutor = taskExecutor;
    }

    @Bean
    public LamportClock lamportClock(){
        return new LamportClock();
    }

    /**
     * Conecta el executor al pool manager después de que ambos estén creados.
     * Evita dependencia circular.
     */
    @PostConstruct
    public void wirePoolWithExecutor(){
        poolManager.setTaskExecutor(taskExecutor);
    }
}
