package com.grupoamarillo.sdypp.HIT3.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor
@AllArgsConstructor
public class Hit3ClusterNodeStatus {
    // ID lógico del nodo
    private int nodeId;
    // host del nodo
    private String host;
    // si se considera vivo según heartbeats
    private boolean alive;
    // si es líder actual
    private boolean leader;
    // último instante (epoch ms) en que se lo vio vivo
    private long lastSeenMs;
    // si está ejecutando una tarea ahora mismo
    private boolean busy;
}
