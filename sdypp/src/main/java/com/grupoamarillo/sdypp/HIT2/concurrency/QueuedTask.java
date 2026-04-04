package com.grupoamarillo.sdypp.HIT2.concurrency;

import java.util.concurrent.CompletableFuture;

import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;

public class QueuedTask implements Comparable<QueuedTask> {
    private final Hit2TaskRequest request;
    private final CompletableFuture<Hit2TaskResponse> future;
    private final long lamportTimestamp;
    private final long enqueueOrder; // Desempate FIFO

    public QueuedTask(Hit2TaskRequest request, CompletableFuture<Hit2TaskResponse> future, long lamportTimestamp, long enqueueOrder) {
        this.request = request;
        this.future = future;
        this.lamportTimestamp = lamportTimestamp;
        this.enqueueOrder = enqueueOrder;
    }

    public Hit2TaskRequest getRequest(){ return request; }
    public CompletableFuture<Hit2TaskResponse> getFuture(){ return future; }
    public long getLamportTimestamp(){ return lamportTimestamp; }
    public long getEnqueueOrder(){ return enqueueOrder; }

    @Override
    public int compareTo(QueuedTask other){
        //Primero por Lamport timestamp (menor = mas prioritario)
        int cmp = Long.compare(this.lamportTimestamp, other.lamportTimestamp);
        if (cmp != 0) return cmp;
        //Desempate por orden de llegada (menor = mas prioritario)
        return Long.compare(this.enqueueOrder, other.enqueueOrder);
    }
}
