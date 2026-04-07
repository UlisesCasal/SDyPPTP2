package com.grupoamarillo.sdypp.HIT2;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.grupoamarillo.sdypp.HIT2.concurrency.LamportClock;
import com.grupoamarillo.sdypp.HIT2.concurrency.WorkerPoolManager;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;
import com.grupoamarillo.sdypp.HIT2.services.Hit2TaskServiceImpl;

class Hit2TaskServiceImplTest {

    @Test
    void ejecutarTareaConcurrenteShouldSynchronizeLamportAndSubmitToPool() throws Exception {
        WorkerPoolManager poolManager = mock(WorkerPoolManager.class);
        LamportClock lamportClock = mock(LamportClock.class);

        Hit2TaskRequest request = new Hit2TaskRequest(
                "sumar",
                Map.of("a", 1, "b", 2),
                null,
                "test-image",
                3L);

        when(lamportClock.receive(3L)).thenReturn(5L);

        Hit2TaskResponse expectedResponse = new Hit2TaskResponse(
                "OK",
                Map.of("resultado", 3),
                "done",
                null,
                10L,
                5L,
                1L);
        CompletableFuture<Hit2TaskResponse> future = CompletableFuture.completedFuture(expectedResponse);
        when(poolManager.submitTask(request, 5L)).thenReturn(future);

        Hit2TaskServiceImpl service = new Hit2TaskServiceImpl(poolManager, lamportClock);

        Hit2TaskResponse actual = service.ejecutarTareaConcurrente(request).get(1, TimeUnit.SECONDS);

        assertSame(expectedResponse, actual);
        verify(lamportClock).receive(3L);
        verify(poolManager).submitTask(request, 5L);
    }
}
