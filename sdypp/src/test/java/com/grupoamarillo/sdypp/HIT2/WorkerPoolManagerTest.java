package com.grupoamarillo.sdypp.HIT2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.grupoamarillo.sdypp.HIT2.concurrency.TaskExecutor;
import com.grupoamarillo.sdypp.HIT2.concurrency.WorkerPoolManager;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;

class WorkerPoolManagerTest {

    private WorkerPoolManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Test
    void submitTaskShouldProcessTasksInLamportOrder() throws Exception {
        manager = new WorkerPoolManager();
        ReflectionTestUtils.setField(manager, "maxWorkers", 1);

        List<String> processed = new CopyOnWriteArrayList<>();
        TaskExecutor executor = (request, lamportTs, posicionEnCola) -> {
            processed.add(lamportTs + ":" + posicionEnCola);
            return new Hit2TaskResponse("OK", null, null, null, 0L, lamportTs, posicionEnCola);
        };
        manager.setTaskExecutor(executor);
        manager.init();

        // Give worker thread time to start
        Thread.sleep(50);

        Hit2TaskRequest taskA = new Hit2TaskRequest("calcA", Map.of("value", 1), null, "imgA", 10L);
        Hit2TaskRequest taskB = new Hit2TaskRequest("calcB", Map.of("value", 2), null, "imgB", 5L);
        Hit2TaskRequest taskC = new Hit2TaskRequest("calcC", Map.of("value", 3), null, "imgC", 5L);

        CompletableFuture<Hit2TaskResponse> futureB = manager.submitTask(taskB, 5L);
        CompletableFuture<Hit2TaskResponse> futureC = manager.submitTask(taskC, 5L);
        CompletableFuture<Hit2TaskResponse> futureA = manager.submitTask(taskA, 10L);

        futureA.get(5, TimeUnit.SECONDS);
        futureB.get(5, TimeUnit.SECONDS);
        futureC.get(5, TimeUnit.SECONDS);

        assertEquals(3, processed.size());
        assertEquals("5:1", processed.get(0));
        assertEquals("5:2", processed.get(1));
        assertEquals("10:3", processed.get(2));
    }
}
