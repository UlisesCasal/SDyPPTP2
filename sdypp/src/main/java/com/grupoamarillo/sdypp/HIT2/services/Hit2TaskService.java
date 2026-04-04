package com.grupoamarillo.sdypp.HIT2.services;

import java.util.concurrent.CompletableFuture;

import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;

public interface Hit2TaskService {
    CompletableFuture<Hit2TaskResponse> ejecutarTareaConcurrente(Hit2TaskRequest request);
}
